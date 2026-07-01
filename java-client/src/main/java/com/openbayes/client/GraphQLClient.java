package com.openbayes.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.openbayes.client.model.OpenBayesException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * Minimal GraphQL-over-HTTP transport using the JDK's {@link HttpClient}.
 *
 * <p>Every request carries {@code Authorization: Bearer <token>} (when a token is set) and an
 * {@code Origin} header, mirroring what the {@code bayes} CLI sends. The single {@link #exec}
 * method returns the {@code data} node and throws {@link OpenBayesException} if the response
 * carries GraphQL {@code errors} or a non-2xx status.
 */
public final class GraphQLClient {

    private final String endpoint;
    private volatile String token;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    public GraphQLClient(String endpoint, String token) {
        this.endpoint = endpoint;
        this.token = token;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public void setToken(String token) {
        this.token = token;
    }

    /** Execute a GraphQL query/mutation and return the {@code data} node. */
    public JsonNode exec(String query, Map<String, Object> variables) {
        HttpResponse<String> resp;
        try {
            String payload = buildRequestBody(mapper, query, variables);
            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header("Origin", endpoint)
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
            if (token != null && !token.isBlank()) {
                req.header("Authorization", "Bearer " + token);
            }
            resp = http.send(req.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new OpenBayesException("GraphQL request failed: " + e.getMessage(), e);
        }

        if (resp.statusCode() / 100 != 2) {
            throw new OpenBayesException("GraphQL HTTP " + resp.statusCode() + ": " + resp.body());
        }

        JsonNode root;
        try {
            root = mapper.readTree(resp.body());
        } catch (JsonProcessingException e) {
            throw new OpenBayesException("GraphQL response not JSON: " + resp.body(), e);
        }

        JsonNode errors = root.get("errors");
        if (errors != null && errors.isArray() && !errors.isEmpty()) {
            throw new OpenBayesException(formatErrors(errors));
        }

        JsonNode data = root.get("data");
        if (data == null || data.isNull()) {
            throw new OpenBayesException("GraphQL response has no data: " + resp.body());
        }
        return data;
    }

    /** Build the {@code {"query": ..., "variables": ...}} request body. */
    static String buildRequestBody(ObjectMapper mapper, String query, Map<String, Object> variables)
            throws JsonProcessingException {
        ObjectNode body = mapper.createObjectNode();
        body.put("query", query);
        body.set("variables", mapper.valueToTree(variables == null ? Map.of() : variables));
        return mapper.writeValueAsString(body);
    }

    /** Mirror the CLI's error formatting: message plus optional extensions.details. */
    static String formatErrors(JsonNode errors) {
        JsonNode first = errors.get(0);
        String message = first.path("message").asText("Unknown error");
        JsonNode details = first.path("extensions").path("details");
        if (!details.isMissingNode() && !details.isNull() && !details.isEmpty()) {
            return message + ". Details: " + details.toString();
        }
        return message;
    }
}
