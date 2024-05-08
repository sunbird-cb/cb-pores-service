package com.igot.karmaquest.service;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * @author Mahesh RV
 * @author Ruksana
 */
public interface KarmaQuestService {

    Object getInterest(String interestId);

    Object insertInterest(JsonNode requestBodyMap);

}
