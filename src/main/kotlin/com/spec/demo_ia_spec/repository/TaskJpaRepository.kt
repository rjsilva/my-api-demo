package com.spec.demo_ia_spec.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface TaskJpaRepository : JpaRepository<TaskEntity, Long> {

    fun findAllByOrderByCompletedAscCreatedAtAscIdAsc(): List<TaskEntity>

    // Single-statement delete; clearing the persistence context keeps it from serving the deleted entity.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TaskEntity t where t.id = :id")
    fun deleteTaskById(@Param("id") id: Long): Int
}
