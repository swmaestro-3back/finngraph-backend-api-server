package com.finngraph.stock.codegen

import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

fun main(args: Array<String>) {
    require(args.size == 2) { "마이그레이션 디렉터리, 출력 디렉터리 파라미터 필요" }
    val migrationsDir = Path.of(args[0])
    val outputDir = args[1]
    require(Files.isDirectory(migrationsDir)) { "마이그레이션 디렉터리 없음: $migrationsDir" }

    val postgres = PostgreSQLContainer(DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
    postgres.start()
    try {
        applyMigrations(postgres, migrationsDir)
        GenerationTool.generate(configuration(postgres, outputDir))
        println("jOOQ 생성 완료 → $outputDir")
    } finally {
        postgres.stop()
    }
}

private val MIGRATION_FILE = Regex("V\\d+__.*\\.sql")

private fun applyMigrations(postgres: PostgreSQLContainer, dir: Path) {
    val migrations = Files.list(dir).use { files ->
        files.filter { it.fileName.toString().matches(MIGRATION_FILE) }
            .sorted(compareBy { versionOf(it.fileName.toString()) })
            .toList()
    }
    require(migrations.isNotEmpty()) { "마이그레이션 파일 없음: $dir" }

    DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
        conn.createStatement().use { statement ->
            migrations.forEach { statement.execute(Files.readString(it)) }
        }
    }
}

private fun versionOf(fileName: String): Int =
    fileName.removePrefix("V").substringBefore("__").toInt()

private fun configuration(postgres: PostgreSQLContainer, outputDir: String) = Configuration()
    .withJdbc(
        Jdbc()
            .withDriver("org.postgresql.Driver")
            .withUrl(postgres.jdbcUrl)
            .withUser(postgres.username)
            .withPassword(postgres.password),
    )
    .withGenerator(
        Generator()
            .withName("org.jooq.codegen.KotlinGenerator")
            .withDatabase(
                Database()
                    .withName("org.jooq.meta.postgres.PostgresDatabase")
                    .withInputSchema("public")
                    .withIncludes(
                        "stocks|stock_candles_daily|stock_candles_period|stock_investor_flows|" +
                            "stock_valuations_daily|stock_dividends|company_financials|companies",
                    )
                    .withOutputSchemaToDefault(true),
            )
            .withGenerate(
                Generate()
                    .withRecords(true)
                    .withPojos(false)
                    .withDaos(false)
                    .withDeprecated(false),
            )
            .withTarget(
                Target()
                    .withPackageName("com.finngraph.stock.adapter.jooq")
                    .withDirectory(outputDir),
            ),
    )

private const val IMAGE = "pgvector/pgvector:pg17"
