package com.finngraph

import com.zaxxer.hikari.HikariDataSource
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

@SpringBootTest
class BackendServerApplicationTests {

    @Autowired
    lateinit var primaryDslContext: DSLContext

    @Autowired
    @Qualifier("etlDslContext")
    lateinit var etlDslContext: DSLContext

    @Autowired
    @Qualifier("appDslContext")
    lateinit var appDslContext: DSLContext

    @Autowired
    @Qualifier("etlDataSource")
    lateinit var etlDataSource: HikariDataSource

    @Autowired
    @Qualifier("appDataSource")
    lateinit var appDataSource: HikariDataSource

    @Test
    fun contextLoads() {
    }

    @Test
    fun `ETL데이터는 ETL파이프라인의 데이터소스`() {
        assertSame(etlDslContext, primaryDslContext)
        assertTrue(etlDataSource.isReadOnly)
        assertEquals(etlPostgres.jdbcUrl, etlDataSource.jdbcUrl)
    }

    @Test
    fun `write작업이 요구되는 데이터는 app데이터소스를 문다`() {
        assertNotSame(etlDslContext, appDslContext)
        assertFalse(appDataSource.isReadOnly)
        assertNotEquals(etlDataSource.jdbcUrl, appDataSource.jdbcUrl)
        assertEquals(appPostgres.jdbcUrl, appDataSource.jdbcUrl)
    }

    @Test
    fun `app스키마는 app데이터소스에서 보임`() {
        assertEquals(0, appDslContext.fetchCount(DSL.table("users")))
        assertFailsWith<DataAccessException> { etlDslContext.fetchCount(DSL.table("users")) }
    }

    companion object {
        @JvmStatic
        val etlPostgres = PostgreSQLContainer("postgres:17").apply { start() }

        @JvmStatic
        val appPostgres = PostgreSQLContainer("postgres:17").apply {
            start()
            applyAppSchema(this)
        }

        private fun applyAppSchema(container: PostgreSQLContainer) {
            val schema = Files.readString(Path.of(System.getProperty("finngraph.app.schema.sql")))
            DriverManager.getConnection(container.jdbcUrl, container.username, container.password).use { connection ->
                connection.createStatement().use { it.execute(schema) }
            }
        }

        @DynamicPropertySource
        @JvmStatic
        fun dataSources(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { etlPostgres.jdbcUrl }
            registry.add("spring.datasource.username") { etlPostgres.username }
            registry.add("spring.datasource.password") { etlPostgres.password }
            registry.add("app.datasource.url") { appPostgres.jdbcUrl }
            registry.add("app.datasource.username") { appPostgres.username }
            registry.add("app.datasource.password") { appPostgres.password }
        }
    }
}
