package com.spec.demo_ia_spec.integration

import com.spec.demo_ia_spec.DemoIaSpecApplication
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.boot.builder.SpringApplicationBuilder
import java.net.ConnectException
import java.time.Duration

/**
 * FR-010: when the database is unreachable at startup, the application keeps trying for up to
 * 30 seconds and then aborts with a clear connection error.
 */
@Tag("slow")
class StartupWithoutDatabaseTest {

    @Test
    fun `startup waits about 30 seconds for the database and then fails with a connection error`() {
        val start = System.nanoTime()

        val failure = assertThrows(Throwable::class.java) {
            // Port 1 guarantees "connection refused".
            SpringApplicationBuilder(DemoIaSpecApplication::class.java).run(
                "--spring.datasource.url=jdbc:postgresql://localhost:1/tasks",
                "--server.port=0",
                "--spring.docker.compose.enabled=false",
            ).close()
        }

        val elapsed = Duration.ofNanos(System.nanoTime() - start)
        assertTrue(elapsed >= Duration.ofSeconds(25) && elapsed <= Duration.ofSeconds(60), "startup gave up after $elapsed")

        val causes = generateSequence(failure) { it.cause }.toList()
        assertTrue(
            causes.any { it is ConnectException || it.javaClass.simpleName == "PoolInitializationException" },
            "expected a database connection failure in the cause chain, got: ${causes.map { it.javaClass.name }}",
        )
        assertTrue(
            causes.any { it.message?.contains("refused", ignoreCase = true) == true },
            "expected a message mentioning the refused connection",
        )
    }
}
