package com.spec.demo_ia_spec.integration

import com.spec.demo_ia_spec.TestcontainersConfiguration
import com.spec.demo_ia_spec.model.Task
import com.spec.demo_ia_spec.repository.TaskRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/** SC-005: every operation answers in under 1 second with 1,000 stored tasks. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration::class)
class ListingPerformanceTest {

    @Value("\${local.server.port}")
    private var port: Int = 0

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var repository: TaskRepository

    private val api: RestClient by lazy { RestClient.create("http://localhost:$port") }

    private val limit = Duration.ofSeconds(1)

    @BeforeEach
    fun seed() {
        jdbcTemplate.execute("TRUNCATE TABLE tasks")
        val base = Instant.now().truncatedTo(ChronoUnit.MICROS)
        repeat(1_000) { i ->
            repository.save(
                Task(title = "Task $i", description = null, completed = i % 2 == 0, createdAt = base.plusMillis(i.toLong())),
            )
        }
    }

    private fun <T> timed(block: () -> T): Pair<T, Duration> {
        val start = System.nanoTime()
        val result = block()
        return result to Duration.ofNanos(System.nanoTime() - start)
    }

    private fun call(method: HttpMethod, path: String, body: String? = null): Int {
        val request = api.method(method).uri(path)
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).body(body)
        return request.exchange { _, response -> response.statusCode.value() }!!
    }

    @Test
    fun `all operations answer in under one second with 1000 tasks`() {
        api.get().uri("/api/tasks").retrieve().body(String::class.java) // warm-up

        val (listing, listTime) = timed { api.get().uri("/api/tasks").retrieve().body(String::class.java)!! }
        val tasks = JsonMapper().readTree(listing)
        assertEquals(1_000, tasks.size())
        val completedFlags = (0 until tasks.size()).map { tasks[it]["completed"].asBoolean() }
        assertEquals(completedFlags.sorted(), completedFlags, "pending tasks must come before completed ones")
        assertTrue(listTime < limit, "list took $listTime")

        val (createStatus, createTime) = timed { call(HttpMethod.POST, "/api/tasks", """{"title":"New"}""") }
        assertEquals(201, createStatus)
        assertTrue(createTime < limit, "create took $createTime")

        val pendingId = tasks[0]["id"].asLong()
        val (completeStatus, completeTime) = timed { call(HttpMethod.PATCH, "/api/tasks/$pendingId/complete") }
        assertEquals(200, completeStatus)
        assertTrue(completeTime < limit, "complete took $completeTime")

        val (deleteStatus, deleteTime) = timed { call(HttpMethod.DELETE, "/api/tasks/$pendingId") }
        assertEquals(204, deleteStatus)
        assertTrue(deleteTime < limit, "delete took $deleteTime")
    }
}
