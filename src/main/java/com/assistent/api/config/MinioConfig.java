package com.assistent.api.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class MinioConfig {

    /** Cliente usado pelo Spring (rede interna). */
    @Bean
    @Qualifier("internalMinioClient")
    MinioClient internalMinioClient(StorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    /** Cliente usado apenas para assinar URLs entregues ao navegador. A região fixa evita chamadas de rede ao assinar. */
    @Bean
    @Qualifier("publicMinioClient")
    MinioClient publicMinioClient(StorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.publicEndpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .region("us-east-1")
                .build();
    }

    @Bean
    ApplicationRunner minioBucketInitializer(@Qualifier("internalMinioClient") MinioClient client,
            StorageProperties properties) {
        return args -> {
            if (!properties.createBucketOnStartup()) {
                return;
            }
            try {
                boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(properties.bucket()).build());
                if (!exists) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(properties.bucket()).build());
                }
            } catch (Exception e) {
                log.warn("Could not verify or create bucket '{}': {}", properties.bucket(), e.getMessage());
            }
        };
    }
}
