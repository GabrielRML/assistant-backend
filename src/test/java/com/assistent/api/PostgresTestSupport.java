package com.assistent.api;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Container único (compartilhado entre as classes de teste) usando a imagem com pgvector. */
public final class PostgresTestSupport {

    public static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("pgvector/pgvector:pg17").asCompatibleSubstituteFor("postgres"));

    static {
        POSTGRES.start();
    }

    private PostgresTestSupport() {
    }
}
