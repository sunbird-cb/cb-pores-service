package com.igot.cb.contentprovider.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.pores.dto.CustomResponse;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.util.ApiResponse;

public interface ContentPartnerService {
    CustomResponse createOrUpdate(JsonNode demandsJson);

    ApiResponse read(String id);

    CustomResponse searchEntity(SearchCriteria searchCriteria);

    String delete(String id);

}
