package com.igot.cb.orgbookmark.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.orgbookmark.service.OrgBookmarkService;
import com.igot.cb.pores.dto.CustomResponse;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/org/bookmark")
public class OrgBookmarkController {
    @Autowired
    private OrgBookmarkService orgBookmarkService;

    @PostMapping("/create")
    public ResponseEntity<CustomResponse> create(@RequestBody JsonNode orgDetails) {
        CustomResponse response = orgBookmarkService.create(orgDetails);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @GetMapping("/read/{id}")
    public ResponseEntity<?> read(@PathVariable String id) {
        CustomResponse response = orgBookmarkService.read(id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody SearchCriteria searchCriteria) {
        CustomResponse response = orgBookmarkService.search(searchCriteria);
        return new ResponseEntity<>(response, response.getResponseCode());
    }

    @DeleteMapping("/delete/{id}")
    public ResponseEntity<String> delete(@PathVariable String id) {
        String response = orgBookmarkService.delete(id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }
}
