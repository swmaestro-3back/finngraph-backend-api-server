package com.finngraph.config

import com.zaxxer.hikari.HikariDataSource
import org.jooq.DSLContext
import org.jooq.ExecuteListenerProvider
import org.jooq.conf.Settings
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.support.JdbcTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

@ConfigurationProperties("spring.datasource")
data class EtlDataSourceProperties(
    val url: String,
    val username: String,
    val password: String,
    val maximumPoolSize: Int = 10,
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(EtlDataSourceProperties::class)
class EtlJooqConfig {

    @Bean
    @Primary
    fun etlDataSource(properties: EtlDataSourceProperties): HikariDataSource =
        HikariDataSource().apply {
            jdbcUrl = requireJdbcUrl(properties.url, "spring.datasource.url")
            username = properties.username
            password = properties.password
            maximumPoolSize = properties.maximumPoolSize
            poolName = ETL_POOL_NAME
            isReadOnly = true
            addDataSourceProperty("readOnlyMode", "always")
        }

    @Bean
    fun etlTransactionManager(@Qualifier("etlDataSource") dataSource: DataSource): JdbcTransactionManager =
        JdbcTransactionManager(dataSource)

    @Bean
    @Primary
    fun etlDslContext(
        @Qualifier("etlDataSource") dataSource: DataSource,
        @Qualifier("etlTransactionManager") transactionManager: PlatformTransactionManager,
        executeListenerProviders: ObjectProvider<ExecuteListenerProvider>,
        settings: ObjectProvider<Settings>,
    ): DSLContext = jooqDslContext(dataSource, transactionManager, executeListenerProviders, settings)

    private companion object {
        const val ETL_POOL_NAME = "finngraph-read"
    }
}
