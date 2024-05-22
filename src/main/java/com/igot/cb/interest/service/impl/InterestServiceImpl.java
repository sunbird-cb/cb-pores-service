package com.igot.cb.interest.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.igot.cb.demand.entity.DemandEntity;
import com.igot.cb.demand.repository.DemandRepository;
import com.igot.cb.demand.service.DemandServiceImpl;
import com.igot.cb.interest.entity.Interest;
import com.igot.cb.interest.repository.InterestRepository;
import com.igot.cb.interest.service.InterestService;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.pores.dto.CustomResponse;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.exceptions.CustomException;
import com.igot.cb.pores.util.Constants;
import java.sql.Timestamp;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class InterestServiceImpl implements InterestService {

  @Autowired
  private InterestRepository interestRepository;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private EsUtilService esUtilService;

  @Autowired
  private CacheService cacheService;

  private Logger logger = LoggerFactory.getLogger(InterestServiceImpl.class);

  @Autowired
  private DemandRepository demandRepository;

  @Override
  public CustomResponse createInterest(Interest interest) {
    CustomResponse response = new CustomResponse();
    try {
      log.info("InterestService::createInterest:creating interest");
      Random random = new Random();
      int randomNumber = 1000000 + random.nextInt(8999999);
      String id = String.valueOf(randomNumber);
      Interest jsonNodeEntity = interest;
      jsonNodeEntity.setInterestId(id);
      Timestamp currentTime = new Timestamp(System.currentTimeMillis());
      jsonNodeEntity.setCreatedOn(currentTime);
      jsonNodeEntity.setUpdatedOn(currentTime);

      Interest saveJsonEntity = interestRepository.save(jsonNodeEntity);
      Optional<DemandEntity> demandEntity = demandRepository.findById(interest.getDemandId());
      JsonNode fetchedDemandDetais = demandEntity.get().getData();
      ((ObjectNode) fetchedDemandDetais).put(Constants.INTEREST_COUNT, fetchedDemandDetais.get(Constants.INTEREST_COUNT).asInt()+1);
      ((ObjectNode) fetchedDemandDetais).put(Constants.CREATED_ON, String.valueOf(currentTime));
      ((ObjectNode) fetchedDemandDetais).put(Constants.UPDATED_ON, String.valueOf(currentTime));
      Map<String, Object> esMap = objectMapper.convertValue(fetchedDemandDetais, Map.class);
      esUtilService.addDocument(Constants.INDEX_NAME, Constants.INDEX_TYPE, jsonNodeEntity.getDemandId(), esMap);
      cacheService.putCache(id, fetchedDemandDetais);
      log.info("InterestService::createInterest:interest created");
      Map<String, Object> map = objectMapper.convertValue(saveJsonEntity, Map.class);
      map.put(Constants.INTEREST_ID, id);
      response.setResult(map);
      response.setMessage(Constants.SUCCESSFULLY_CREATED);
      response.setResponseCode(HttpStatus.OK);
      return response;
    } catch (Exception e) {
      logger.error("Error occurred while creating interst", e);
      throw new CustomException("error while processing", e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
