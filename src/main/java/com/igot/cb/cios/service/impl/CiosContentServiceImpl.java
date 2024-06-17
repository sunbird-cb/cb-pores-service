package com.igot.cb.cios.service.impl;


import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.cios.dto.ObjectDto;
import com.igot.cb.cios.entity.ExternalContentEntity;
import com.igot.cb.cios.entity.CiosContentEntity;
import com.igot.cb.cios.repository.CiosRepository;
import com.igot.cb.cios.repository.ExternalContentRepository;
import com.igot.cb.cios.service.CiosContentService;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.exceptions.CustomException;
import com.igot.cb.pores.util.Constants;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;


@Service
@Slf4j
public class CiosContentServiceImpl implements CiosContentService {
    @Autowired
    private ExternalContentRepository contentRepository;

    @Autowired
    private CiosRepository ciosRepository;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    EsUtilService esUtilService;

    @Autowired
    private RedisTemplate<String, SearchResult> redisTemplate;

    @Value("${search.result.redis.ttl}")
    private long searchResultRedisTtl;

    @Autowired
    private CacheService cacheService;

    public String generateId() {
        long nanoTime = System.nanoTime();
        long count = new AtomicLong().incrementAndGet();
        return Constants.ID_PREFIX + nanoTime + count;
    }

    @Override
    public Object fetchDataByContentId(String contentId){
        log.info("getting content by id: " + contentId);
        if (StringUtils.isEmpty(contentId)) {
            log.error("CiosContentServiceImpl::read:Id not found");
            throw new CustomException(Constants.ERROR,"contentId is mandatory",HttpStatus.BAD_REQUEST);
        }
        String cachedJson = cacheService.getCache(contentId);
        Object response = null;
        if (StringUtils.isNotEmpty(cachedJson)) {
            log.info("CiosContentServiceImpl::read:Record coming from redis cache");
            try {
                response=objectMapper.readValue(cachedJson, new TypeReference<Object>() {
                       });
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }else{
            Optional<CiosContentEntity> optionalJsonNodeEntity = ciosRepository.findByContentIdAndIsActive(contentId, true);
            if (optionalJsonNodeEntity.isPresent()) {
                CiosContentEntity ciosContentEntity = optionalJsonNodeEntity.get();
                cacheService.putCache(contentId, ciosContentEntity.getCiosData());
                log.info("CiosContentServiceImpl::read:Record coming from postgres db");
                response=objectMapper.convertValue(ciosContentEntity.getCiosData(), new TypeReference<Object>() {
                                        });
            } else {
                log.error("Invalid Id: {}", contentId);
                throw new CustomException(Constants.ERROR,"No data found for given Id",HttpStatus.BAD_REQUEST);
            }
        }
        return response;
    }

    @Override
    public Object deleteContent(String contentId) {
        log.info("CiosContentServiceImpl::read:inside the method");
        Optional<CiosContentEntity> ciosContentEntity = ciosRepository.findByContentIdAndIsActive(
                contentId,true);
        Timestamp currentTime = new Timestamp(System.currentTimeMillis());
        if (ciosContentEntity.isPresent()) {
            CiosContentEntity fetchedEntity = ciosContentEntity.get();
            JsonNode fetchedJsonData = fetchedEntity.getCiosData();
            ((ObjectNode) fetchedJsonData.path("content")).put(Constants.UPDATED_ON, String.valueOf(currentTime));
            ((ObjectNode) fetchedJsonData.path("content")).put(Constants.IS_ACTIVE,Constants.ACTIVE_STATUS_FALSE);
            fetchedEntity.setCiosData(fetchedJsonData);
            fetchedEntity.setLastUpdatedOn(currentTime);
            fetchedEntity.setIsActive(false);
            ciosRepository.save(fetchedEntity);
            Map<String, Object> map = objectMapper.convertValue(fetchedEntity.getCiosData().get("content"), Map.class);
            esUtilService.addDocument(Constants.CIOS_INDEX_NAME, Constants.INDEX_TYPE,fetchedEntity.getContentId(), map, Constants.ES_REQUIRED_FIELDS_JSON_FILE);
            cacheService.deleteCache(fetchedEntity.getContentId());
            log.info("deleted content");
            return "Content with id : " + contentId + " is deleted";
        } else {
            log.error("no data found");
            throw new CustomException(Constants.ERROR, Constants.NO_DATA_FOUND, HttpStatus.NOT_FOUND);
        }

    }



    @Override
    public Object onboardContent(List<ObjectDto> data) {
        try {
            log.info("CiosContentServiceImpl::createOrUpdateContent");
            for (ObjectDto dto : data) {
                CiosContentEntity ciosContentEntity = null;
                Optional<ExternalContentEntity> dataFetched =
                        contentRepository.findByExternalId(dto.getIdentifier());
                if (dataFetched.isPresent()) {
                    log.info("data present in external table");
                    ciosContentEntity = createNewContent(dataFetched.get().getCiosData(),dto);
                    ExternalContentEntity externalContentEntity = dataFetched.get();
                    externalContentEntity.setIsActive(true);
                    contentRepository.save(externalContentEntity);
                }
                ciosRepository.save(ciosContentEntity);
                Map<String, Object> map = objectMapper.convertValue(ciosContentEntity.getCiosData().get("content"), Map.class);
                log.info("Id of content created in Igot: " + ciosContentEntity.getContentId());
                cacheService.putCache(ciosContentEntity.getContentId(), ciosContentEntity.getCiosData());
                esUtilService.addDocument(Constants.CIOS_INDEX_NAME,Constants.INDEX_TYPE,ciosContentEntity.getContentId(), map, Constants.ES_REQUIRED_FIELDS_JSON_FILE);
            }
            return "Success";
        } catch (Exception e) {
            throw new CustomException("ERROR", e.getMessage(),HttpStatus.BAD_REQUEST);
        }
    }

    private CiosContentEntity createNewContent(JsonNode jsonNode,ObjectDto dto) {
        log.info("SidJobServiceImpl::createOrUpdateContent:updating the content");
        try {
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            CiosContentEntity igotContent = new CiosContentEntity();
            String externalId = jsonNode.path("content").path("externalId").asText();
            Optional<CiosContentEntity> igotContentEntity = ciosRepository.findByExternalId(externalId);
            if (!igotContentEntity.isPresent()) {
                igotContent.setContentId(generateId());
                igotContent.setExternalId(externalId);
                igotContent.setCreatedOn(currentTime);
                igotContent.setLastUpdatedOn(currentTime);
                igotContent.setIsActive(Constants.ACTIVE_STATUS);
                ((ObjectNode) jsonNode.path("content")).put("contentId", generateId());
                ((ObjectNode) jsonNode.path("content")).put(Constants.CREATED_ON, String.valueOf(currentTime));
                ((ObjectNode) jsonNode.path("content")).put(Constants.LAST_UPDATED_ON, String.valueOf(currentTime));
                ((ObjectNode) jsonNode.path("content")).put(Constants.IS_ACTIVE, Constants.ACTIVE_STATUS);
                ((ObjectNode) jsonNode.path("content")).put(Constants.COMPETENCY,dto.getCompetencyArea());
                addSearchTags(jsonNode);
                igotContent.setCiosData(jsonNode);
            } else {
                igotContent.setContentId(igotContentEntity.get().getContentId());
                igotContent.setExternalId(igotContentEntity.get().getExternalId());
                igotContent.setCreatedOn(igotContentEntity.get().getCreatedOn());
                igotContent.setLastUpdatedOn(currentTime);
                igotContent.setIsActive(Constants.ACTIVE_STATUS);
                ((ObjectNode) jsonNode.path("content")).put("contentId", igotContentEntity.get().getContentId());
                ((ObjectNode) jsonNode.path("content")).put(Constants.CREATED_ON, String.valueOf(igotContent.getCreatedOn()));
                ((ObjectNode) jsonNode.path("content")).put(Constants.LAST_UPDATED_ON, String.valueOf(currentTime));
                ((ObjectNode) jsonNode.path("content")).put(Constants.COMPETENCY,dto.getCompetencyArea());
                igotContent.setCiosData(jsonNode);
            }
            return igotContent;
        }catch (Exception e){
            throw new CustomException(Constants.ERROR,e.getMessage(),HttpStatus.BAD_REQUEST);
        }
    }
    private JsonNode addSearchTags(JsonNode formattedData) {
        List<String> searchTags = new ArrayList<>();
        searchTags.add(formattedData.path("content").get("topic").textValue().toLowerCase());
        searchTags.add(formattedData.path("content").get("name").textValue().toLowerCase());
        ArrayNode searchTagsArray = objectMapper.valueToTree(searchTags);
        ((ObjectNode) formattedData.path("content")).set("searchTags", searchTagsArray);
        return formattedData;
    }

    @Override
    public SearchResult searchCotent(SearchCriteria searchCriteria) {
        log.info("CiosContentServiceImpl::searchCotent");
        SearchResult searchResult = redisTemplate.opsForValue()
                .get(generateRedisJwtTokenKey(searchCriteria));
        if (searchResult != null) {
            log.info("CiosContentServiceImpl::searchCotent:  search result fetched from redis");
            return searchResult;
        }
        try {
            searchResult= esUtilService.searchDocuments(Constants.CIOS_INDEX_NAME, searchCriteria);
            redisTemplate.opsForValue()
                    .set(generateRedisJwtTokenKey(searchCriteria), searchResult, searchResultRedisTtl,
                            TimeUnit.SECONDS);
            return searchResult;
        } catch (Exception e) {
            throw new CustomException("ERROR", e.getMessage(),HttpStatus.BAD_REQUEST);
        }

    }

    private String generateRedisJwtTokenKey(Object requestPayload) {
        if (requestPayload != null) {
            try {
                String reqJsonString = objectMapper.writeValueAsString(requestPayload);
                return JWT.create()
                        .withClaim(Constants.REQUEST_PAYLOAD, reqJsonString)
                        .sign(Algorithm.HMAC256(Constants.JWT_SECRET_KEY));
            } catch (JsonProcessingException e) {
                log.error("Error occurred while converting json object to json string", e);
            }
        }
        return "";
    }
    public void validatePayload(String fileName, JsonNode payload) {
        log.info("CiosContentServiceImpl::validatePayload");
        try {
            JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance();
            InputStream schemaStream = schemaFactory.getClass().getResourceAsStream(fileName);
            JsonSchema schema = schemaFactory.getSchema(schemaStream);

            Set<ValidationMessage> validationMessages = schema.validate(payload);
            if (!validationMessages.isEmpty()) {
                StringBuilder errorMessage = new StringBuilder("Validation error(s): \n");
                for (ValidationMessage message : validationMessages) {
                    errorMessage.append(message.getMessage()).append("\n");
                }
                throw new CustomException(Constants.ERROR, errorMessage.toString(),HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            throw new CustomException(Constants.ERROR, "Failed to validate payload: " + e.getMessage(),HttpStatus.BAD_REQUEST);
        }
    }
}
