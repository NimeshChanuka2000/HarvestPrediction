package com.__25J_323.HarvestPrediction.DTO;

import lombok.Data;

import java.time.LocalDate;

@Data
public class HarvestPredictionResponse {

    private String plantId;
    private String variety;
    private LocalDate plantingDate;
    private LocalDate predictedHarvestDate;
    private int daysToHarvest;
}
