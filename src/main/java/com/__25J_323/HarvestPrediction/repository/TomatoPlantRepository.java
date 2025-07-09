package com.__25J_323.HarvestPrediction.repository;

import com.__25J_323.HarvestPrediction.model.TomatoPlant;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TomatoPlantRepository extends MongoRepository<TomatoPlant, String> {


}
