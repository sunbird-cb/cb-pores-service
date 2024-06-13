package com.igot.cb.cios.service.impl;


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
import com.igot.cb.pores.elasticsearch.dto.SearchCriteria;
import com.igot.cb.pores.elasticsearch.dto.SearchResult;
import com.igot.cb.pores.elasticsearch.service.EsUtilService;
import com.igot.cb.pores.exceptions.CustomException;
import com.igot.cb.pores.util.Constants;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import lombok.extern.slf4j.Slf4j;
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
    private RedisTemplate redisTemplate;

    @Value("${content.entity.redis.ttl}")
    private long redisTtl;

    public String generateId() {
        long nanoTime = System.nanoTime();
        long count = new AtomicLong().incrementAndGet();
        return Constants.ID_PREFIX + nanoTime + count;
    }

    @Override
    public Object fetchDataByContentId(String contentId) {
        log.info("getting content by id: " + contentId);
        try {
            JsonNode externalContentEntity = (JsonNode) redisTemplate.opsForValue().get(Constants.CIOS_CONTENT_SERVICE_KEY + contentId);
            if (externalContentEntity == null) {
                log.info("Fetch from postgres and add fetched contents into redis");
                Optional<CiosContentEntity> optionalJsonNodeEntity = ciosRepository.findByContentIdAndIsActive(contentId, true);
                externalContentEntity = optionalJsonNodeEntity.get().getCiosData();
                if (externalContentEntity != null) {
                    // Store in Redis if found in PostgreSQL
                    redisTemplate.opsForValue().set(Constants.CIOS_CONTENT_SERVICE_KEY + contentId, externalContentEntity, redisTtl, TimeUnit.SECONDS);
                } else {
                    throw new CustomException(Constants.ERROR, "No such content found", HttpStatus.BAD_REQUEST);
                }
            }
            if (externalContentEntity != null) {
                return externalContentEntity;
            }
        } catch (CustomException e) {
            log.error("Error fetching Content: " + e.getMessage());
            throw new CustomException(Constants.ERROR, "Error fetching content: " + e.getMessage(),HttpStatus.BAD_REQUEST);
        } catch (Exception e) {
            log.error("Error fetching content: " + e.getMessage());
            throw new CustomException(Constants.ERROR, "Failed to fetch content: " + e.getMessage(),HttpStatus.BAD_REQUEST);
        }
        return null;
    }

    @Override
    public Object deleteContent(String contentId) {
        log.info("deleting content by ID: " + contentId);
        try {
            Optional<CiosContentEntity> optExternalContent = ciosRepository.findByContentIdAndIsActive(contentId, true);
            if (optExternalContent.isPresent()) {
                Timestamp currentTime = new Timestamp(System.currentTimeMillis());
                CiosContentEntity externalContent = optExternalContent.get();
                externalContent.setIsActive(false);
                JsonNode jsonNode=externalContent.getCiosData();
                ((ObjectNode) jsonNode.path("content")).put(Constants.IS_ACTIVE,Constants.ACTIVE_STATUS_FALSE);
                ((ObjectNode) jsonNode.path("content")).put(Constants.UPDATED_ON, String.valueOf(currentTime));
                externalContent.setLastUpdatedOn(currentTime);
                externalContent.setCiosData(jsonNode);
                ciosRepository.save(externalContent);
                Map<String, Object> map = objectMapper.convertValue(externalContent.getCiosData().get("content"), Map.class);
                esUtilService.addDocument(Constants.CIOS_INDEX_NAME, Constants.INDEX_TYPE,externalContent.getContentId(), map, Constants.ES_REQUIRED_FIELDS_JSON_FILE);
                String redisKey = Constants.CIOS_CONTENT_SERVICE_KEY + externalContent.getContentId();
                redisTemplate.delete(redisKey);
                return "Content with id : " + contentId + " is deleted ";
            } else {
                return "Content with id : " + contentId + " is not found to be deleted";
            }

        } catch (CustomException e) {
            // Handle AnnouncementException with appropriate status code
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            throw new CustomException(Constants.ERROR, e.getMessage(),HttpStatus.BAD_REQUEST);
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
                redisTemplate.opsForValue()
                        .set(Constants.CIOS_CONTENT_SERVICE_KEY + ciosContentEntity.getContentId(), ciosContentEntity.getCiosData(), redisTtl,
                                TimeUnit.SECONDS);
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
        try {
            return esUtilService.searchDocuments(Constants.CIOS_INDEX_NAME, searchCriteria);
        } catch (Exception e) {
            throw new CustomException("ERROR", e.getMessage(),HttpStatus.BAD_REQUEST);
        }

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
