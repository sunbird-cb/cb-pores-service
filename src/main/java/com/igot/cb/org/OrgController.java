package com.igot.cb.org;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.org.service.OrgService;
import com.igot.cb.pores.util.ApiResponse;
import com.igot.cb.pores.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/org")
@Slf4j
public class OrgController {

    private @Autowired OrgService orgService;

    @PostMapping(value = "/framework/read")
    public ResponseEntity<Object> readFramework(@RequestBody JsonNode node,  @RequestHeader(Constants.X_AUTH_TOKEN) String userAuthToken) {
        ApiResponse response = orgService.readFramework(node,userAuthToken);
        return new ResponseEntity<>(response,response.getResponseCode());
    }

}
