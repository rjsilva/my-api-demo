package com.spec.demo_ia_spec.service

import com.spec.demo_ia_spec.dto.CreateTaskRequest
import com.spec.demo_ia_spec.exception.TaskNotFoundException
import com.spec.demo_ia_spec.repository.InMemoryTaskRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TaskServiceTest {

    private val repository = InMemoryTaskRepository()
    private val service = TaskService(repository)

    @Test
    fun `createTask with valid title creates pending task`() {
        val created = service.createTask(CreateTaskRequest(title = "Buy milk", description = null))

        assertEquals("Buy milk", created.title)
        assertFalse(created.completed)
    }

    @Test
    fun `createTask with title at 100 characters is accepted`() {
        val title = "a".repeat(100)

        val created = service.createTask(CreateTaskRequest(title = title, description = null))

        assertEquals(title, created.title)
    }

    @Test
    fun `listTasks returns empty list when no tasks exist`() {
        assertTrue(service.listTasks().isEmpty())
    }

    @Test
    fun `listTasks returns pending before completed preserving creation order`() {
        val first = service.createTask(CreateTaskRequest(title = "First", description = null))
        val second = service.createTask(CreateTaskRequest(title = "Second", description = null))
        val third = service.createTask(CreateTaskRequest(title = "Third", description = null))
        repository.save(second.copy(completed = true))

        val result = service.listTasks()

        assertEquals(listOf(first.id, third.id, second.id), result.map { it.id })
    }

    @Test
    fun `completeTask marks a pending task as completed`() {
        val created = service.createTask(CreateTaskRequest(title = "Buy milk", description = null))

        val completed = service.completeTask(created.id)

        assertTrue(completed.completed)
    }

    @Test
    fun `completeTask is idempotent for an already completed task`() {
        val created = service.createTask(CreateTaskRequest(title = "Buy milk", description = null))
        service.completeTask(created.id)

        val completedAgain = service.completeTask(created.id)

        assertTrue(completedAgain.completed)
    }

    @Test
    fun `completeTask throws TaskNotFoundException for unknown id`() {
        assertThrows(TaskNotFoundException::class.java) { service.completeTask(999L) }
    }

    @Test
    fun `deleteTask removes an existing pending task`() {
        val created = service.createTask(CreateTaskRequest(title = "Buy milk", description = null))

        service.deleteTask(created.id)

        assertTrue(service.listTasks().none { it.id == created.id })
    }

    @Test
    fun `deleteTask removes an existing completed task`() {
        val created = service.createTask(CreateTaskRequest(title = "Buy milk", description = null))
        service.completeTask(created.id)

        service.deleteTask(created.id)

        assertTrue(service.listTasks().none { it.id == created.id })
    }

    @Test
    fun `deleteTask throws TaskNotFoundException for unknown id`() {
        assertThrows(TaskNotFoundException::class.java) { service.deleteTask(999L) }
    }
}