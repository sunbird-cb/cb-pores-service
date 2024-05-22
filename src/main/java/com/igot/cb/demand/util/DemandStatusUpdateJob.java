/*
package com.igot.cb.demand.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.igot.cb.demand.service.DemandServiceImpl;
import com.igot.cb.pores.util.Constants;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.SourceFunction;
import org.apache.http.client.fluent.Request;
import org.springframework.beans.factory.annotation.Autowired;

public class DemandStatusUpdateJob {
    @Autowired
    private DemandServiceImpl demandService;

    public void startJob() throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        DataStream<JsonNode> contentStream = env.addSource(new SourceFunction<JsonNode>() {
            @Override
            public void run(SourceContext<JsonNode> sourceContext) throws Exception {
                while (true) {
                    String response = Request.Get("https://cbp.karmayogi.nic.in/apis/proxies/v8/action/content/v3/hierarchy/do_11405850829826457612?mode=edit")
                            .execute()
                            .returnContent()
                            .asString();

                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode contentData = mapper.readTree(response);
                    sourceContext.collect(contentData);
                    Thread.sleep(60000);
                }
            }

            @Override
            public void cancel() {

            }
        });
        DataStream<String> updateDemands = contentStream.map(new MapFunction<JsonNode, String>() {
            @Override
            public String map(JsonNode contentData) throws Exception {
                String demandId = contentData.get(Constants.DEMAND_ID).asText();
                String contentStatus = contentData.get(Constants.STATUS).asText();

                String newStatus;
                if ("draft".equals(contentStatus)) {
                    newStatus = Constants.IN_PROGRESS;
                } else if ("published".equals(contentStatus)) {
                    newStatus = Constants.FULFILL;
                } else {
                    newStatus = "Unknown";
                }
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode demandUpdateNode = mapper.createObjectNode();
                demandUpdateNode.put(Constants.DEMAND_ID, demandId);
                demandUpdateNode.put(Constants.NEW_STATUS, newStatus);
                demandService.updateDemandStatus(demandUpdateNode);
                return demandId + " updated to " + newStatus;
            }
        });
    }
}
*/
