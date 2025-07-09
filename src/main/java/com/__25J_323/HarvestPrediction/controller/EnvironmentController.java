package com.__25J_323.HarvestPrediction.controller;

import com.__25J_323.HarvestPrediction.model.EnvironmentData;
import com.__25J_323.HarvestPrediction.service.EnvironmentDataService;
import com.__25J_323.HarvestPrediction.service.MqttService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/environment")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class EnvironmentController {

    private final EnvironmentDataService environmentDataService;
    private final MqttService mqttService;

    /**
     * Get real-time current environment data
     */
    @GetMapping("/current")
    public ResponseEntity<EnvironmentData> getCurrentData() {
        try {
            EnvironmentData currentData = mqttService.getCurrentReadings();
            return ResponseEntity.ok(currentData);
        } catch (Exception e) {
            log.error("Error getting current environment data", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get device status information
     */
    @GetMapping("/device-status")
    public ResponseEntity<Map<String, Object>> getDeviceStatus() {
        try {
            Map<String, Object> status = mqttService.getDeviceStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error getting device status", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Check if device is online (simple boolean response)
     */
    @GetMapping("/device-online")
    public ResponseEntity<Boolean> isDeviceOnline() {
        try {
            boolean online = mqttService.isDeviceOnline();
            return ResponseEntity.ok(online);
        } catch (Exception e) {
            log.error("Error checking device online status", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get recent historical environment data
     */
    @GetMapping("/recent")
    public ResponseEntity<List<EnvironmentData>> getRecentData() {
        try {
            List<EnvironmentData> data = environmentDataService.getRecentEnvironmentData();
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("Error getting recent environment data", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get environment data between specific dates
     */
    @GetMapping("/history")
    public ResponseEntity<List<EnvironmentData>> getHistoricalData(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        try {
            List<EnvironmentData> data = environmentDataService.getEnvironmentDataBetween(start, end);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("Error getting historical environment data", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get environment data for the last N hours
     */
    @GetMapping("/last-hours/{hours}")
    public ResponseEntity<List<EnvironmentData>> getLastHoursData(@PathVariable int hours) {
        try {
            LocalDateTime end = LocalDateTime.now();
            LocalDateTime start = end.minusHours(hours);
            List<EnvironmentData> data = environmentDataService.getEnvironmentDataBetween(start, end);
            return ResponseEntity.ok(data);
        } catch (Exception e) {
            log.error("Error getting last {} hours environment data", hours, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Manually trigger data save (for testing purposes)
     */
    @PostMapping("/save-current")
    public ResponseEntity<EnvironmentData> saveCurrentData() {
        try {
            EnvironmentData currentData = mqttService.getCurrentReadings();
            EnvironmentData savedData = environmentDataService.saveEnvironmentData(currentData);
            return ResponseEntity.ok(savedData);
        } catch (Exception e) {
            log.error("Error saving current environment data", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get statistics for environment data
     */
    @GetMapping("/stats")
    public ResponseEntity<EnvironmentStats> getEnvironmentStats() {
        try {
            List<EnvironmentData> recentData = environmentDataService.getRecentEnvironmentData();

            if (recentData.isEmpty()) {
                return ResponseEntity.ok(new EnvironmentStats());
            }

            // Calculate statistics
            double avgTemp = recentData.stream().mapToDouble(EnvironmentData::getTemperature).average().orElse(0.0);
            double avgHumidity = recentData.stream().mapToDouble(EnvironmentData::getHumidity).average().orElse(0.0);
            double avgSoilMoisture = recentData.stream().mapToDouble(EnvironmentData::getSoilMoisture).average().orElse(0.0);

            double maxTemp = recentData.stream().mapToDouble(EnvironmentData::getTemperature).max().orElse(0.0);
            double minTemp = recentData.stream().mapToDouble(EnvironmentData::getTemperature).min().orElse(0.0);

            double maxHumidity = recentData.stream().mapToDouble(EnvironmentData::getHumidity).max().orElse(0.0);
            double minHumidity = recentData.stream().mapToDouble(EnvironmentData::getHumidity).min().orElse(0.0);

            double maxSoilMoisture = recentData.stream().mapToDouble(EnvironmentData::getSoilMoisture).max().orElse(0.0);
            double minSoilMoisture = recentData.stream().mapToDouble(EnvironmentData::getSoilMoisture).min().orElse(0.0);

            EnvironmentStats stats = new EnvironmentStats();
            stats.setAverageTemperature(avgTemp);
            stats.setAverageHumidity(avgHumidity);
            stats.setAverageSoilMoisture(avgSoilMoisture);
            stats.setMaxTemperature(maxTemp);
            stats.setMinTemperature(minTemp);
            stats.setMaxHumidity(maxHumidity);
            stats.setMinHumidity(minHumidity);
            stats.setMaxSoilMoisture(maxSoilMoisture);
            stats.setMinSoilMoisture(minSoilMoisture);
            stats.setTotalReadings(recentData.size());

            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting environment statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // Inner class for statistics
    public static class EnvironmentStats {
        private double averageTemperature;
        private double averageHumidity;
        private double averageSoilMoisture;
        private double maxTemperature;
        private double minTemperature;
        private double maxHumidity;
        private double minHumidity;
        private double maxSoilMoisture;
        private double minSoilMoisture;
        private int totalReadings;

        // Getters and setters
        public double getAverageTemperature() { return averageTemperature; }
        public void setAverageTemperature(double averageTemperature) { this.averageTemperature = averageTemperature; }

        public double getAverageHumidity() { return averageHumidity; }
        public void setAverageHumidity(double averageHumidity) { this.averageHumidity = averageHumidity; }

        public double getAverageSoilMoisture() { return averageSoilMoisture; }
        public void setAverageSoilMoisture(double averageSoilMoisture) { this.averageSoilMoisture = averageSoilMoisture; }

        public double getMaxTemperature() { return maxTemperature; }
        public void setMaxTemperature(double maxTemperature) { this.maxTemperature = maxTemperature; }

        public double getMinTemperature() { return minTemperature; }
        public void setMinTemperature(double minTemperature) { this.minTemperature = minTemperature; }

        public double getMaxHumidity() { return maxHumidity; }
        public void setMaxHumidity(double maxHumidity) { this.maxHumidity = maxHumidity; }

        public double getMinHumidity() { return minHumidity; }
        public void setMinHumidity(double minHumidity) { this.minHumidity = minHumidity; }

        public double getMaxSoilMoisture() { return maxSoilMoisture; }
        public void setMaxSoilMoisture(double maxSoilMoisture) { this.maxSoilMoisture = maxSoilMoisture; }

        public double getMinSoilMoisture() { return minSoilMoisture; }
        public void setMinSoilMoisture(double minSoilMoisture) { this.minSoilMoisture = minSoilMoisture; }

        public int getTotalReadings() { return totalReadings; }
        public void setTotalReadings(int totalReadings) { this.totalReadings = totalReadings; }
    }
}

