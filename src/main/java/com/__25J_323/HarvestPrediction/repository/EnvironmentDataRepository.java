package com.__25J_323.HarvestPrediction.repository;

import com.__25J_323.HarvestPrediction.model.EnvironmentData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface EnvironmentDataRepository extends MongoRepository<EnvironmentData, String> {
    List<EnvironmentData> findByTimestampBetweenOrderByTimestampDesc(LocalDateTime start, LocalDateTime end);
    List<EnvironmentData> findTop24ByOrderByTimestampDesc();
}
