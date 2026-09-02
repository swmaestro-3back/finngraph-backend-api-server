import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":domain-auth-api"))
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testRuntimeOnly("org.postgresql:postgresql")
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

val jooqCodegen: SourceSet = extensions.getByType<SourceSetContainer>().create("jooqCodegen")

dependencies {
    "jooqCodegenImplementation"("org.jooq:jooq-codegen")
    "jooqCodegenImplementation"("org.testcontainers:testcontainers-postgresql")
    "jooqCodegenRuntimeOnly"("org.postgresql:postgresql")
}

val appMigrations = rootProject.fileTree("db/migration-app") { include("V*__*.sql") }
val jooqOutputDir = layout.buildDirectory.dir("generated/sources/jooq/main/kotlin")

val launcher21 = extensions.getByType<JavaToolchainService>().launcherFor {
    languageVersion.set(JavaLanguageVersion.of(21))
}

val generateJooq = tasks.register<JavaExec>("generateJooq") {
    group = "build"

    classpath = jooqCodegen.runtimeClasspath
    mainClass.set("com.finngraph.auth.codegen.CodegenRunnerKt")

    javaLauncher.set(launcher21)

    argumentProviders.add(
        CommandLineArgumentProvider {
            appMigrations.files.sortedBy { it.name }.map { it.absolutePath } +
                jooqOutputDir.get().asFile.absolutePath
        },
    )

    inputs.files(appMigrations).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(jooqOutputDir)
}

extensions.getByType<KotlinJvmProjectExtension>()
    .sourceSets.getByName("main").kotlin.srcDir(generateJooq)
