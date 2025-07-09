package com.__25J_323.HarvestPrediction.repository;

import com.__25J_323.HarvestPrediction.model.RipenessData;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RipenessDataRepository extends MongoRepository<RipenessData, String> {

}
