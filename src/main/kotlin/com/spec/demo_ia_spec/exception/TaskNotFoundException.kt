package com.spec.demo_ia_spec.exception

class TaskNotFoundException(id: Long) : RuntimeException("Task not found: $id")