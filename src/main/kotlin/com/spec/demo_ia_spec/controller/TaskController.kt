package com.spec.demo_ia_spec.controller

import com.spec.demo_ia_spec.dto.CreateTaskRequest
import com.spec.demo_ia_spec.dto.TaskResponse
import com.spec.demo_ia_spec.service.TaskService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/tasks")
class TaskController(private val taskService: TaskService) {

    @PostMapping
    fun createTask(@Valid @RequestBody request: CreateTaskRequest): ResponseEntity<TaskResponse> {
        val created = taskService.createTask(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(TaskResponse.from(created))
    }

    @GetMapping
    fun listTasks(): ResponseEntity<List<TaskResponse>> =
        ResponseEntity.ok(taskService.listTasks().map(TaskResponse::from))

    @PatchMapping("/{id}/complete")
    fun completeTask(@PathVariable id: Long): ResponseEntity<TaskResponse> =
        ResponseEntity.ok(TaskResponse.from(taskService.completeTask(id)))

    @DeleteMapping("/{id}")
    fun deleteTask(@PathVariable id: Long): ResponseEntity<Unit> {
        taskService.deleteTask(id)
        return ResponseEntity.noContent().build()
    }
}