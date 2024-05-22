package com.igot.cb.interest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.igot.cb.interest.entity.Interest;
import com.igot.cb.pores.dto.CustomResponse;
import org.springframework.stereotype.Service;

@Service
public interface InterestService {
  CustomResponse createInterest(Interest interest);
}
