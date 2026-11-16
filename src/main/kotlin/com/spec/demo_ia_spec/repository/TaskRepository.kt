package com.spec.demo_ia_spec.repository

import com.spec.demo_ia_spec.model.Task

interface TaskRepository {
    fun save(task: Task): Task
    fun findAllOrdered(): List<Task>
    fun findById(id: Long): Task?
    fun deleteById(id: Long): Boolean
}