package com.spec.demo_ia_spec

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))

    companion object {
        // Must match the image used in compose.yaml (FR-016).
        const val POSTGRES_IMAGE = "postgres:18"

        // Must match the lgtm image used in compose.yaml.
        const val LGTM_IMAGE = "grafana/otel-lgtm:0.40.0"
    }
}
