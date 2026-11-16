package com.spec.demo_ia_spec.service

import com.spec.demo_ia_spec.dto.CreateTaskRequest
import com.spec.demo_ia_spec.exception.TaskNotFoundException
import com.spec.demo_ia_spec.model.Task
import com.spec.demo_ia_spec.repository.TaskRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.RETURNS_DEFAULTS
import org.mockito.Mockito.`when`
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.stubbing.Answer
import java.time.Instant

class TaskServiceTest {

    // Records every task passed to save() and echoes it back, assigning an id to new tasks.
    // Avoids Mockito's any()/captor, which return null for non-nullable Kotlin parameters.
    private val savedTasks = mutableListOf<Task>()
    private val repository: TaskRepository = mock(TaskRepository::class.java, Answer { invocation ->
        if (invocation.method.name == "save") {
            val task = invocation.arguments[0] as Task
            savedTasks += task
            if (task.id == 0L) task.copy(id = savedTasks.size.toLong()) else task
        } else {
            RETURNS_DEFAULTS.answer(invocation)
        }
    })
    private val service = TaskService(repository)

    private fun task(id: Long, completed: Boolean = false) =
        Task(id = id, title = "Task $id", description = null, completed = completed, createdAt = Instant.parse("2026-11-15T10:00:00Z"))

    @Test
    fun `createTask with valid title creates pending task`() {
        val created = service.createTask(CreateTaskRequest(title = "Buy milk", description = null))

        assertEquals("Buy milk", created.title)
        assertFalse(created.completed)
        assertEquals(1, savedTasks.size)
    }

    @Test
    fun `createTask with title at 100 characters is accepted`() {
        val title = "a".repeat(100)

        val created = service.createTask(CreateTaskRequest(title = title, description = null))

        assertEquals(title, created.title)
    }

    @Test
    fun `createTask truncates createdAt to microseconds`() {
        val created = service.createTask(CreateTaskRequest(title = "Buy milk", description = null))

        assertEquals(0, created.createdAt.nano % 1_000)
    }

    @Test
    fun `listTasks delegates to findAllOrdered`() {
        val ordered = listOf(task(1), task(3), task(2, completed = true))
        `when`(repository.findAllOrdered()).thenReturn(ordered)

        assertSame(ordered, service.listTasks())
    }

    @Test
    fun `completeTask marks a pending task as completed`() {
        `when`(repository.findById(1L)).thenReturn(task(1))

        val completed = service.completeTask(1L)

        assertTrue(completed.completed)
        assertEquals(listOf(task(1, completed = true)), savedTasks)
    }

    @Test
    fun `completeTask is idempotent for an already completed task`() {
        `when`(repository.findById(1L)).thenReturn(task(1, completed = true))

        val completedAgain = service.completeTask(1L)

        assertTrue(completedAgain.completed)
        assertTrue(savedTasks.isEmpty())
    }

    @Test
    fun `completeTask throws TaskNotFoundException for unknown id`() {
        assertThrows(TaskNotFoundException::class.java) { service.completeTask(999L) }
    }

    @Test
    fun `deleteTask deletes with a single repository call and no prior lookup`() {
        `when`(repository.deleteById(5L)).thenReturn(true)

        service.deleteTask(5L)

        verify(repository, times(1)).deleteById(5L)
        verify(repository, never()).findById(anyLong())
    }

    @Test
    fun `deleteTask throws TaskNotFoundException when nothing was deleted`() {
        `when`(repository.deleteById(999L)).thenReturn(false)

        assertThrows(TaskNotFoundException::class.java) { service.deleteTask(999L) }
    }
}
