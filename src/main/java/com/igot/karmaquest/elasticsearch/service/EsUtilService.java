package com.igot.karmaquest.elasticsearch.service;

import com.igot.karmaquest.elasticsearch.dto.SearchCriteria;
import com.igot.karmaquest.elasticsearch.dto.SearchResult;
import java.util.Map;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.builder.SearchSourceBuilder;

public interface EsUtilService {
  RestStatus addDocument(String esIndexName, String type, String id, Map<String, Object> document);

  RestStatus updateDocument(String index, String indexType, String entityId, Map<String, Object> document);

  void deleteDocument(String documentId, String esIndexName);

  void deleteDocumentsByCriteria(String esIndexName, SearchSourceBuilder sourceBuilder);

  SearchResult searchDocuments(String esIndexName, SearchCriteria searchCriteria) throws Exception;

}
