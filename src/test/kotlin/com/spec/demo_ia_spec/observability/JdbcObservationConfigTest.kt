package com.spec.demo_ia_spec.observability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy
import javax.sql.DataSource

class JdbcObservationConfigTest {

    // A DataSource that fails on any call: resolving the name must never open a connection,
    // otherwise an unreachable database doubles the startup wait (feature 002, FR-010).
    private val untouchableDataSource = Proxy.newProxyInstance(
        javaClass.classLoader,
        arrayOf(DataSource::class.java),
    ) { _, method, _ -> throw AssertionError("DataSource.${method.name} must not be called") } as DataSource

    @Test
    fun `data source name resolver uses the bean name without connecting`() {
        val resolver = JdbcObservationConfig().dataSourceNameResolver()

        assertEquals("dataSource", resolver.resolve("dataSource", untouchableDataSource))
    }
}
