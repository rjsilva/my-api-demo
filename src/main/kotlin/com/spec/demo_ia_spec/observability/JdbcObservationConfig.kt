package com.spec.demo_ia_spec.observability

import net.ttddyy.observation.boot.autoconfigure.DataSourceNameResolver
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class JdbcObservationConfig {

    // The library default opens a connection to read the catalog name. With the database down,
    // that attempt blocks for the whole Hikari initialization timeout before startup even
    // reaches Flyway, doubling the startup wait. The bean name is enough to label JDBC spans.
    @Bean
    fun dataSourceNameResolver(): DataSourceNameResolver = DataSourceNameResolver { beanName, _ -> beanName }
}
