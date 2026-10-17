package com.finngraph

import com.finngraph.support.TestContainers
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
        assertEquals(TestContainers.etlPostgres.jdbcUrl, etlDataSource.jdbcUrl)
    }

    @Test
    fun `write작업이 요구되는 데이터는 app데이터소스를 문다`() {
        assertNotSame(etlDslContext, appDslContext)
        assertFalse(appDataSource.isReadOnly)
        assertNotEquals(etlDataSource.jdbcUrl, appDataSource.jdbcUrl)
        assertEquals(TestContainers.appPostgres.jdbcUrl, appDataSource.jdbcUrl)
    }

    @Test
    fun `app스키마는 app데이터소스에서 보임`() {
        appDslContext.fetchCount(DSL.table("users"))
        assertFailsWith<DataAccessException> { etlDslContext.fetchCount(DSL.table("users")) }
    }

    @Test
    fun `ETL데이터소스는 autocommit 경로에서도 write작업을 거부한다`() {
        val thrown = assertFailsWith<RuntimeException> {
            etlDslContext.execute("insert into themes (name) values ('readonly-probe')")
        }
        assertTrue(
            generateSequence<Throwable>(thrown) { it.cause }
                .any { it.message?.contains("read-only") == true },
            thrown.stackTraceToString(),
        )
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
