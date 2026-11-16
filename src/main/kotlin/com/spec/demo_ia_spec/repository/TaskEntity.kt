package com.spec.demo_ia_spec.repository

import com.spec.demo_ia_spec.model.Task
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * JPA mapping of the `tasks` table. Stays inside the repository layer; the rest of the
 * application works with the [Task] domain model.
 */
@Entity
@Table(name = "tasks")
class TaskEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, length = 100)
    var title: String,

    @Column(columnDefinition = "text")
    var description: String?,

    @Column(nullable = false)
    var completed: Boolean,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant,
) {
    fun toDomain(): Task = Task(
        id = id!!,
        title = title,
        description = description,
        completed = completed,
        createdAt = createdAt,
    )

    companion object {
        fun fromDomain(task: Task): TaskEntity = TaskEntity(
            id = task.id.takeIf { it != 0L },
            title = task.title,
            description = task.description,
            completed = task.completed,
            createdAt = task.createdAt,
        )
    }
}
