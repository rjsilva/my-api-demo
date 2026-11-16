package com.spec.demo_ia_spec.repository

import com.spec.demo_ia_spec.TestcontainersConfiguration
import com.spec.demo_ia_spec.model.Task
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager
import org.springframework.context.annotation.Import
import java.time.Instant
import java.time.temporal.ChronoUnit

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration::class, JpaTaskRepository::class)
class JpaTaskRepositoryTest {

    @Autowired
    private lateinit var repository: TaskRepository

    @Autowired
    private lateinit var testEntityManager: TestEntityManager

    private val baseTime: Instant = Instant.parse("2026-11-15T10:00:00.123456Z")

    private fun newTask(title: String, createdAt: Instant = baseTime, description: String? = null, completed: Boolean = false) =
        Task(title = title, description = description, completed = completed, createdAt = createdAt)

    // Forces writes to the database and drops the persistence context, so the next read hits PostgreSQL.
    private fun flushAndClear() {
        testEntityManager.flush()
        testEntityManager.clear()
    }

    @Test
    fun `save assigns an id and the task round-trips through the database`() {
        val createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS)
        val saved = repository.save(newTask("Buy milk", createdAt = createdAt, description = "2 litres"))
        flushAndClear()

        assertTrue(saved.id > 0)
        val loaded = repository.findById(saved.id)
        assertEquals(Task(id = saved.id, title = "Buy milk", description = "2 litres", completed = false, createdAt = createdAt), loaded)
    }

    @Test
    fun `findAllOrdered returns pending before completed, each group by creation time`() {
        val pendingLate = repository.save(newTask("pending late", createdAt = baseTime.plusSeconds(20)))
        val completedEarly = repository.save(newTask("completed early", createdAt = baseTime, completed = true))
        val pendingEarly = repository.save(newTask("pending early", createdAt = baseTime.plusSeconds(10)))
        val completedLate = repository.save(newTask("completed late", createdAt = baseTime.plusSeconds(30), completed = true))
        flushAndClear()

        val ids = repository.findAllOrdered().map { it.id }

        assertEquals(listOf(pendingEarly.id, pendingLate.id, completedEarly.id, completedLate.id), ids)
    }

    @Test
    fun `tasks with the same createdAt are ordered by id`() {
        val first = repository.save(newTask("first"))
        val second = repository.save(newTask("second"))
        val third = repository.save(newTask("third"))
        flushAndClear()

        assertEquals(listOf(first.id, second.id, third.id), repository.findAllOrdered().map { it.id })
    }

    @Test
    fun `findById returns null for an unknown id`() {
        assertNull(repository.findById(Long.MAX_VALUE))
    }

    @Test
    fun `deleteById removes an existing task and reports whether something was deleted`() {
        val saved = repository.save(newTask("to delete"))
        flushAndClear()

        assertTrue(repository.deleteById(saved.id))
        flushAndClear()

        assertNull(repository.findById(saved.id))
        assertFalse(repository.deleteById(saved.id))
    }

    @Test
    fun `ids of deleted tasks are never reused`() {
        repository.save(newTask("first"))
        val highest = repository.save(newTask("second"))
        flushAndClear()
        repository.deleteById(highest.id)
        flushAndClear()

        val next = repository.save(newTask("third"))

        assertTrue(next.id > highest.id, "expected id greater than ${highest.id}, got ${next.id}")
    }

    @Test
    fun `saving an existing task persists the completed status`() {
        val saved = repository.save(newTask("to complete"))
        flushAndClear()

        repository.save(saved.copy(completed = true))
        flushAndClear()

        assertEquals(true, repository.findById(saved.id)?.completed)
    }

    @Test
    fun `long descriptions with accents and emoji are stored unchanged`() {
        val description = "Ação ✅ ünïcødé ".repeat(700).take(10_000)
        val saved = repository.save(newTask("unicode", description = description))
        flushAndClear()

        assertEquals(description, repository.findById(saved.id)?.description)
    }
}
