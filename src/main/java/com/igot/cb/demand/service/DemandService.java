package com.igot.cb.demand.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.pores.dto.CustomResponse;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import org.springframework.stereotype.Service;

@Service
public interface DemandService {
  CustomResponse createDemand(JsonNode demandDetails);

  CustomResponse readDemand(String id);

  CustomResponse searchDemand(SearchCriteria searchCriteria);

  String delete(String id);
  CustomResponse updateDemandStatus(JsonNode demandDetails);

  CustomResponse updateDemand(JsonNode demandsDetails);
}
