package com.__25J_323.HarvestPrediction.service;

import com.__25J_323.HarvestPrediction.model.EnvironmentData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
public class MqttService {

    private final EnvironmentDataService environmentDataService;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;

    private Mqtt5BlockingClient mqttClient;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    // Current sensor readings cache
    private final ConcurrentHashMap<String, Double> currentReadings = new ConcurrentHashMap<>();
    private volatile LocalDateTime lastUpdateTime = LocalDateTime.now();
    private volatile LocalDateTime lastSavedTime = null;

    // Device status tracking
    private volatile boolean deviceOnline = false;
    private volatile LocalDateTime lastOnlineTime = null;
    private volatile LocalDateTime deviceOfflineSince = null;

    // Configuration for faster disconnect detection
    private static final long DATA_FRESHNESS_SECONDS = 30; // Consider device offline after 30 seconds
    private static final long MIN_SAVE_INTERVAL_SECONDS = 30; // Minimum interval between saves
    private static final long STATUS_CHECK_INTERVAL_SECONDS = 5; // Check device status every 5 seconds
    private static final long SAVE_DATA_INTERVAL_SECONDS = 30; // Save data every 30 seconds

    // Pattern to extract numeric values from strings with units
    private static final Pattern NUMERIC_PATTERN = Pattern.compile("([0-9]*\\.?[0-9]+)");

    @org.springframework.beans.factory.annotation.Value("${mqtt.broker.url}")
    private String brokerUrl;

    @org.springframework.beans.factory.annotation.Value("${mqtt.broker.port}")
    private int brokerPort;

    @org.springframework.beans.factory.annotation.Value("${mqtt.client.id:tomato-client-}")
    private String clientId;

    @org.springframework.beans.factory.annotation.Value("${mqtt.topic.temperature}")
    private String temperatureTopic;

    @org.springframework.beans.factory.annotation.Value("${mqtt.topic.humidity}")
    private String humidityTopic;

    @org.springframework.beans.factory.annotation.Value("${mqtt.topic.soil-moisture}")
    private String soilMoistureTopic;

    @org.springframework.beans.factory.annotation.Value("${mqtt.username:}")
    private String username;

    @org.springframework.beans.factory.annotation.Value("${mqtt.password:}")
    private String password;

    @PostConstruct
    public void init() {
        try {
            // Create an MQTT client
            mqttClient = Mqtt5Client.builder()
                    .identifier(clientId + UUID.randomUUID().toString())
                    .serverHost(brokerUrl.replace("ssl://", ""))
                    .serverPort(brokerPort)
                    .sslWithDefaultConfig()
                    .buildBlocking();

            // Connect with username and password if provided
            Mqtt5ConnAck connAck;
            if (username != null && !username.isEmpty()) {
                connAck = mqttClient.connectWith()
                        .simpleAuth()
                        .username(username)
                        .password(password.getBytes(StandardCharsets.UTF_8))
                        .applySimpleAuth()
                        .send();
            } else {
                connAck = mqttClient.connect();
            }

            log.info("MQTT client connected: {}", connAck.getReasonCode());

            // Subscribe to topics
            subscribeToTopic(temperatureTopic);
            subscribeToTopic(humidityTopic);
            subscribeToTopic(soilMoistureTopic);

            // Set up a global callback for all messages
            mqttClient.toAsync().publishes(MqttGlobalPublishFilter.ALL, this::handleMessage);

            // Schedule periodic data aggregation and saving - with faster interval
            scheduler.scheduleAtFixedRate(this::aggregateAndSaveData, 10, SAVE_DATA_INTERVAL_SECONDS, TimeUnit.SECONDS);

            // Schedule periodic device status checking - more frequent
            scheduler.scheduleAtFixedRate(this::checkDeviceStatus, 5, STATUS_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);

            log.info("MQTT Service initialized with faster disconnect detection ({}s offline threshold, {}s status check)",
                    DATA_FRESHNESS_SECONDS, STATUS_CHECK_INTERVAL_SECONDS);

        } catch (Exception e) {
            log.error("Error initializing MQTT client", e);
        }
    }

    private void subscribeToTopic(String topic) {
        mqttClient.subscribeWith()
                .topicFilter(topic)
                .qos(MqttQos.AT_LEAST_ONCE)
                .send();
        log.info("Subscribed to topic: {}", topic);
    }

    private void handleMessage(Mqtt5Publish publish) {
        try {
            String topic = publish.getTopic().toString();
            byte[] payload = publish.getPayloadAsBytes();
            String message = new String(payload, StandardCharsets.UTF_8);

            log.debug("Received message on topic {}: {}", topic, message);

            // Process the message based on the topic
            if (topic.equals(temperatureTopic)) {
                processTemperature(message);
            } else if (topic.equals(humidityTopic)) {
                processHumidity(message);
            } else if (topic.equals(soilMoistureTopic)) {
                processSoilMoisture(message);
            }

        } catch (Exception e) {
            log.error("Error processing MQTT message", e);
        }
    }

    /**
     * Extract numeric value from a string that may contain units
     */
    private Double extractNumericValue(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return null;
        }

        String cleanPayload = payload.trim();

        try {
            return Double.parseDouble(cleanPayload);
        } catch (NumberFormatException e) {
            Matcher matcher = NUMERIC_PATTERN.matcher(cleanPayload);
            if (matcher.find()) {
                try {
                    return Double.parseDouble(matcher.group(1));
                } catch (NumberFormatException ex) {
                    log.warn("Could not parse numeric value from: {}", cleanPayload);
                    return null;
                }
            }
        }

        log.warn("No numeric value found in payload: {}", cleanPayload);
        return null;
    }

    private void processTemperature(String payload) {
        try {
            Double temperature = extractNumericValue(payload);
            if (temperature != null) {
                currentReadings.put("temperature", temperature);
                updateDeviceStatus();
                log.debug("Updated temperature: {} at {}", temperature, LocalDateTime.now());
                broadcastCurrentReadings();
            } else {
                log.warn("Failed to extract temperature from payload: {}", payload);
            }
        } catch (Exception e) {
            log.error("Error processing temperature data", e);
        }
    }

    private void processHumidity(String payload) {
        try {
            Double humidity = extractNumericValue(payload);
            if (humidity != null) {
                currentReadings.put("humidity", humidity);
                updateDeviceStatus();
                log.debug("Updated humidity: {} at {}", humidity, LocalDateTime.now());
                broadcastCurrentReadings();
            } else {
                log.warn("Failed to extract humidity from payload: {}", payload);
            }
        } catch (Exception e) {
            log.error("Error processing humidity data", e);
        }
    }

    private void processSoilMoisture(String payload) {
        try {
            Double soilMoisture = extractNumericValue(payload);
            if (soilMoisture != null) {
                currentReadings.put("soilMoisture", soilMoisture);
                updateDeviceStatus();
                log.debug("Updated soil moisture: {} at {}", soilMoisture, LocalDateTime.now());
                broadcastCurrentReadings();
            } else {
                log.warn("Failed to extract soil moisture from payload: {}", payload);
            }
        } catch (Exception e) {
            log.error("Error processing soil moisture data", e);
        }
    }

    /**
     * Update device status when new data is received
     */
    private void updateDeviceStatus() {
        LocalDateTime now = LocalDateTime.now();
        lastUpdateTime = now;

        if (!deviceOnline) {
            deviceOnline = true;
            lastOnlineTime = now;
            deviceOfflineSince = null;
            log.info("Device came online at: {} (reconnected)", lastOnlineTime);
            broadcastDeviceStatus();
        } else {
            // Device was already online, just update the last seen time
            log.debug("Device heartbeat received at: {}", now);
        }
    }

    /**
     * Periodically check device status with faster detection
     */
    private void checkDeviceStatus() {
        try {
            boolean currentlyOnline = isDataFresh();
            LocalDateTime now = LocalDateTime.now();

            if (deviceOnline && !currentlyOnline) {
                // Device just went offline
                deviceOnline = false;
                deviceOfflineSince = now;
                long secondsSinceLastUpdate = lastUpdateTime != null ?
                        ChronoUnit.SECONDS.between(lastUpdateTime, now) : 0;

                log.warn("Device went OFFLINE at: {} ({}s since last update: {})",
                        deviceOfflineSince, secondsSinceLastUpdate, lastUpdateTime);
                broadcastDeviceStatus();

            } else if (!deviceOnline && currentlyOnline) {
                // Device came back online (this shouldn't happen without updateDeviceStatus being called)
                deviceOnline = true;
                lastOnlineTime = now;
                deviceOfflineSince = null;
                log.info("Device came back ONLINE at: {} (status check detected)", lastOnlineTime);
                broadcastDeviceStatus();
            }

            // Debug logging for monitoring
            if (lastUpdateTime != null) {
                long secondsSinceUpdate = ChronoUnit.SECONDS.between(lastUpdateTime, now);
                if (secondsSinceUpdate > 15) { // Log warning if no updates for 15+ seconds
                    log.debug("No updates for {}s. Last update: {}, Device status: {}",
                            secondsSinceUpdate, lastUpdateTime, deviceOnline ? "ONLINE" : "OFFLINE");
                }
            }

        } catch (Exception e) {
            log.error("Error checking device status", e);
        }
    }

    /**
     * Broadcast device status via WebSocket
     */
    private void broadcastDeviceStatus() {
        try {
            Map<String, Object> status = getDeviceStatus();
            messagingTemplate.convertAndSend("/topic/device-status", status);
            log.info("Broadcasted device status: {} - {}", status.get("status"),
                    deviceOnline ? "Online since: " + lastOnlineTime : "Offline since: " + deviceOfflineSince);
        } catch (Exception e) {
            log.error("Error broadcasting device status", e);
        }
    }

    /**
     * Check if the current data is fresh enough (using seconds now for faster detection)
     */
    private boolean isDataFresh() {
        if (lastUpdateTime == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        long secondsSinceUpdate = ChronoUnit.SECONDS.between(lastUpdateTime, now);

        return secondsSinceUpdate <= DATA_FRESHNESS_SECONDS;
    }

    /**
     * Check if enough time has passed since the last save
     */
    private boolean shouldSaveData() {
        if (lastSavedTime == null) {
            return true;
        }

        LocalDateTime now = LocalDateTime.now();
        long secondsSinceLastSave = ChronoUnit.SECONDS.between(lastSavedTime, now);

        return secondsSinceLastSave >= MIN_SAVE_INTERVAL_SECONDS;
    }

    /**
     * Aggregate current readings and save as a single EnvironmentData object
     * Now with better logging and freshness checks
     */
    private void aggregateAndSaveData() {
        try {
            LocalDateTime now = LocalDateTime.now();

            if (currentReadings.isEmpty()) {
                log.debug("No current readings available, skipping save");
                return;
            }

            // Check if data is fresh before saving
            if (!isDataFresh()) {
                long secondsSinceUpdate = lastUpdateTime != null ?
                        ChronoUnit.SECONDS.between(lastUpdateTime, now) : 0;
                log.debug("Data is not fresh ({}s old, threshold: {}s), skipping save. Last update: {}",
                        secondsSinceUpdate, DATA_FRESHNESS_SECONDS, lastUpdateTime);
                return;
            }

            // Check if enough time has passed since last save
            if (!shouldSaveData()) {
                long secondsSinceLastSave = lastSavedTime != null ?
                        ChronoUnit.SECONDS.between(lastSavedTime, now) : 0;
                log.debug("Not enough time has passed since last save ({}s ago, minimum: {}s)",
                        secondsSinceLastSave, MIN_SAVE_INTERVAL_SECONDS);
                return;
            }

            // Create and save environment data
            EnvironmentData data = new EnvironmentData();
            data.setTemperature(currentReadings.getOrDefault("temperature", 0.0));
            data.setHumidity(currentReadings.getOrDefault("humidity", 0.0));
            data.setSoilMoisture(currentReadings.getOrDefault("soilMoisture", 0.0));
            data.setTimestamp(lastUpdateTime); // Use the actual last update time, not current time

            environmentDataService.saveEnvironmentData(data);
            lastSavedTime = now;

            long secondsSinceUpdate = ChronoUnit.SECONDS.between(lastUpdateTime, now);
            log.info("Saved FRESH environment data: T={}, H={}, SM={} (Data age: {}s, Last update: {})",
                    data.getTemperature(), data.getHumidity(), data.getSoilMoisture(),
                    secondsSinceUpdate, lastUpdateTime);

        } catch (Exception e) {
            log.error("Error aggregating and saving environment data", e);
        }
    }

    /**
     * Broadcast current readings via WebSocket
     */
    private void broadcastCurrentReadings() {
        try {
            EnvironmentData currentData = new EnvironmentData();
            currentData.setTemperature(currentReadings.getOrDefault("temperature", 0.0));
            currentData.setHumidity(currentReadings.getOrDefault("humidity", 0.0));
            currentData.setSoilMoisture(currentReadings.getOrDefault("soilMoisture", 0.0));
            currentData.setTimestamp(lastUpdateTime); // Use actual last update time

            messagingTemplate.convertAndSend("/topic/environment", currentData);
            log.debug("Broadcasted current readings via WebSocket (timestamp: {})", lastUpdateTime);

        } catch (Exception e) {
            log.error("Error broadcasting current readings", e);
        }
    }

    /**
     * Get current real-time readings
     */
    public EnvironmentData getCurrentReadings() {
        EnvironmentData data = new EnvironmentData();
        data.setTemperature(currentReadings.getOrDefault("temperature", 0.0));
        data.setHumidity(currentReadings.getOrDefault("humidity", 0.0));
        data.setSoilMoisture(currentReadings.getOrDefault("soilMoisture", 0.0));
        data.setTimestamp(lastUpdateTime);
        return data;
    }

    /**
     * Check if the IoT device is currently online
     */
    public boolean isDeviceOnline() {
        return deviceOnline && isDataFresh();
    }

    /**
     * Get comprehensive device status information
     */
    public Map<String, Object> getDeviceStatus() {
        Map<String, Object> status = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        boolean actuallyOnline = isDataFresh();
        status.put("online", actuallyOnline);
        status.put("status", actuallyOnline ? "ONLINE" : "OFFLINE");
        status.put("lastUpdateTime", lastUpdateTime);
        status.put("lastOnlineTime", lastOnlineTime);
        status.put("offlineSince", deviceOfflineSince);

        if (lastUpdateTime != null) {
            long secondsSinceLastUpdate = ChronoUnit.SECONDS.between(lastUpdateTime, now);
            status.put("secondsSinceLastUpdate", secondsSinceLastUpdate);
            status.put("minutesSinceLastUpdate", secondsSinceLastUpdate / 60);
        }

        if (deviceOfflineSince != null) {
            long secondsOffline = ChronoUnit.SECONDS.between(deviceOfflineSince, now);
            long minutesOffline = secondsOffline / 60;
            status.put("secondsOffline", secondsOffline);
            status.put("minutesOffline", minutesOffline);
        }

        // Add freshness info
        status.put("dataFreshThresholdSeconds", DATA_FRESHNESS_SECONDS);
        status.put("isDataFresh", actuallyOnline);

        return status;
    }

    /**
     * Get the last update time
     */
    public LocalDateTime getLastUpdateTime() {
        return lastUpdateTime;
    }

    /**
     * Manual method to force offline status (for testing)
     */
    public void setDeviceOffline() {
        if (deviceOnline) {
            deviceOnline = false;
            deviceOfflineSince = LocalDateTime.now();
            log.warn("Device manually set to OFFLINE at: {}", deviceOfflineSince);
            broadcastDeviceStatus();
        }
    }

    /**
     * Get detailed connection statistics
     */
    public Map<String, Object> getConnectionStats() {
        Map<String, Object> stats = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();

        stats.put("currentTime", now);
        stats.put("lastUpdateTime", lastUpdateTime);
        stats.put("deviceOnline", deviceOnline);
        stats.put("dataFresh", isDataFresh());
        stats.put("freshThresholdSeconds", DATA_FRESHNESS_SECONDS);
        stats.put("statusCheckIntervalSeconds", STATUS_CHECK_INTERVAL_SECONDS);

        if (lastUpdateTime != null) {
            stats.put("secondsSinceLastUpdate", ChronoUnit.SECONDS.between(lastUpdateTime, now));
        }

        return stats;
    }

    @PreDestroy
    public void destroy() {
        try {
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduler.shutdown();
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            }
            if (mqttClient != null) {
                mqttClient.disconnect();
                log.info("MQTT client disconnected");
            }
        } catch (Exception e) {
            log.error("Error disconnecting MQTT client", e);
        }
    }

    public void publishMessage(String topic, String payload) {
        try {
            mqttClient.publishWith()
                    .topic(topic)
                    .payload(payload.getBytes(StandardCharsets.UTF_8))
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .send();
            log.debug("Published message to topic {}: {}", topic, payload);
        } catch (Exception e) {
            log.error("Error publishing MQTT message", e);
        }
    }
}