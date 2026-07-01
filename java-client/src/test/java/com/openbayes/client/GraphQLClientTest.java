package com.openbayes.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphQLClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void buildsRequestBodyWithQueryAndVariables() throws Exception {
        String body = GraphQLClient.buildRequestBody(
                mapper, "mutation Foo { foo }", Map.of("userId", "demo-user", "storageType", "TEMPORARY"));
        JsonNode node = mapper.readTree(body);
        assertEquals("mutation Foo { foo }", node.get("query").asText());
        assertEquals("demo-user", node.get("variables").get("userId").asText());
        assertEquals("TEMPORARY", node.get("variables").get("storageType").asText());
    }

    @Test
    void buildsRequestBodyWithEmptyVariablesWhenNull() throws Exception {
        String body = GraphQLClient.buildRequestBody(mapper, "query Me { me }", null);
        JsonNode node = mapper.readTree(body);
        assertTrue(node.get("variables").isObject());
        assertEquals(0, node.get("variables").size());
    }

    @Test
    void formatsErrorMessageOnly() throws Exception {
        JsonNode errors = mapper.readTree("[{\"message\": \"boom\"}]");
        assertEquals("boom", GraphQLClient.formatErrors(errors));
    }

    @Test
    void formatsErrorWithDetails() throws Exception {
        JsonNode errors = mapper.readTree(
                "[{\"message\": \"bad\", \"extensions\": {\"details\": {\"field\": \"x\"}}}]");
        String formatted = GraphQLClient.formatErrors(errors);
        assertTrue(formatted.startsWith("bad. Details: "));
        assertTrue(formatted.contains("field"));
    }
}
