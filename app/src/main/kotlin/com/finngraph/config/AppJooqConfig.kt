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
import org.springframework.jdbc.support.JdbcTransactionManager
import org.springframework.transaction.PlatformTransactionManager
import javax.sql.DataSource

@ConfigurationProperties("app.datasource")
data class AppDataSourceProperties(
    val url: String,
    val username: String,
    val password: String,
    val maximumPoolSize: Int = 5,
)

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppDataSourceProperties::class)
class AppJooqConfig {

    @Bean
    fun appDataSource(properties: AppDataSourceProperties): HikariDataSource =
        HikariDataSource().apply {
            jdbcUrl = requireJdbcUrl(properties.url, "app.datasource.url")
            username = properties.username
            password = properties.password
            maximumPoolSize = properties.maximumPoolSize
            poolName = APP_POOL_NAME
        }

    @Bean
    fun appTransactionManager(@Qualifier("appDataSource") dataSource: DataSource): JdbcTransactionManager =
        JdbcTransactionManager(dataSource)

    @Bean
    fun appDslContext(
        @Qualifier("appDataSource") dataSource: DataSource,
        @Qualifier("appTransactionManager") transactionManager: PlatformTransactionManager,
        executeListenerProviders: ObjectProvider<ExecuteListenerProvider>,
        settings: ObjectProvider<Settings>,
    ): DSLContext = jooqDslContext(dataSource, transactionManager, executeListenerProviders, settings)

    private companion object {
        const val APP_POOL_NAME = "finngraph-app"
    }
}
