package com.igot.cb.orgbookmark.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.igot.cb.orgbookmark.entity.OrgBookmarkEntity;
import com.igot.cb.orgbookmark.repository.OrgBookmarkRepository;
import com.igot.cb.pores.cache.CacheService;
import com.igot.cb.pores.dto.CustomResponse;
import com.igot.cb.pores.dto.RespParam;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class OrgBookmarkServiceImpl implements OrgBookmarkService {
    @Autowired
    private EsUtilService esUtilService;
    @Autowired
    private OrgBookmarkRepository orgBookmarkRepository;
    @Autowired
    private CacheService cacheService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private RedisTemplate<String, SearchResult> redisTemplate;
    private Logger logger = LoggerFactory.getLogger(OrgBookmarkServiceImpl.class);
    @Value("${search.result.redis.ttl}")
    private long searchResultRedisTtl;

    @Override
    public CustomResponse create(JsonNode orgDetails) {
        CustomResponse response = new CustomResponse();
        validatePayload(Constants.PAYLOAD_VALIDATION_FILE_ORG_LIST, orgDetails);
        try {
            log.info("OrgBookmarkService::createOrgList:creating orgList");
            String id = String.valueOf(UUID.randomUUID());
            ((ObjectNode) orgDetails).put(Constants.IS_ACTIVE, Constants.ACTIVE_STATUS);
            Timestamp currentTime = new Timestamp(System.currentTimeMillis());
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            String formattedDate = dateFormat.format(new Date(currentTime.getTime()));
            ((ObjectNode) orgDetails).put(Constants.CREATED_ON, String.valueOf(formattedDate));
            ((ObjectNode) orgDetails).put(Constants.UPDATED_ON, String.valueOf(formattedDate));

            OrgBookmarkEntity jsonNodeEntity = new OrgBookmarkEntity();
            jsonNodeEntity.setOrgBookmarkId(id);
            jsonNodeEntity.setData(orgDetails);
            jsonNodeEntity.setCreatedOn(currentTime);
            jsonNodeEntity.setUpdatedOn(currentTime);

            OrgBookmarkEntity saveJsonEntity = orgBookmarkRepository.save(jsonNodeEntity);

            ObjectMapper objectMapper = new ObjectMapper();
            ObjectNode jsonNode = objectMapper.createObjectNode();
            jsonNode.set(Constants.ORG_ID, new TextNode(saveJsonEntity.getOrgBookmarkId()));
            jsonNode.setAll((ObjectNode) saveJsonEntity.getData());

            Map<String, Object> map = objectMapper.convertValue(jsonNode, Map.class);
            esUtilService.addDocument(Constants.INDEX_NAME, Constants.INDEX_TYPE, id, map);

            cacheService.putCache(jsonNodeEntity.getOrgBookmarkId(), jsonNode);
            log.info("org List created");
            response.setMessage(Constants.SUCCESSFULLY_CREATED);
            map.put(Constants.ORG_ID, id);
            response.setResult(map);
            response.setResponseCode(HttpStatus.OK);
            return response;
        } catch (Exception e) {
            logger.error("Error occurred while creating orgList", e);
            throw new CustomException("error while processing", e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public CustomResponse read(String id) {
        log.info("reading orgList");
        CustomResponse response = new CustomResponse();
        if (StringUtils.isEmpty(id)) {
            logger.error("Id not found");
            response.setResponseCode(HttpStatus.INTERNAL_SERVER_ERROR);
            response.setMessage(Constants.ID_NOT_FOUND);
            return response;
        }
        try {
            String cachedJson = cacheService.getCache(id);
            if (StringUtils.isNotEmpty(cachedJson)) {
                log.info("Record coming from redis cache");
                response.setMessage(Constants.SUCCESSFULLY_READING);
                response
                        .getResult()
                        .put(Constants.RESULT, objectMapper.readValue(cachedJson, new TypeReference<Object>() {
                        }));
            } else {
                Optional<OrgBookmarkEntity> entityOptional = orgBookmarkRepository.findById(id);
                if (entityOptional.isPresent()) {
                    OrgBookmarkEntity orgBookmarkEntity = entityOptional.get();
                    cacheService.putCache(id, orgBookmarkEntity.getData());
                    log.info("Record coming from postgres db");
                    response.setMessage(Constants.SUCCESSFULLY_READING);
                    response
                            .getResult()
                            .put(Constants.RESULT,
                                    objectMapper.convertValue(
                                            orgBookmarkEntity.getData(), new TypeReference<Object>() {
                                            }));
                } else {
                    logger.error("Invalid Id: {}", id);
                    response.setResponseCode(HttpStatus.NOT_FOUND);
                    response.setMessage(Constants.INVALID_ID);
                }
            }
        } catch (Exception e) {
            logger.error("Error while mapping JSON for id {}: {}", id, e.getMessage(), e);
            throw new CustomException(Constants.ERROR, "error while processing", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        return response;
    }

    @Override
    public CustomResponse search(SearchCriteria searchCriteria) {
        log.info("OrgBookmarkServiceImpl::searchOrg");
        CustomResponse response = new CustomResponse();
        SearchResult searchResult = redisTemplate.opsForValue().get(generateRedisJwtTokenKey(searchCriteria));
        if (searchResult != null) {
            log.info("SidJobServiceImpl::searchJobs: job search result fetched from redis");
            response.getResult().put(Constants.RESULT, searchResult);
            createSuccessResponse(response);
            return response;
        }
        String searchString = searchCriteria.getSearchString();
        if (searchString != null && searchString.length() < 2) {
            createErrorResponse(response, "Minimum 3 characters are required to search",
                    HttpStatus.BAD_REQUEST,
                    Constants.FAILED_CONST);
            return response;
        }
        try {
            searchResult =
                    esUtilService.searchDocuments(Constants.INDEX_NAME, searchCriteria);
            response.getResult().put(Constants.RESULT, searchResult);
            createSuccessResponse(response);
            return response;
        } catch (Exception e) {
            createErrorResponse(response, e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR, Constants.FAILED_CONST);
            redisTemplate.opsForValue()
                    .set(generateRedisJwtTokenKey(searchCriteria), searchResult, searchResultRedisTtl,
                            TimeUnit.SECONDS);
            return response;
        }
    }

    public String generateRedisJwtTokenKey(Object requestPayload) {
        if (requestPayload != null) {
            try {
                String reqJsonString = objectMapper.writeValueAsString(requestPayload);
                return JWT.create()
                        .withClaim(Constants.REQUEST_PAYLOAD, reqJsonString)
                        .sign(Algorithm.HMAC256(Constants.JWT_SECRET_KEY));
            } catch (JsonProcessingException e) {
                logger.error("Error occurred while converting json object to json string", e);
            }
        }
        return "";
    }

    @Override
    public String delete(String id) {
        log.info("OrgBookmarkServiceImpl::delete");
        try {
            if (StringUtils.isNotEmpty(id)) {
                Optional<OrgBookmarkEntity> entityOptional = orgBookmarkRepository.findById(id);
                if (entityOptional.isPresent()) {
                    OrgBookmarkEntity josnEntity = entityOptional.get();
                    JsonNode data = josnEntity.getData();
                    Timestamp currentTime = new Timestamp(System.currentTimeMillis());
                    if (data.get(Constants.IS_ACTIVE).asBoolean()) {
                        ((ObjectNode) data).put(Constants.IS_ACTIVE, false);
                        josnEntity.setData(data);
                        josnEntity.setOrgBookmarkId(id);
                        josnEntity.setUpdatedOn(currentTime);
                        OrgBookmarkEntity updateJsonEntity = orgBookmarkRepository.save(josnEntity);
                        Map<String, Object> map = objectMapper.convertValue(data, Map.class);
                        esUtilService.addDocument(Constants.INDEX_NAME, Constants.INDEX_TYPE, id, map);
                        cacheService.putCache(id, data);

                        logger.debug("orgList details deleted successfully");
                        return Constants.DELETED_SUCCESSFULLY;
                    } else
                        log.info("OrgList is already inactive.");
                    return Constants.ALREADY_INACTIVE;
                } else return Constants.NO_DATA_FOUND;
            } else return Constants.INVALID_ID;
        } catch (Exception e) {
            logger.error("Error while deleting org with ID: {}. Exception: {}", id, e.getMessage(), e);
            return Constants.ERROR_WHILE_DELETING_ORG_LIST + id + " " + e.getMessage();
        }
    }

    public void validatePayload(String fileName, JsonNode payload) {
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
                logger.error("Validation Error", errorMessage.toString());
                throw new CustomException("Validation Error", errorMessage.toString(), HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            logger.error("Failed to validate payload", e);
            throw new CustomException("Failed to validate payload", e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }

    public void createSuccessResponse(CustomResponse response) {
        response.setParams(new RespParam());
        response.getParams().setStatus(Constants.SUCCESS);
        response.setResponseCode(HttpStatus.OK);
    }

    public void createErrorResponse(
            CustomResponse response, String errorMessage, HttpStatus httpStatus, String status) {
        response.setParams(new RespParam());
        //response.getParams().setErrorMsg(errorMessage);
        response.getParams().setStatus(status);
        response.setResponseCode(httpStatus);
    }
}
