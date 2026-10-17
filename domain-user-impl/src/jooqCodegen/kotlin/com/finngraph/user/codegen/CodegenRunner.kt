package com.finngraph.user.codegen

import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.Configuration
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Generator
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Target
import org.testcontainers.postgresql.PostgreSQLContainer
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

fun main(args: Array<String>) {
    require(args.size >= 2) { "최소 1개 이상 스키마 SQL 경로와 출력 디렉터리 파라미터 필요" }
    val outputDir = args.last()
    val schemaFiles = args.dropLast(1).map(Path::of)
    schemaFiles.forEach { require(Files.exists(it)) { "스키마 파일 없음: $it" } }

    val postgres = PostgreSQLContainer(IMAGE)
    postgres.start()
    try {
        applySchema(postgres, schemaFiles)
        GenerationTool.generate(configuration(postgres, outputDir))
        println("jOOQ 생성 완료 → $outputDir")
    } finally {
        postgres.stop()
    }
}

private fun applySchema(postgres: PostgreSQLContainer, schemaFiles: List<Path>) {
    DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
        conn.createStatement().use { statement ->
            schemaFiles.forEach { statement.execute(Files.readString(it)) }
        }
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
                    .withIncludes("users")
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
                    .withPackageName("com.finngraph.user.adapter.jooq")
                    .withDirectory(outputDir),
            ),
    )

private const val IMAGE = "postgres:17"
