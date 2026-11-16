package com.spec.demo_ia_spec.service

import com.spec.demo_ia_spec.dto.CreateTaskRequest
import com.spec.demo_ia_spec.exception.TaskNotFoundException
import com.spec.demo_ia_spec.model.Task
import com.spec.demo_ia_spec.repository.TaskRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class TaskService(private val taskRepository: TaskRepository) {

    fun createTask(request: CreateTaskRequest): Task {
        val task = Task(
            title = request.title,
            description = request.description,
            completed = false,
            createdAt = Instant.now(),
        )
        return taskRepository.save(task)
    }

    fun listTasks(): List<Task> = taskRepository.findAllOrdered()

    fun completeTask(id: Long): Task {
        val task = taskRepository.findById(id) ?: throw TaskNotFoundException(id)
        if (task.completed) return task
        return taskRepository.save(task.copy(completed = true))
    }

    fun deleteTask(id: Long) {
        taskRepository.findById(id) ?: throw TaskNotFoundException(id)
        taskRepository.deleteById(id)
    }
}