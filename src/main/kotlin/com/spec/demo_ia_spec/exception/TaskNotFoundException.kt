package com.spec.demo_ia_spec.exception

class TaskNotFoundException(val id: Long) : RuntimeException("Task not found: $id")
