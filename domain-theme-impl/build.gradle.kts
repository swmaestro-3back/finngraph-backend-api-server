import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":domain-theme-api"))
    implementation("org.springframework.boot:spring-boot-starter-jooq")

    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testRuntimeOnly("org.postgresql:postgresql")
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

tasks.named<Test>("test") {
    systemProperty("finngraph.schema.sql", rootProject.file("db/migration/V1__schema.sql").absolutePath)
}

val jooqCodegen: SourceSet = extensions.getByType<SourceSetContainer>().create("jooqCodegen")

dependencies {
    "jooqCodegenImplementation"("org.jooq:jooq-codegen")
    "jooqCodegenImplementation"("org.testcontainers:testcontainers-postgresql")
    "jooqCodegenRuntimeOnly"("org.postgresql:postgresql")
}

val schemaSqlFile = rootProject.layout.projectDirectory.file("db/migration/V1__schema.sql")
val jooqOutputDir = layout.buildDirectory.dir("generated/sources/jooq/main/kotlin")

val launcher21 = extensions.getByType<JavaToolchainService>().launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}

val generateJooq = tasks.register<JavaExec>("generateJooq") {
    group = "build"
    description = "Testcontainers로 Postgres를 띄우고 마이그레이션을 적용한 뒤 jOOQ 타입을 생성한다"

    classpath = jooqCodegen.runtimeClasspath
    mainClass.set("com.finngraph.theme.codegen.CodegenRunnerKt")

    javaLauncher.set(launcher21)

    args = listOf(schemaSqlFile.asFile.absolutePath, jooqOutputDir.get().asFile.absolutePath)

    inputs.file(schemaSqlFile).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(jooqOutputDir)
}

extensions.getByType<KotlinJvmProjectExtension>()
    .sourceSets.getByName("main").kotlin.srcDir(generateJooq)
