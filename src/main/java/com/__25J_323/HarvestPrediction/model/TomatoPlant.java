package com.__25J_323.HarvestPrediction.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Document(collection = "tomato_plant")
public class TomatoPlant {

    @Id
    private String id;
    private String plantName;
    private String variety;
    private LocalDate plantingDate;
    private LocalDate predictedHarvestDate;
    private LocalDateTime lastUpdated;
}
