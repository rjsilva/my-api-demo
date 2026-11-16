package com.spec.demo_ia_spec.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateTaskRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val title: String,
    val description: String?,
)