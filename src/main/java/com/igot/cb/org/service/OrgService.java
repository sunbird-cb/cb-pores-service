package com.igot.cb.org.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.pores.util.ApiResponse;
import org.springframework.web.bind.annotation.RequestParam;

public interface OrgService {

    public ApiResponse readFramework(String frameworkName, String orgId, String userAuthToken);
}
