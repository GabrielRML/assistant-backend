package com.assistent.api.storage;

import com.assistent.api.config.StorageProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PostPolicy;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class MinioStorageService implements StorageService {

    private static final Duration MAX_DURATION_SECONDS_GUARD = Duration.ofDays(7);

    private final MinioClient internalClient;
    private final MinioClient publicClient;
    private final StorageProperties properties;

    public MinioStorageService(@Qualifier("internalMinioClient") MinioClient internalClient,
            @Qualifier("publicMinioClient") MinioClient publicClient, StorageProperties properties) {
        this.internalClient = internalClient;
        this.publicClient = publicClient;
        this.properties = properties;
    }

    @Override
    public PresignedUpload createUpload(String key, String contentType, long maxSizeBytes, Duration expiration) {
        try {
            Instant expiresAt = Instant.now().plus(clamp(expiration));
            PostPolicy policy = new PostPolicy(properties.bucket(), ZonedDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
            policy.addEqualsCondition("key", key);
            policy.addEqualsCondition("Content-Type", contentType);
            policy.addContentLengthRangeCondition(1, maxSizeBytes);
            Map<String, String> fields = new LinkedHashMap<>(publicClient.getPresignedPostFormData(policy));
            // Campos exigidos pela policy: o navegador deve enviá-los com exatamente estes valores.
            fields.put("key", key);
            fields.put("Content-Type", contentType);
            String url = trimTrailingSlash(properties.publicEndpoint()) + "/" + properties.bucket();
            return new PresignedUpload(url, fields, expiresAt);
        } catch (Exception e) {
            throw new StorageException("Failed to create presigned upload for " + key, e);
        }
    }

    @Override
    public PresignedDownload createDownload(String key, String downloadFileName, Duration expiration) {
        try {
            Duration safe = clamp(expiration);
            String disposition = "attachment; filename=\"" + downloadFileName.replace("\"", "") + "\"";
            String url = publicClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(properties.bucket())
                    .object(key)
                    .expiry((int) safe.toSeconds())
                    .extraQueryParams(Map.of("response-content-disposition", disposition))
                    .build());
            return new PresignedDownload(url, Instant.now().plus(safe));
        } catch (Exception e) {
            throw new StorageException("Failed to create presigned download for " + key, e);
        }
    }

    @Override
    public Optional<StoredObject> stat(String key) {
        try {
            StatObjectResponse stat = internalClient.statObject(
                    StatObjectArgs.builder().bucket(properties.bucket()).object(key).build());
            return Optional.of(new StoredObject(stat.size(), stat.contentType()));
        } catch (ErrorResponseException e) {
            String code = e.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code) || "NoSuchBucket".equals(code)) {
                return Optional.empty();
            }
            throw new StorageException("Failed to stat " + key, e);
        } catch (Exception e) {
            throw new StorageException("Failed to stat " + key, e);
        }
    }

    @Override
    public void put(String key, InputStream content, long size, String contentType) {
        try {
            internalClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(key)
                    .stream(content, size, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new StorageException("Failed to put " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            internalClient.removeObject(RemoveObjectArgs.builder().bucket(properties.bucket()).object(key).build());
        } catch (Exception e) {
            throw new StorageException("Failed to delete " + key, e);
        }
    }

    private Duration clamp(Duration duration) {
        return duration.compareTo(MAX_DURATION_SECONDS_GUARD) > 0 ? MAX_DURATION_SECONDS_GUARD : duration;
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
