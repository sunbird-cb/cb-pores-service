package com.igot.karmaquest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.karmaquest.dto.CustomResponse;
import com.igot.karmaquest.elasticsearch.dto.SearchCriteria;

public interface DemandService {
  CustomResponse createDemand(JsonNode demandDetails);

  CustomResponse readDemand(String id);

  CustomResponse searchDemand(SearchCriteria searchCriteria);

  String delete(String id);

}
