package com.openbayes.client.model;

import java.util.Map;

/** Result of {@code createJob}: the job id and its named links (e.g. "frontend"). */
public record JobResult(String id, Map<String, String> links) {

    public String link(String name) {
        return links.get(name);
    }
}
