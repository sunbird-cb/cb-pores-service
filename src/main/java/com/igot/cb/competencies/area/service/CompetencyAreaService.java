package com.igot.cb.competencies.area.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.pores.dto.CustomResponse;
import org.springframework.stereotype.Repository;
import org.springframework.web.multipart.MultipartFile;

@Repository
public interface  CompetencyAreaService {

  void loadCompetencyArea(MultipartFile file);

 public CustomResponse createCompArea(JsonNode competencyArea);

  CustomResponse updateCompArea(JsonNode updatedCompArea);
}
