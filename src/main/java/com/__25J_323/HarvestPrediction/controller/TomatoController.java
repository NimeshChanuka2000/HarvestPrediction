package com.__25J_323.HarvestPrediction.controller;

import com.__25J_323.HarvestPrediction.DTO.HarvestPredictionRequest;
import com.__25J_323.HarvestPrediction.DTO.HarvestPredictionResponse;
import com.__25J_323.HarvestPrediction.model.RipenessData;
import com.__25J_323.HarvestPrediction.model.TomatoPlant;
import com.__25J_323.HarvestPrediction.repository.TomatoPlantRepository;
import com.__25J_323.HarvestPrediction.service.HarvestPredictionService;
import com.__25J_323.HarvestPrediction.service.RipenessDetectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/tomato")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin
public class TomatoController {
    private final HarvestPredictionService harvestPredictionService;
    private final RipenessDetectionService ripenessDetectionService;
    private final TomatoPlantRepository tomatoPlantRepository;

    @PostMapping("/plants")
    public ResponseEntity<TomatoPlant> savePlant(@RequestBody TomatoPlant plant) {
        log.info("Saving new plant: {}", plant.getPlantName());
        TomatoPlant savedPlant = tomatoPlantRepository.save(plant);
        return ResponseEntity.ok(savedPlant);
    }

    // Get all plants
    @GetMapping("/plants")
    public ResponseEntity<List<TomatoPlant>> getAllPlants() {
        log.info("Fetching all plants");
        List<TomatoPlant> plants = tomatoPlantRepository.findAll();
        return ResponseEntity.ok(plants);
    }

    // Get plant by ID
    @GetMapping("/plants/{id}")
    public ResponseEntity<TomatoPlant> getPlant(@PathVariable String id) {
        log.info("Fetching plant with ID: {}", id);
        return tomatoPlantRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Update plant
    @PutMapping("/plants/{id}")
    public ResponseEntity<TomatoPlant> updatePlant(@PathVariable String id, @RequestBody TomatoPlant plant) {
        log.info("Updating plant with ID: {}", id);
        return tomatoPlantRepository.findById(id)
                .map(existingPlant -> {
                    plant.setId(id);
                    return ResponseEntity.ok(tomatoPlantRepository.save(plant));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Delete plant
    @DeleteMapping("/plants/{id}")
    public ResponseEntity<Void> deletePlant(@PathVariable String id) {
        log.info("Deleting plant with ID: {}", id);
        if (tomatoPlantRepository.existsById(id)) {
            tomatoPlantRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    // Predict harvest for existing plant
    @PostMapping("/plants/{id}/predict")
    public ResponseEntity<HarvestPredictionResponse> predictForPlant(@PathVariable String id) {
        log.info("Predicting harvest for plant ID: {}", id);

        return tomatoPlantRepository.findById(id)
                .map(plant -> {
                    HarvestPredictionRequest request = new HarvestPredictionRequest();
                    request.setPlantId(plant.getId());
                    request.setPlantingDate(plant.getPlantingDate());
                    request.setVariety(plant.getVariety());

                    HarvestPredictionResponse response = harvestPredictionService.predictHarvestDate(request);
                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Get current prediction status for a plant
    @GetMapping("/plants/{id}/status")
    public ResponseEntity<HarvestPredictionResponse> getPlantStatus(@PathVariable String id) {
        log.info("Getting status for plant ID: {}", id);

        return tomatoPlantRepository.findById(id)
                .map(plant -> {
                    HarvestPredictionResponse response = new HarvestPredictionResponse();
                    response.setPlantId(plant.getId());
                    response.setPlantingDate(plant.getPlantingDate());
                    response.setPredictedHarvestDate(plant.getPredictedHarvestDate());

                    if (plant.getPredictedHarvestDate() != null) {
                        long daysToHarvest = ChronoUnit.DAYS.between(LocalDate.now(), plant.getPredictedHarvestDate());
                        response.setDaysToHarvest((int) daysToHarvest);
                    }

                    return ResponseEntity.ok(response);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // Original predict harvest endpoint
    @PostMapping("/predict-harvest")
    public ResponseEntity<HarvestPredictionResponse> predictHarvest(@RequestBody @Valid HarvestPredictionRequest request) {
        HarvestPredictionResponse response = harvestPredictionService.predictHarvestDate(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/detect-ripeness", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RipenessData> detectRipeness(
            @RequestParam("image") MultipartFile image){
        RipenessData result = ripenessDetectionService.detectRipeness(image);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/ripeness-history")
    public ResponseEntity<List<RipenessData>> getRipenessHistory() {
        List<RipenessData> history = ripenessDetectionService.getRipenessHistory();
        return ResponseEntity.ok(history);
    }
}
