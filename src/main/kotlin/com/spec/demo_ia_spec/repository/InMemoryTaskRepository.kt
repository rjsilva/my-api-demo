package com.spec.demo_ia_spec.repository

import com.spec.demo_ia_spec.model.Task
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Repository
class InMemoryTaskRepository : TaskRepository {

    private val tasks = ConcurrentHashMap<Long, Task>()
    private val idGenerator = AtomicLong(0)

    override fun save(task: Task): Task {
        val saved = if (task.id == 0L) task.copy(id = idGenerator.incrementAndGet()) else task
        tasks[saved.id] = saved
        return saved
    }

    override fun findAllOrdered(): List<Task> =
        tasks.values.sortedWith(compareBy({ it.completed }, { it.createdAt }))

    override fun findById(id: Long): Task? = tasks[id]

    override fun deleteById(id: Long): Boolean = tasks.remove(id) != null
}