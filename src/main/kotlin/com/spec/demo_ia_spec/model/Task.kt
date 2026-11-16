package com.spec.demo_ia_spec.model

import java.time.Instant

data class Task(
    val id: Long = 0,
    val title: String,
    val description: String?,
    val completed: Boolean = false,
    val createdAt: Instant,
)