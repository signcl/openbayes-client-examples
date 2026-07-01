package com.openbayes.client.model;

/** Inputs needed to create a single-node TASK job. */
public record TaskSpec(
        String projectId,
        String runtime,
        String resource,
        String sourceCodeId,
        String command) {
}
