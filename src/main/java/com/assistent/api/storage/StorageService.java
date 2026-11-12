package com.assistent.api.storage;

import java.io.InputStream;
import java.time.Duration;
import java.util.Optional;

public interface StorageService {

    PresignedUpload createUpload(String key, String contentType, long maxSizeBytes, Duration expiration);

    PresignedDownload createDownload(String key, String downloadFileName, Duration expiration);

    Optional<StoredObject> stat(String key);

    void put(String key, InputStream content, long size, String contentType);

    void delete(String key);
}
