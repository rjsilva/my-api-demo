package com.spec.demo_ia_spec.controller

import tools.jackson.databind.ObjectMapper
import com.spec.demo_ia_spec.dto.CreateTaskRequest
import com.spec.demo_ia_spec.exception.TaskNotFoundException
import com.spec.demo_ia_spec.model.Task
import com.spec.demo_ia_spec.service.TaskService
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.CannotCreateTransactionException
import java.time.Instant

@WebMvcTest(TaskController::class)
class TaskControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var taskService: TaskService

    private val objectMapper = ObjectMapper()

    @Test
    fun `POST tasks with valid title returns 201`() {
        `when`(taskService.createTask(CreateTaskRequest(title = "Buy milk", description = null))).thenReturn(
            Task(id = 1, title = "Buy milk", description = null, completed = false, createdAt = Instant.now()),
        )

        mockMvc.perform(
            post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("title" to "Buy milk"))),
        ).andExpect(status().isCreated)
    }

    @Test
    fun `POST tasks with blank title returns 400`() {
        mockMvc.perform(
            post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("title" to ""))),
        ).andExpect(status().isBadRequest)
    }

    @Test
    fun `GET tasks returns 200 with ordered list`() {
        val now = Instant.now()
        `when`(taskService.listTasks()).thenReturn(
            listOf(
                Task(id = 1, title = "First", description = null, completed = false, createdAt = now),
                Task(id = 2, title = "Second", description = null, completed = true, createdAt = now),
            ),
        )

        mockMvc.perform(get("/api/tasks"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(1))
    }

    @Test
    fun `GET tasks returns 200 with empty array when no tasks exist`() {
        `when`(taskService.listTasks()).thenReturn(emptyList())

        mockMvc.perform(get("/api/tasks"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `PATCH tasks id complete returns 200 with completed task`() {
        `when`(taskService.completeTask(1L)).thenReturn(
            Task(id = 1, title = "Buy milk", description = null, completed = true, createdAt = Instant.now()),
        )

        mockMvc.perform(patch("/api/tasks/1/complete"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.completed").value(true))
    }

    @Test
    fun `PATCH tasks id complete returns 404 for unknown id`() {
        `when`(taskService.completeTask(999L)).thenThrow(TaskNotFoundException(999L))

        mockMvc.perform(patch("/api/tasks/999/complete"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE tasks id returns 204`() {
        mockMvc.perform(delete("/api/tasks/1"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `DELETE tasks id returns 404 for unknown id`() {
        `when`(taskService.deleteTask(999L)).thenThrow(TaskNotFoundException(999L))

        mockMvc.perform(delete("/api/tasks/999"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `GET tasks returns 503 with generic message when storage is unavailable`() {
        `when`(taskService.listTasks()).thenThrow(CannotCreateTransactionException("db down"))

        mockMvc.perform(get("/api/tasks"))
            .andExpect(status().isServiceUnavailable)
            .andExpect(jsonPath("$.message").value("Storage temporarily unavailable. Please try again later."))
    }

    @Test
    fun `POST tasks returns 503 when storage is unavailable`() {
        `when`(taskService.createTask(CreateTaskRequest(title = "Buy milk", description = null)))
            .thenThrow(DataAccessResourceFailureException("db down"))

        mockMvc.perform(
            post("/api/tasks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("title" to "Buy milk"))),
        ).andExpect(status().isServiceUnavailable)
    }

    @Test
    fun `data errors that are not storage unavailability do not map to 503`() {
        `when`(taskService.listTasks()).thenThrow(DataIntegrityViolationException("x"))

        // Unhandled exceptions are rethrown by MockMvc; either way the status must not be 503.
        val status = runCatching { mockMvc.perform(get("/api/tasks")).andReturn().response.status }.getOrDefault(500)

        assertNotEquals(503, status)
    }
}
