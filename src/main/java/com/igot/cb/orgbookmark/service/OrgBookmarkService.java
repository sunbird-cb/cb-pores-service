package com.igot.cb.orgbookmark.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.pores.dto.CustomResponse;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import org.springframework.stereotype.Service;

@Service
public interface OrgBookmarkService {
    CustomResponse create(JsonNode orgDetails);

    CustomResponse read(String id);

    CustomResponse search(SearchCriteria searchCriteria);

    String delete(String id);
}
