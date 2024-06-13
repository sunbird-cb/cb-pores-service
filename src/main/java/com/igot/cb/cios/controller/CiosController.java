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
        try {
            return ResponseEntity.ok(ciosContentService.onboardContent(data));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
    }

    @PostMapping(value = "/search/content")
    public ResponseEntity<?> searchContent(@RequestBody SearchCriteria searchCriteria) {
        try {
            SearchResult searchResult = ciosContentService.searchCotent(searchCriteria);
            return ResponseEntity.ok(searchResult);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error during content search: " + e.getMessage());
        }
    }

    @DeleteMapping("/content/delete")
    public ResponseEntity<Object> deleteContent(@RequestParam String contentId) {
        try {
            return ResponseEntity.ok(ciosContentService.deleteContent(contentId));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
    }
    @GetMapping("/content/fetchById")
    public ResponseEntity<Object> fetchData(@RequestParam String contentId) {
        try {
            return ResponseEntity.ok(ciosContentService.fetchDataByContentId(contentId));
        }catch (Exception e){
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(e.getMessage());
        }
    }
}
