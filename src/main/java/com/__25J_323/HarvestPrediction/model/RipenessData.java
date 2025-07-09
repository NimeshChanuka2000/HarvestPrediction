package com.__25J_323.HarvestPrediction.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Document(collection = "ripeness_data")
public class RipenessData {

    @Id
    private String id;
    private String imageUrl;
    private List<DetectedTomato> detectedTomatoes;
    private LocalDateTime timestamp;

    @Data
    public static class DetectedTomato {
        private String ripenessState;
        private double confidence;
        private BoundingBox boundingBox;
    }

    @Data
    public static class BoundingBox {
        private int x;
        private int y;
        private int width;
        private int height;
    }
}
