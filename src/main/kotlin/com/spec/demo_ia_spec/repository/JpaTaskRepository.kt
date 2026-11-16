package com.spec.demo_ia_spec.repository

import com.spec.demo_ia_spec.model.Task
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional
class JpaTaskRepository(private val jpa: TaskJpaRepository) : TaskRepository {

    override fun save(task: Task): Task = jpa.save(TaskEntity.fromDomain(task)).toDomain()

    @Transactional(readOnly = true)
    override fun findAllOrdered(): List<Task> =
        jpa.findAllByOrderByCompletedAscCreatedAtAscIdAsc().map { it.toDomain() }

    @Transactional(readOnly = true)
    override fun findById(id: Long): Task? = jpa.findById(id).orElse(null)?.toDomain()

    override fun deleteById(id: Long): Boolean = jpa.deleteTaskById(id) > 0
}
