package com.openbayes.client.model;

/**
 * Inputs to create a TASK that reuses a prepared workspace (clone mode, no upload).
 *
 * <p>The task carries no sourceCode; it binds {@code <party>/jobs/<workspaceId>/output} so the
 * code/env prepared in that workspace's {@code /output} is available in the container.
 */
public record WorkspaceTaskSpec(
        String projectId,
        String workspaceId,
        String runtime,
        String resource,
        String command) {
}
