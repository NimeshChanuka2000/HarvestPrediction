package com.__25J_323.HarvestPrediction.service;

import com.__25J_323.HarvestPrediction.model.EnvironmentData;
import com.__25J_323.HarvestPrediction.repository.EnvironmentDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class EnvironmentDataService {

    private final EnvironmentDataRepository environmentDataRepository;

    public EnvironmentData saveEnvironmentData(EnvironmentData data) {
        if (data.getTimestamp() == null) {
            data.setTimestamp(LocalDateTime.now());
        }
        return environmentDataRepository.save(data);
    }

    public List<EnvironmentData> getRecentEnvironmentData() {
        return environmentDataRepository.findTop24ByOrderByTimestampDesc();
    }

    public List<EnvironmentData> getEnvironmentDataBetween(LocalDateTime start, LocalDateTime end) {
        return environmentDataRepository.findByTimestampBetweenOrderByTimestampDesc(start, end);
    }
}
