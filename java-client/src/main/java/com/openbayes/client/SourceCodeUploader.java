package com.openbayes.client;

import com.openbayes.client.model.OpenBayesException;
import com.openbayes.client.model.SourceCodePolicy;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Uploads every file under a project directory to the bucket/prefix carried by a
 * {@link SourceCodePolicy}, using the temporary credentials it provides.
 *
 * <p><b>Critical:</b> {@link #buildClient} sets {@code requestChecksumCalculation = WHEN_REQUIRED}.
 * Without it, AWS SDK for Java v2 (like botocore &ge; 1.36) auto-adds a CRC32 checksum and degrades
 * the PUT to {@code aws-chunked} streaming, dropping {@code Content-Length} — which MinIO/Ceph
 * backends reject with {@code MissingContentLength} (or a dropped connection behind some proxies).
 */
public final class SourceCodeUploader {

    /**
     * Upload all regular files under {@code projectDir}. Returns the {@code sourceCodeId}
     * ({@code policy.id()}) to hand to {@code createJob}.
     */
    public String upload(SourceCodePolicy policy, Path projectDir) {
        if (!Files.isDirectory(projectDir)) {
            throw new OpenBayesException("project dir not found: " + projectDir);
        }
        String bucket = policy.bucket();
        String prefix = policy.keyPrefix();
        List<Path> files = listFiles(projectDir);
        System.out.printf("共发现 %d 个文件，开始上传到 %s/%s ...%n", files.size(), bucket, prefix);

        try (S3Client s3 = buildClient(policy)) {
            for (Path file : files) {
                String rel = projectDir.relativize(file).toString().replace('\\', '/');
                String key = prefix.isEmpty() ? rel : prefix + "/" + rel;
                try {
                    // RequestBody.fromFile sends a normal PUT with a known Content-Length.
                    s3.putObject(
                            PutObjectRequest.builder().bucket(bucket).key(key).build(),
                            RequestBody.fromFile(file));
                    System.out.println("  上传: " + rel);
                } catch (RuntimeException e) {
                    throw new OpenBayesException("上传失败: " + rel + " - " + e.getMessage(), e);
                }
            }
        }
        return policy.id();
    }

    /** Build an S3 client for the policy's endpoint with the right MinIO-compatible settings. */
    static S3Client buildClient(SourceCodePolicy policy) {
        return S3Client.builder()
                .endpointOverride(URI.create(policy.endpoint()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(policy.accessKey(), policy.secretKey())))
                .region(Region.US_EAST_1)   // placeholder region; MinIO ignores it
                .forcePathStyle(true)       // MinIO requires path-style addressing
                // Keep a normal Content-Length PUT instead of aws-chunked streaming. See class doc.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .build();
    }

    /** Every regular file under {@code dir}, recursively. */
    static List<Path> listFiles(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> files = new ArrayList<>();
            walk.filter(Files::isRegularFile).forEach(files::add);
            return files;
        } catch (IOException e) {
            throw new OpenBayesException("failed to scan dir: " + dir + " - " + e.getMessage(), e);
        }
    }
}
