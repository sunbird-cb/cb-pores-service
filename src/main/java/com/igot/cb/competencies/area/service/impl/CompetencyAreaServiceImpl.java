package com.igot.cb.competencies.area.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.igot.cb.competencies.area.entity.CompetencyAreaEntity;
import com.igot.cb.competencies.area.repository.CompetencyAreaRepository;
import com.igot.cb.competencies.area.service.CompetencyAreaService;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.pores.dto.CustomResponse;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.exceptions.CustomException;
import com.igot.cb.pores.util.CbServerProperties;
import com.igot.cb.pores.util.Constants;
import com.igot.cb.pores.util.FileProcessService;
import com.igot.cb.pores.util.PayloadValidation;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
public class CompetencyAreaServiceImpl implements CompetencyAreaService {

  @Autowired
  ObjectMapper objectMapper;

  @Autowired
  private PayloadValidation payloadValidation;

  @Autowired
  private EsUtilService esUtilService;

  @Autowired
  private CacheService cacheService;

  @Autowired
  private CbServerProperties cbServerProperties;

  @Autowired
  private CompetencyAreaRepository competencyAreaRepository;

  @Autowired
  private FileProcessService fileProcessService;


  @Override
  public void loadCompetencyArea(MultipartFile file) {
    log.info("CompetencyAreaService::loadDesignationFromExcel");
    List<Map<String, String>> processedData = fileProcessService.processExcelFile(file);
    log.info("No.of processedData from excel: " + processedData.size());
    JsonNode designationJson = objectMapper.valueToTree(processedData);
    AtomicLong startingId = new AtomicLong(competencyAreaRepository.count());
    CompetencyAreaEntity competencyAreaEntity = new CompetencyAreaEntity();
    designationJson.forEach(
        eachDesignation -> {
          String formattedId = String.format("COMAREA-%06d", startingId.incrementAndGet());
          if (!eachDesignation.isNull()) {
            ((ObjectNode) eachDesignation).put(Constants.ID, formattedId);
            ((ObjectNode) eachDesignation).put(Constants.TITLE, eachDesignation.get(Constants.COMPETENCY_AREA_TYPE));
            payloadValidation.validatePayload(Constants.COMP_AREA_PAYLOAD_VALIDATION,
                eachDesignation);
            ((ObjectNode) eachDesignation).put(Constants.STATUS, Constants.ACTIVE);
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            ((ObjectNode) eachDesignation).put(Constants.CREATED_ON, String.valueOf(currentTime));
            ((ObjectNode) eachDesignation).put(Constants.UPDATED_ON, String.valueOf(currentTime));
            ((ObjectNode) eachDesignation).put(Constants.VERSION, 1);
            List<String> searchTags = new ArrayList<>();
            searchTags.add(eachDesignation.get(Constants.TITLE).textValue().toLowerCase());
            ArrayNode searchTagsArray = objectMapper.valueToTree(searchTags);
            ((ObjectNode) eachDesignation).putArray(Constants.SEARCHTAGS).add(searchTagsArray);
            competencyAreaEntity.setId(formattedId);
            competencyAreaEntity.setData(eachDesignation);
            competencyAreaEntity.setIsActive(true);
            competencyAreaEntity.setCreatedOn(currentTime);
            competencyAreaEntity.setUpdatedOn(currentTime);
            competencyAreaRepository.save(competencyAreaEntity);
            log.info(
                "CompetencyAreaService::loadDesignationFromExcel::persited designation in postgres with id: "
                    + formattedId);
            Map<String, Object> map = objectMapper.convertValue(eachDesignation, Map.class);
            esUtilService.addDocument(Constants.COMP_AREA_INDEX_NAME, Constants.INDEX_TYPE,
                formattedId, map, cbServerProperties.getElasticCompAreaJsonPath());
            cacheService.putCache(formattedId, eachDesignation);
            log.info(
                "CompetencyAreaService::loadDesignationFromExcel::created the designation with: "
                    + formattedId);
          }

        });
  }

  @Override
  public CustomResponse createCompArea(JsonNode competencyArea) {
    log.info("CompetencyAreaService::createCompArea");
    CustomResponse response = new CustomResponse();
    try {
      AtomicLong count = new AtomicLong(competencyAreaRepository.count());
      CompetencyAreaEntity competencyAreaEntity = new CompetencyAreaEntity();
      String formattedId = String.format("COMAREA-%06d", count.incrementAndGet());
      ((ObjectNode) competencyArea).put(Constants.STATUS, Constants.ACTIVE);
      ((ObjectNode) competencyArea).put(Constants.ID, formattedId);
      Timestamp currentTime = new Timestamp(System.currentTimeMillis());
      ((ObjectNode) competencyArea).put(Constants.CREATED_ON, String.valueOf(currentTime));
      ((ObjectNode) competencyArea).put(Constants.UPDATED_ON, String.valueOf(currentTime));
      List<String> searchTags = new ArrayList<>();
      searchTags.add(competencyArea.get(Constants.TITLE).textValue().toLowerCase());
      ArrayNode searchTagsArray = objectMapper.valueToTree(searchTags);
      ((ObjectNode) competencyArea).putArray(Constants.SEARCHTAGS).add(searchTagsArray);
      competencyArea = addExtraFields(competencyArea);
      competencyAreaEntity.setId(formattedId);
      competencyAreaEntity.setData(competencyArea);
      competencyAreaEntity.setIsActive(true);
      competencyAreaEntity.setCreatedOn(currentTime);
      competencyAreaEntity.setUpdatedOn(currentTime);
      competencyAreaRepository.save(competencyAreaEntity);
      log.info(
          "CompetencyAreaService::createCompArea::persited comArea in postgres with id: "
              + formattedId);
      Map<String, Object> map = objectMapper.convertValue(competencyArea, Map.class);
      esUtilService.addDocument(Constants.COMP_AREA_INDEX_NAME, Constants.INDEX_TYPE,
          formattedId, map, cbServerProperties.getElasticCompAreaJsonPath());
      cacheService.putCache(formattedId, competencyArea);
      log.info(
          "CompetencyAreaService::createCompArea::created the compArea with: "
              + formattedId);
      response.setMessage(Constants.SUCCESSFULLY_CREATED);
      map.put(Constants.ID, competencyAreaEntity.getId());
      response.setResult(map);
      response.setResponseCode(HttpStatus.OK);
      return response;
    }catch (Exception e){
      log.error("Error occurred while creating compArea", e);
      throw new CustomException("error while processing", e.getMessage(),
          HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  private JsonNode addExtraFields(JsonNode competencyArea) {
    log.info("CompetencyAreaService::updateCompArea");
    String descriptionValue =
        (competencyArea.has(Constants.DESCRIPTION_PAYLOAD) && !competencyArea.get(
            Constants.DESCRIPTION_PAYLOAD).isNull())
            ? competencyArea.get(Constants.DESIGNATION).asText("")
            : "";
    ((ObjectNode) competencyArea).put(Constants.DESCRIPTION, descriptionValue);
    ((ObjectNode) competencyArea).put(Constants.TYPE, Constants.COMPETENCY_AREA_TYPE);
  }

  @Override
  public CustomResponse updateCompArea(JsonNode updatedCompArea) {
    log.info("CompetencyAreaService::updateCompArea");
    payloadValidation.validatePayload(Constants.COMP_AREA_PAYLOAD_VALIDATION,
        updatedCompArea);
    CustomResponse response = new CustomResponse();
    try {
      if (updatedCompArea.has(Constants.ID) && !updatedCompArea.get(Constants.ID)
          .isNull()) {
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        Optional<CompetencyAreaEntity> compArea = competencyAreaRepository.findById(
            updatedCompArea.get(Constants.ID).asText());
        CompetencyAreaEntity competencyAreaEntityUpdated = null;
        if (compArea.isPresent()) {
          JsonNode dataNode = compArea.get().getData();
          Iterator<Entry<String, JsonNode>> fields = updatedCompArea.fields();
          while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String fieldName = field.getKey();
            // Check if the field is present in the update JsonNode
            if (dataNode.has(fieldName)) {
              // Update the main JsonNode with the value from the update JsonNode
              ((ObjectNode) dataNode).set(fieldName, updatedCompArea.get(fieldName));
            } else {
              ((ObjectNode) dataNode).put(fieldName, updatedCompArea.get(fieldName));
            }
          }
          compArea.get().setUpdatedOn(currentTime);
          ((ObjectNode) dataNode).put(Constants.UPDATED_ON, new TextNode(
              convertTimeStampToDate(compArea.get().getUpdatedOn().getTime())));
          competencyAreaEntityUpdated = competencyAreaRepository.save(compArea.get());
          ObjectNode jsonNode = objectMapper.createObjectNode();
          jsonNode.set(Constants.ID,
              new TextNode(updatedCompArea.get(Constants.ID).asText()));
          jsonNode.setAll((ObjectNode) competencyAreaEntityUpdated.getData());
          jsonNode.set(Constants.UPDATED_ON, new TextNode(
              convertTimeStampToDate(competencyAreaEntityUpdated.getUpdatedOn().getTime())));
          Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
          esUtilService.updateDocument(Constants.INDEX_NAME_FOR_ORG_BOOKMARK, Constants.INDEX_TYPE,
              competencyAreaEntityUpdated.getId(), map,
              cbServerProperties.getElasticBookmarkJsonPath());
          cacheService.putCache(competencyAreaEntityUpdated.getId(),
              competencyAreaEntityUpdated.getData());
          log.info("updated the CompArea");
          response.setMessage(Constants.SUCCESSFULLY_CREATED);
          map.put(Constants.ID, competencyAreaEntityUpdated.getId());
          response.setResult(map);
          response.setResponseCode(HttpStatus.OK);
          log.info("InterestServiceImpl::createInterest::persited interest in Pores");
          return response;
        }else {
          response.setMessage("No data found for this id");
          response.setResponseCode(HttpStatus.BAD_REQUEST);
          return response;
        }
      }else {
        response.setMessage("Id is missing");
        response.setResponseCode(HttpStatus.BAD_REQUEST);
        return response;
      }
    }catch (Exception e){
      log.error("Error while processing file: {}", e.getMessage());
      throw new RuntimeException(e.getMessage());
    }
  }

  private String convertTimeStampToDate(long timeStamp) {
    Instant instant = Instant.ofEpochMilli(timeStamp);
    OffsetDateTime dateTime = instant.atOffset(ZoneOffset.UTC);
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy'T'HH:mm:ss.SSS'Z'");
    return dateTime.format(formatter);
  }
}
