package com.finngraph.support

import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.Base64

class RedisContainer(image: String) : GenericContainer<RedisContainer>(image)

object TestContainers {

    const val REDIS_PORT = 6379

    private val MIGRATION_FILE = Regex("V\\d+__.*\\.sql")

    val etlPostgres: PostgreSQLContainer = PostgreSQLContainer("pgvector/pgvector:pg17").apply {
        start()
        applySchema(this, "finngraph.etl.schema.sql")
    }

    val appPostgres: PostgreSQLContainer = PostgreSQLContainer("postgres:17").apply {
        start()
        applyMigrations(this, "finngraph.app.migrations.dir")
    }

    val redis: RedisContainer = RedisContainer("redis:7-alpine").apply {
        withExposedPorts(REDIS_PORT)
        start()
    }

    val jwtSecret: String = Base64.getEncoder().encodeToString(ByteArray(32) { (it * 7 + 13).toByte() })

    fun register(registry: DynamicPropertyRegistry) {
        registry.add("spring.datasource.url") { etlPostgres.jdbcUrl }
        registry.add("spring.datasource.username") { etlPostgres.username }
        registry.add("spring.datasource.password") { etlPostgres.password }
        registry.add("app.datasource.url") { appPostgres.jdbcUrl }
        registry.add("app.datasource.username") { appPostgres.username }
        registry.add("app.datasource.password") { appPostgres.password }
        registry.add("spring.data.redis.host") { redis.host }
        registry.add("spring.data.redis.port") { redis.getMappedPort(REDIS_PORT) }
        registry.add("spring.data.redis.timeout") { REDIS_TIMEOUT }
        registry.add("app.jwt.secret") { jwtSecret }
    }

    private fun applySchema(container: PostgreSQLContainer, schemaProperty: String) {
        val schema = Files.readString(Path.of(System.getProperty(schemaProperty)))
        DriverManager.getConnection(container.jdbcUrl, container.username, container.password)
            .use { connection -> connection.createStatement().use { it.execute(schema) } }
    }

    private fun applyMigrations(container: PostgreSQLContainer, dirProperty: String) {
        val dir = Path.of(System.getProperty(dirProperty))
        val migrations = Files.list(dir).use { files ->
            files.filter { it.fileName.toString().matches(MIGRATION_FILE) }
                .sorted(compareBy<Path> { versionOf(it.fileName.toString()) })
                .toList()
        }
        check(migrations.isNotEmpty()) { "app 마이그레이션 파일이 없음: $dir" }

        DriverManager.getConnection(container.jdbcUrl, container.username, container.password)
            .use { connection ->
                connection.createStatement().use { statement ->
                    migrations.forEach { statement.execute(Files.readString(it)) }
                }
            }
    }

    private fun versionOf(fileName: String): Int =
        fileName.removePrefix("V").substringBefore("__").toInt()

    private const val REDIS_TIMEOUT = "2s"
}
