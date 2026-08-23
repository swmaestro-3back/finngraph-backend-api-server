package com.finngraph.config

import org.jooq.ExecuteListenerProvider
import org.jooq.impl.DefaultExecuteListenerProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order

@Configuration(proxyBeanMethods = false)
class JooqConfig {
    @Bean
    @Order(100)
    fun queryCountExecuteListenerProvider(): ExecuteListenerProvider =
        DefaultExecuteListenerProvider(QueryCountListener())
}
