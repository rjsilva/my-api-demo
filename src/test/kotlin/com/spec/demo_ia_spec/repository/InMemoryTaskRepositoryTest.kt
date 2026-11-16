package com.spec.demo_ia_spec.repository

import com.spec.demo_ia_spec.model.Task
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class InMemoryTaskRepositoryTest {

    private fun newTask(title: String, completed: Boolean = false, createdAt: Instant = Instant.now()) =
        Task(title = title, description = null, completed = completed, createdAt = createdAt)

    @Test
    fun `save assigns sequential ids`() {
        val repository = InMemoryTaskRepository()

        val first = repository.save(newTask("First"))
        val second = repository.save(newTask("Second"))

        assertEquals(1L, first.id)
        assertEquals(2L, second.id)
    }

    @Test
    fun `findAllOrdered returns pending before completed ordered by createdAt`() {
        val repository = InMemoryTaskRepository()
        val now = Instant.now()

        val oldestCompleted = repository.save(newTask("OldestCompleted", completed = true, createdAt = now.minusSeconds(30)))
        val oldestPending = repository.save(newTask("OldestPending", createdAt = now.minusSeconds(20)))
        val newestPending = repository.save(newTask("NewestPending", createdAt = now.minusSeconds(10)))
        val newestCompleted = repository.save(newTask("NewestCompleted", completed = true, createdAt = now))

        val result = repository.findAllOrdered()

        assertEquals(
            listOf(oldestPending.id, newestPending.id, oldestCompleted.id, newestCompleted.id),
            result.map { it.id },
        )
    }

    @Test
    fun `findById returns null for unknown id`() {
        val repository = InMemoryTaskRepository()

        assertNull(repository.findById(999L))
    }

    @Test
    fun `deleteById removes existing task and returns true, false otherwise`() {
        val repository = InMemoryTaskRepository()
        val saved = repository.save(newTask("ToDelete"))

        assertTrue(repository.deleteById(saved.id))
        assertNull(repository.findById(saved.id))
        assertFalse(repository.deleteById(saved.id))
    }
}