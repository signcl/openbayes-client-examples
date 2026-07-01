package com.openbayes.client.model;

/**
 * Temporary upload credentials returned by the {@code createSourceCodePolicy} mutation.
 *
 * <p>{@code path} is {@code <bucket>/<keyPrefix>}, e.g. {@code demo-user/codes/WFkMTSaBui2}.
 * Note there is no session token — {@code accessKey}/{@code secretKey} is a plain pair used for
 * standard SigV4 signing of the S3 PUT requests.
 *
 * <p>{@code id} is the {@code sourceCodeId} to pass to {@code createJob} as {@code newTask.code}.
 */
public record SourceCodePolicy(
        String id,
        String endpoint,
        String accessKey,
        String secretKey,
        String path) {

    /** Bucket = the first path segment. */
    public String bucket() {
        return splitPath(path)[0];
    }

    /** Key prefix = everything after the first '/', or "" if there is none. */
    public String keyPrefix() {
        return splitPath(path)[1];
    }

    /** Split "bucket/a/b/c" (with optional leading slash) into ["bucket", "a/b/c"]. */
    public static String[] splitPath(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        int i = p.indexOf('/');
        if (i < 0) {
            return new String[] {p, ""};
        }
        return new String[] {p.substring(0, i), p.substring(i + 1)};
    }
}
