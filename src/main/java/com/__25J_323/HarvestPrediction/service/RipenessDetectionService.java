package com.__25J_323.HarvestPrediction.service;

import com.__25J_323.HarvestPrediction.model.RipenessData;
import com.__25J_323.HarvestPrediction.repository.RipenessDataRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RipenessDetectionService {

    private final RipenessDataRepository ripenessDataRepository;
    private final RestTemplate restTemplate;

    @Value("${spring.servlet.multipart.location:./uploads}")
    private String uploadDir;

    @Value("${flask.api.url:http://localhost:5000}")
    private String flaskApiUrl;

    @PostConstruct
    public void init() {
        // Create upload directory if it doesn't exist
        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (IOException e) {
            log.error("Could not create upload directory", e);
        }
    }

    public RipenessData detectRipeness(MultipartFile image) {
        try {
            // Save uploaded image
            String filename = UUID.randomUUID() + "_" + image.getOriginalFilename();
            Path filePath = Paths.get(uploadDir, filename);
            image.transferTo(filePath);

            // Call Flask API for ripeness detection
            Map<String, Object> detectionResult = callFlaskRipenessDetection(image);

            // Parse the detected tomatoes from the response
            List<RipenessData.DetectedTomato> detectedTomatoes = new ArrayList<>();
            List<Map<String, Object>> detections = (List<Map<String, Object>>) detectionResult.get("detectedTomatoes");

            for (Map<String, Object> detection : detections) {
                RipenessData.DetectedTomato tomato = new RipenessData.DetectedTomato();
                tomato.setRipenessState((String) detection.get("ripenessState"));
                tomato.setConfidence((Double) detection.get("confidence"));

                Map<String, Integer> bbox = (Map<String, Integer>) detection.get("boundingBox");
                RipenessData.BoundingBox box = new RipenessData.BoundingBox();
                box.setX(bbox.get("x"));
                box.setY(bbox.get("y"));
                box.setWidth(bbox.get("width"));
                box.setHeight(bbox.get("height"));
                tomato.setBoundingBox(box);

                detectedTomatoes.add(tomato);
            }

            // Save results to database
            RipenessData ripenessData = new RipenessData();
            ripenessData.setImageUrl("/images/" + filename);
            ripenessData.setDetectedTomatoes(detectedTomatoes);
            ripenessData.setTimestamp(LocalDateTime.now());

            return ripenessDataRepository.save(ripenessData);
        } catch (Exception e) {
            log.error("Error detecting ripeness", e);
            throw new RuntimeException("Failed to process image for ripeness detection", e);
        }
    }

    public List<RipenessData> getRipenessHistory() {
        return ripenessDataRepository.findAll();
    }

    private Map<String, Object> callFlaskRipenessDetection(MultipartFile image) throws IOException {
        String url = flaskApiUrl + "/detect-ripeness";

        // Create headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        // Create a MultiValueMap to contain the image file
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(image.getBytes()) {
            @Override
            public String getFilename() {
                return image.getOriginalFilename();
            }
        };

        body.add("image", resource);

        // Create the request
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        // Make the API call
        ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);

        return response.getBody();
    }
}