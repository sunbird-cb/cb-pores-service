package com.igot.cb.cios.controller;



import com.igot.cb.cios.dto.ObjectDto;
import com.igot.cb.cios.service.CiosContentService;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/cios/v1")
@Slf4j
public class CiosController {
    @Autowired
    CiosContentService ciosContentService;

    @PostMapping(value = "/onboardContent")
    public ResponseEntity<Object> onboardContent(@RequestBody List<ObjectDto> data) {
        return new ResponseEntity<>(ciosContentService.onboardContent(data), HttpStatus.OK);
    }

    @PostMapping(value = "/search/content")
    public ResponseEntity<?> searchContent(@RequestBody SearchCriteria searchCriteria) {
        SearchResult searchResult = ciosContentService.searchCotent(searchCriteria);
        return new ResponseEntity<>(searchResult, HttpStatus.OK);
    }

    @DeleteMapping("/content/delete")
    public ResponseEntity<Object> deleteContent(@RequestParam String contentId) {
        return new ResponseEntity<>(ciosContentService.deleteContent(contentId), HttpStatus.OK);
    }

    @GetMapping("/content/fetchById")
    public ResponseEntity<Object> fetchData(@RequestParam String contentId) {
        return new ResponseEntity<>(ciosContentService.fetchDataByContentId(contentId), HttpStatus.OK);
    }
}
