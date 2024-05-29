package com.igot.cb.pores.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.demand.service.DemandServiceImpl;
import com.igot.cb.pores.exceptions.CustomException;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import java.io.InputStream;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PayloadValidation {

  private Logger logger = LoggerFactory.getLogger(DemandServiceImpl.class);

  public void validatePayload(String fileName, JsonNode payload) {
    log.info("PayloadValidation::validatePayload:inside");
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
      logger.error("Failed to validate payload",e);
      throw new CustomException("Failed to validate payload", e.getMessage(), HttpStatus.BAD_REQUEST);
    }
  }
}
