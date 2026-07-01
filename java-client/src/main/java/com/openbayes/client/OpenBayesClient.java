package com.openbayes.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.openbayes.client.model.JobResult;
import com.openbayes.client.model.OpenBayesException;
import com.openbayes.client.model.SourceCodePolicy;
import com.openbayes.client.model.TaskSpec;
import com.openbayes.client.model.WorkspaceTaskSpec;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * High-level OpenBayes operations needed to create a task: login, project creation, source-code
 * upload policy, and job creation. Each method maps to one GraphQL mutation and mirrors what the
 * {@code bayes} CLI sends. The actual file upload lives in {@link SourceCodeUploader}.
 */
public final class OpenBayesClient {

    private final GraphQLClient gql;

    public OpenBayesClient(GraphQLClient gql) {
        this.gql = gql;
    }

    /** Log in with username/password; returns the token and sets it on the underlying client. */
    public String login(String username, String password) {
        String query = """
                mutation Login($username: String!, $password: String!) {
                  login(username: $username, password: $password) { email token username }
                }
                """;
        JsonNode data = gql.exec(query, Map.of("username", username, "password", password));
        JsonNode login = data.get("login");
        if (login == null || login.get("token") == null) {
            throw new OpenBayesException("login failed: unexpected response");
        }
        String token = login.get("token").asText();
        gql.setToken(token);
        return token;
    }

    /** Create a project; returns its id. A project can be reused across many jobs. */
    public String createProject(String userId, String name, String description) {
        String query = """
                mutation CreateProject($userId: String!, $name: String!, $description: String, $tagNames: [TagInput]) {
                  createProject(userId: $userId, name: $name, description: $description, tagNames: $tagNames) {
                    id name links { name value }
                  }
                }
                """;
        Map<String, Object> vars = new HashMap<>();
        vars.put("userId", userId);
        vars.put("name", name);
        vars.put("description", description);
        vars.put("tagNames", List.of());
        JsonNode data = gql.exec(query, vars);
        JsonNode project = data.get("createProject");
        if (project == null || project.get("id") == null) {
            throw new OpenBayesException("createProject failed: unexpected response");
        }
        return project.get("id").asText();
    }

    /** Request temporary S3 upload credentials (TEMPORARY storage type). */
    public SourceCodePolicy createSourceCodePolicy(String userId) {
        String query = """
                mutation CreateSourceCodePolicy($userId: String!, $storageType: StorageType!) {
                  createSourceCodePolicy(userId: $userId, storageType: $storageType) {
                    id endpoint accessKey secretKey path
                  }
                }
                """;
        JsonNode data = gql.exec(query, Map.of("userId", userId, "storageType", "TEMPORARY"));
        JsonNode n = data.get("createSourceCodePolicy");
        if (n == null || n.get("id") == null) {
            throw new OpenBayesException("createSourceCodePolicy failed: unexpected response");
        }
        return new SourceCodePolicy(
                n.get("id").asText(),
                n.get("endpoint").asText(),
                n.get("accessKey").asText(),
                n.get("secretKey").asText(),
                n.get("path").asText());
    }

    /** Create a single-node TASK job from an uploaded source code id (upload mode). */
    public JobResult createTask(String userId, TaskSpec spec) {
        Map<String, Object> newTask = new LinkedHashMap<>();
        newTask.put("code", spec.sourceCodeId());
        newTask.put("command", spec.command());

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("mode", "TASK");
        input.put("projectId", spec.projectId());
        input.put("runtime", spec.runtime());
        input.put("resource", spec.resource());
        input.put("newTask", newTask);
        input.put("tagNames", List.of(Map.of("name", "BUSINESS_CHANNEL_ML")));

        return createJob(userId, input);
    }

    /**
     * Create a TASK that reuses a prepared workspace's output (clone mode, no upload).
     *
     * <p>Binds {@code <userId>/jobs/<workspaceId>/output} at {@code /output} (the clone
     * convention; big models/datasets would instead go to {@code /input0}). The task carries
     * <b>no sourceCode</b> — the prepared code/env comes entirely from the binding.
     */
    public JobResult createTaskFromWorkspace(String userId, WorkspaceTaskSpec spec) {
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("name", userId + "/jobs/" + spec.workspaceId() + "/output");
        binding.put("path", "/output");
        binding.put("bindingAuth", "READ_WRITE");

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("mode", "TASK");
        input.put("projectId", spec.projectId());
        input.put("runtime", spec.runtime());
        input.put("resource", spec.resource());
        input.put("newTask", Map.of("command", spec.command()));  // note: no "code"
        input.put("dataBindings", List.of(binding));
        input.put("tagNames", List.of(Map.of("name", "BUSINESS_CHANNEL_ML")));

        return createJob(userId, input);
    }

    /** Send the CreateJob mutation with a prebuilt input and parse the result. */
    private JobResult createJob(String userId, Map<String, Object> input) {
        String query = """
                mutation CreateJob($userId: String!, $input: CreateJobInput) {
                  createJob(userId: $userId, input: $input) {
                    id links { name value }
                  }
                }
                """;
        JsonNode data = gql.exec(query, Map.of("userId", userId, "input", input));
        JsonNode job = data.get("createJob");
        if (job == null || job.get("id") == null) {
            throw new OpenBayesException("createJob failed: unexpected response");
        }

        Map<String, String> links = new LinkedHashMap<>();
        JsonNode linksNode = job.get("links");
        if (linksNode != null && linksNode.isArray()) {
            for (JsonNode l : linksNode) {
                links.put(l.path("name").asText(), l.path("value").asText());
            }
        }
        return new JobResult(job.get("id").asText(), links);
    }
}
