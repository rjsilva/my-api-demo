package com.spec.demo_ia_spec.dto

import com.spec.demo_ia_spec.model.Task
import java.time.Instant

data class TaskResponse(
    val id: Long,
    val title: String,
    val description: String?,
    val completed: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun from(task: Task) = TaskResponse(
            id = task.id,
            title = task.title,
            description = task.description,
            completed = task.completed,
            createdAt = task.createdAt,
        )
    }
}