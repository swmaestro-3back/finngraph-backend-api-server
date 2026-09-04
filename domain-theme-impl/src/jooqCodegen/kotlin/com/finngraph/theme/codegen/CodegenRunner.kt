package com.finngraph.theme.codegen

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
    require(args.size == 2) { "스키마 SQL 경로, 출력 디렉터리 파라미터 필요" }
    val schemaSql = Path.of(args[0])
    val outputDir = args[1]
    require(Files.exists(schemaSql)) { "스키마 파일 없음: $schemaSql" }

    val postgres = PostgreSQLContainer(DockerImageName.parse(IMAGE).asCompatibleSubstituteFor("postgres"))
    postgres.start()
    try {
        applySchema(postgres, Files.readString(schemaSql))
        GenerationTool.generate(configuration(postgres, outputDir))
        println("jOOQ 생성 완료 → $outputDir")
    } finally {
        postgres.stop()
    }
}

private fun applySchema(postgres: PostgreSQLContainer, sql: String) {
    DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
        conn.createStatement().use { it.execute(sql) }
    }
}

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
                        "themes|theme_stocks|stocks|stock_candles_daily|stock_valuations_daily",
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
                    .withPackageName("com.finngraph.theme.adapter.jooq")
                    .withDirectory(outputDir),
            ),
    )

private const val IMAGE = "pgvector/pgvector:pg17"
