package com.__25J_323.HarvestPrediction.DTO;

import lombok.Data;

import java.time.LocalDate;

@Data
public class HarvestPredictionRequest {
    private String plantId;
    private LocalDate plantingDate;
    private String variety;
    private Object environmentData;
}
