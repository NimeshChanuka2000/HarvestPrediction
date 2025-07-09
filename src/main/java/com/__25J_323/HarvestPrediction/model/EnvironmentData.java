package com.__25J_323.HarvestPrediction.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "environment_data")
public class EnvironmentData {

    @Id
    private String id;
    private Double temperature;
    private Double humidity;
    private Double soilMoisture;
    private LocalDateTime timestamp;
}
