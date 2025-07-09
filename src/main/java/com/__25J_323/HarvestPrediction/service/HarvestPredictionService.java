package com.__25J_323.HarvestPrediction.service;

import com.__25J_323.HarvestPrediction.DTO.HarvestPredictionRequest;
import com.__25J_323.HarvestPrediction.DTO.HarvestPredictionResponse;
import com.__25J_323.HarvestPrediction.model.EnvironmentData;
import com.__25J_323.HarvestPrediction.model.TomatoPlant;
import com.__25J_323.HarvestPrediction.repository.EnvironmentDataRepository;
import com.__25J_323.HarvestPrediction.repository.TomatoPlantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class HarvestPredictionService {

    private final TomatoPlantRepository tomatoPlantRepository;
    private final EnvironmentDataRepository environmentDataRepository;
    private final RestTemplate restTemplate;
    private final MqttService mqttService; // Add this dependency

    @Value("${flask.api.url:http://localhost:5000}")
    private String flaskApiUrl;

    // Optimal environmental conditions
    private static final double OPTIMAL_TEMPERATURE = 25.0; // °C
    private static final double OPTIMAL_HUMIDITY = 60.0; // %
    private static final double OPTIMAL_SOIL_MOISTURE = 40.0; // %

    // Environmental impact factors (how much each parameter affects growth)
    private static final double TEMPERATURE_IMPACT_FACTOR = 0.4;
    private static final double HUMIDITY_IMPACT_FACTOR = 0.3;
    private static final double SOIL_MOISTURE_IMPACT_FACTOR = 0.3;

    // Temperature ranges for optimal growth
    private static final double MIN_TEMP_THRESHOLD = 15.0;
    private static final double MAX_TEMP_THRESHOLD = 35.0;

    // Humidity ranges for optimal growth
    private static final double MIN_HUMIDITY_THRESHOLD = 30.0;
    private static final double MAX_HUMIDITY_THRESHOLD = 90.0;

    // Soil moisture ranges for optimal growth
    private static final double MIN_SOIL_MOISTURE_THRESHOLD = 20.0;
    private static final double MAX_SOIL_MOISTURE_THRESHOLD = 80.0;

    public HarvestPredictionResponse predictHarvestDate(HarvestPredictionRequest request) {
        try {
            log.info("Starting enhanced harvest prediction for variety: {}, planting date: {}",
                    request.getVariety(), request.getPlantingDate());

            // Get real-time environment data first
            EnvironmentData currentData = mqttService.getCurrentReadings();
            log.info("Current real-time conditions - Temperature: {}°C, Humidity: {}%, Soil Moisture: {}%",
                    currentData.getTemperature(), currentData.getHumidity(), currentData.getSoilMoisture());

            // Get recent historical data for trend analysis
            List<EnvironmentData> recentData = environmentDataRepository.findTop24ByOrderByTimestampDesc();
            log.info("Retrieved {} historical environment data records", recentData.size());

            // Calculate predicted harvest date using enhanced algorithm
            LocalDate predictedDate = calculateEnhancedHarvestDate(
                    request.getPlantingDate(),
                    request.getVariety(),
                    currentData,
                    recentData
            );

            log.info("Enhanced predicted harvest date: {}", predictedDate);

            // Save or update plant information
            TomatoPlant plant;
            if (request.getPlantId() != null && !request.getPlantId().isEmpty()) {
                plant = tomatoPlantRepository.findById(request.getPlantId())
                        .orElse(new TomatoPlant());
            } else {
                plant = new TomatoPlant();
            }

            plant.setPlantingDate(request.getPlantingDate());
            plant.setVariety(request.getVariety());
            plant.setPredictedHarvestDate(predictedDate);
            plant.setLastUpdated(LocalDateTime.now());

            TomatoPlant savedPlant = tomatoPlantRepository.save(plant);

            // Calculate days to harvest
            long daysToHarvest = ChronoUnit.DAYS.between(LocalDate.now(), predictedDate);
            log.info("Days to harvest: {}", daysToHarvest);

            // Prepare response
            HarvestPredictionResponse response = new HarvestPredictionResponse();
            response.setPlantId(savedPlant.getId());
            response.setPlantingDate(savedPlant.getPlantingDate());
            response.setPredictedHarvestDate(savedPlant.getPredictedHarvestDate());
            response.setDaysToHarvest((int) daysToHarvest);

            return response;
        } catch (Exception e) {
            log.error("Error predicting harvest date", e);
            throw new RuntimeException("Failed to predict harvest date: " + e.getMessage(), e);
        }
    }

    /**
     * Enhanced harvest date calculation using real-time environmental conditions
     */
    private LocalDate calculateEnhancedHarvestDate(LocalDate plantingDate, String variety,
                                                   EnvironmentData currentData, List<EnvironmentData> historicalData) {
        try {
            log.info("Calculating enhanced harvest date with real-time environmental data");

            // Get base maturation days for the variety
            int baseDays = getBaseDaysForVariety(variety);
            log.info("Base maturation days for variety '{}': {}", variety, baseDays);

            // Calculate environmental impact using current real-time data
            double environmentalMultiplier = calculateEnvironmentalImpact(currentData);
            log.info("Environmental impact multiplier based on current conditions: {}", environmentalMultiplier);

            // Calculate historical trend impact (last 7 days average if available)
            double trendMultiplier = calculateTrendImpact(historicalData);
            log.info("Historical trend multiplier: {}", trendMultiplier);

            // Combine current conditions with historical trends (70% current, 30% trend)
            double finalMultiplier = (environmentalMultiplier * 0.7) + (trendMultiplier * 0.3);
            log.info("Final combined multiplier: {}", finalMultiplier);

            // Apply multiplier to base days
            int adjustedDays = (int) Math.round(baseDays * finalMultiplier);

            // Ensure reasonable bounds (30-120 days)
            adjustedDays = Math.max(30, Math.min(120, adjustedDays));

            LocalDate harvestDate = plantingDate.plusDays(adjustedDays);

            log.info("Enhanced prediction: Base={}d, Multiplier={}, Adjusted={}d, Harvest={}",
                    baseDays, finalMultiplier, adjustedDays, harvestDate);

            return harvestDate;

        } catch (Exception e) {
            log.error("Error in enhanced calculation, falling back to simple method", e);
            return calculateFallbackHarvestDate(plantingDate, variety);
        }
    }

    /**
     * Calculate environmental impact based on current conditions
     * Returns multiplier: <1.0 = faster growth (better conditions), >1.0 = slower growth (poor conditions)
     */
    private double calculateEnvironmentalImpact(EnvironmentData currentData) {
        double temperature = currentData.getTemperature() != null ? currentData.getTemperature() : OPTIMAL_TEMPERATURE;
        double humidity = currentData.getHumidity() != null ? currentData.getHumidity() : OPTIMAL_HUMIDITY;
        double soilMoisture = currentData.getSoilMoisture() != null ? currentData.getSoilMoisture() : OPTIMAL_SOIL_MOISTURE;

        // Calculate individual parameter impacts
        double tempImpact = calculateTemperatureImpact(temperature);
        double humidityImpact = calculateHumidityImpact(humidity);
        double soilMoistureImpact = calculateSoilMoistureImpact(soilMoisture);

        // Weighted combination of all factors
        double combinedImpact = (tempImpact * TEMPERATURE_IMPACT_FACTOR) +
                (humidityImpact * HUMIDITY_IMPACT_FACTOR) +
                (soilMoistureImpact * SOIL_MOISTURE_IMPACT_FACTOR);

        log.debug("Environmental impacts - Temperature: {}, Humidity: {}, Soil: {}, Combined: {}",
                tempImpact, humidityImpact, soilMoistureImpact, combinedImpact);

        return combinedImpact;
    }

    /**
     * Calculate temperature impact on growth rate
     */
    private double calculateTemperatureImpact(double temperature) {
        if (temperature < MIN_TEMP_THRESHOLD) {
            // Too cold - significantly slower growth
            return 1.0 + ((MIN_TEMP_THRESHOLD - temperature) * 0.05);
        } else if (temperature > MAX_TEMP_THRESHOLD) {
            // Too hot - slower growth due to stress
            return 1.0 + ((temperature - MAX_TEMP_THRESHOLD) * 0.03);
        } else {
            // Calculate how close to optimal (25°C)
            double deviation = Math.abs(temperature - OPTIMAL_TEMPERATURE);
            if (deviation <= 2.0) {
                // Very close to optimal - faster growth
                return 0.9;
            } else if (deviation <= 5.0) {
                // Good conditions - normal to slightly faster growth
                return 0.95;
            } else {
                // Suboptimal but acceptable - slightly slower growth
                return 1.0 + (deviation * 0.01);
            }
        }
    }

    /**
     * Calculate humidity impact on growth rate
     */
    private double calculateHumidityImpact(double humidity) {
        if (humidity < MIN_HUMIDITY_THRESHOLD) {
            // Too dry - slower growth
            return 1.0 + ((MIN_HUMIDITY_THRESHOLD - humidity) * 0.02);
        } else if (humidity > MAX_HUMIDITY_THRESHOLD) {
            // Too humid - risk of disease, slower growth
            return 1.0 + ((humidity - MAX_HUMIDITY_THRESHOLD) * 0.02);
        } else {
            // Calculate how close to optimal (60%)
            double deviation = Math.abs(humidity - OPTIMAL_HUMIDITY);
            if (deviation <= 5.0) {
                // Very close to optimal
                return 0.95;
            } else if (deviation <= 15.0) {
                // Good conditions
                return 1.0;
            } else {
                // Suboptimal
                return 1.0 + (deviation * 0.005);
            }
        }
    }

    /**
     * Calculate soil moisture impact on growth rate
     */
    private double calculateSoilMoistureImpact(double soilMoisture) {
        if (soilMoisture < MIN_SOIL_MOISTURE_THRESHOLD) {
            // Too dry - significantly slower growth
            return 1.0 + ((MIN_SOIL_MOISTURE_THRESHOLD - soilMoisture) * 0.03);
        } else if (soilMoisture > MAX_SOIL_MOISTURE_THRESHOLD) {
            // Too wet - root problems, slower growth
            return 1.0 + ((soilMoisture - MAX_SOIL_MOISTURE_THRESHOLD) * 0.025);
        } else {
            // Calculate how close to optimal (40%)
            double deviation = Math.abs(soilMoisture - OPTIMAL_SOIL_MOISTURE);
            if (deviation <= 5.0) {
                // Very close to optimal
                return 0.92;
            } else if (deviation <= 10.0) {
                // Good conditions
                return 0.97;
            } else {
                // Suboptimal
                return 1.0 + (deviation * 0.008);
            }
        }
    }

    /**
     * Calculate trend impact based on historical data (last 7 days)
     */
    private double calculateTrendImpact(List<EnvironmentData> historicalData) {
        if (historicalData == null || historicalData.isEmpty()) {
            return 1.0; // No impact if no historical data
        }

        // Take up to last 7 days of data (assuming hourly data)
        int maxRecords = Math.min(historicalData.size(), 168); // 24 hours * 7 days

        double avgTemperature = 0;
        double avgHumidity = 0;
        double avgSoilMoisture = 0;
        int validRecords = 0;

        for (int i = 0; i < maxRecords; i++) {
            EnvironmentData data = historicalData.get(i);
            if (data.getTemperature() != null && data.getHumidity() != null && data.getSoilMoisture() != null) {
                avgTemperature += data.getTemperature();
                avgHumidity += data.getHumidity();
                avgSoilMoisture += data.getSoilMoisture();
                validRecords++;
            }
        }

        if (validRecords == 0) {
            return 1.0;
        }

        avgTemperature /= validRecords;
        avgHumidity /= validRecords;
        avgSoilMoisture /= validRecords;

        // Create average environment data
        EnvironmentData avgData = new EnvironmentData();
        avgData.setTemperature(avgTemperature);
        avgData.setHumidity(avgHumidity);
        avgData.setSoilMoisture(avgSoilMoisture);

        log.debug("Historical averages over {} records - Temp: {}, Humidity: {}, Soil: {}",
                validRecords, avgTemperature, avgHumidity, avgSoilMoisture);

        return calculateEnvironmentalImpact(avgData);
    }

    /**
     * Get base maturation days for different tomato varieties
     */
    private int getBaseDaysForVariety(String variety) {
        if (variety == null) {
            return 75; // Default
        }

        String lowerVariety = variety.toLowerCase();
        if (lowerVariety.contains("cherry")) {
            return 65; // Cherry tomatoes mature faster
        } else if (lowerVariety.contains("beef") || lowerVariety.contains("large")) {
            return 85; // Larger varieties take longer
        } else if (lowerVariety.contains("roma") || lowerVariety.contains("paste")) {
            return 75; // Roma/paste tomatoes
        } else if (lowerVariety.contains("early")) {
            return 60; // Early varieties
        } else if (lowerVariety.contains("late")) {
            return 90; // Late varieties
        } else {
            return 75; // Standard variety default
        }
    }

    private LocalDate calculateHarvestDateWithFlaskAPI(LocalDate plantingDate, String variety, List<EnvironmentData> environmentData) {
        try {
            log.info("Attempting to call Flask API for harvest prediction");
            String url = flaskApiUrl + "/predict-harvest";

            // Create headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Create request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("plantingDate", plantingDate.toString());
            requestBody.put("variety", variety != null ? variety : "Cherry");

            // Convert environment data to format needed by Flask API
            List<Map<String, Object>> envDataList = environmentData.stream()
                    .limit(10) // Limit to recent 10 records to avoid payload issues
                    .map(data -> {
                        Map<String, Object> envMap = new HashMap<>();
                        // Proper null checking with Double wrapper classes
                        envMap.put("temperature", data.getTemperature() != null ? data.getTemperature() : 25.0);
                        envMap.put("humidity", data.getHumidity() != null ? data.getHumidity() : 60.0);
                        envMap.put("soilMoisture", data.getSoilMoisture() != null ? data.getSoilMoisture() : 40.0);
                        return envMap;
                    })
                    .toList();

            requestBody.put("environmentData", envDataList);

            log.info("Flask API request body: {}", requestBody);

            // Create the request
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // Make the API call
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);
            log.info("Flask API response: {}", response);

            if (response != null && response.containsKey("predictedHarvestDate")) {
                // Extract the predicted harvest date
                String harvestDateStr = (String) response.get("predictedHarvestDate");
                log.info("Received harvest date string: {}", harvestDateStr);

                try {
                    // Try different date formats
                    LocalDate parsedDate = null;

                    // Try ISO format first (YYYY-MM-DD)
                    try {
                        parsedDate = LocalDate.parse(harvestDateStr);
                    } catch (DateTimeParseException e1) {
                        // Try other common formats
                        try {
                            parsedDate = LocalDate.parse(harvestDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                        } catch (DateTimeParseException e2) {
                            try {
                                parsedDate = LocalDate.parse(harvestDateStr, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
                            } catch (DateTimeParseException e3) {
                                log.warn("Could not parse date: {}, using fallback calculation", harvestDateStr);
                            }
                        }
                    }

                    if (parsedDate != null) {
                        // Validate the parsed date is reasonable
                        long daysDiff = ChronoUnit.DAYS.between(plantingDate, parsedDate);
                        if (daysDiff >= 30 && daysDiff <= 120) { // Reasonable tomato growing period
                            return parsedDate;
                        } else {
                            log.warn("Parsed date seems unreasonable ({}days from planting), using fallback", daysDiff);
                        }
                    }
                } catch (Exception e) {
                    log.error("Error parsing date from Flask API response: {}", harvestDateStr, e);
                }
            } else {
                log.warn("Flask API response missing predictedHarvestDate field");
            }

        } catch (Exception e) {
            log.error("Error calling Flask API for harvest prediction", e);
        }

        // Fallback to enhanced calculation
        EnvironmentData currentData = mqttService.getCurrentReadings();
        List<EnvironmentData> recentData = environmentDataRepository.findTop24ByOrderByTimestampDesc();
        return calculateEnhancedHarvestDate(plantingDate, variety, currentData, recentData);
    }

    private LocalDate calculateFallbackHarvestDate(LocalDate plantingDate, String variety) {
        log.info("Using fallback calculation for variety: {}", variety);

        int defaultDays = getBaseDaysForVariety(variety);
        LocalDate harvestDate = plantingDate.plusDays(defaultDays);

        log.info("Fallback calculation: {} days from planting = {}", defaultDays, harvestDate);
        return harvestDate;
    }
}