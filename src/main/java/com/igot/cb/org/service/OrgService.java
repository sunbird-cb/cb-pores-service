package com.igot.cb.org.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.pores.util.ApiResponse;

public interface OrgService {

    public ApiResponse readFramework(JsonNode node, String userAuthToken);
}
