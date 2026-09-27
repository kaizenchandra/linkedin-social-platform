package dev.network.media;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.model.*;

@Component
public class ObjectStore implements AutoCloseable {
    private final S3Client s3;
    private final String bucket;

    public ObjectStore(
            @Value("${S3_ENDPOINT:http://localhost:8333}") String endpoint,
            @Value("${S3_ACCESS_KEY}") String access,
            @Value("${S3_SECRET_KEY}") String secret,
            @Value("${S3_BUCKET:network-media}") String bucket) {
        this.bucket = bucket;
        s3 =
                S3Client.builder()
                        .region(Region.US_EAST_1)
                        .endpointOverride(URI.create(endpoint))
                        .credentialsProvider(
                                StaticCredentialsProvider.create(AwsBasicCredentials.create(access, secret)))
                        .forcePathStyle(true)
                        .serviceConfiguration(S3Configuration.builder().chunkedEncodingEnabled(false).build())
                        .httpClientBuilder(
                                UrlConnectionHttpClient.builder()
                                        .connectionTimeout(Duration.ofSeconds(2))
                                        .socketTimeout(Duration.ofSeconds(10)))
                        .overrideConfiguration(
                                c ->
                                        c.apiCallTimeout(Duration.ofSeconds(15))
                                                .apiCallAttemptTimeout(Duration.ofSeconds(12)))
                        .build();
    }

    public void put(String key, ImageValidator.Image image) {
        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(image.contentType())
                        .checksumAlgorithm(ChecksumAlgorithm.SHA256)
                        .build(),
                RequestBody.fromBytes(image.bytes()));
    }

    public java.io.InputStream get(String key) {
        return s3.getObject(
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .checksumMode(ChecksumMode.ENABLED)
                        .build());
    }

    public void delete(String key) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    public void close() {
        s3.close();
    }
}
