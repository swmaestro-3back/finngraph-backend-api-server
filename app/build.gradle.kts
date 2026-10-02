import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    kotlin("plugin.spring")
    kotlin("plugin.serialization")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val koogVersion = "1.3.0"
val awsSdkVersion = "2.55.10"

tasks.named<BootRun>("bootRun") {
    systemProperty("spring.profiles.active", System.getProperty("spring.profiles.active") ?: "local")
}

tasks.named<Test>("test") {
    systemProperty("finngraph.app.migrations.dir", rootProject.file("db/migration-app").absolutePath)
    systemProperty("finngraph.etl.migrations.dir", rootProject.file("db/migration").absolutePath)
    inputs.dir(rootProject.file("db/migration-app"))
    inputs.dir(rootProject.file("db/migration"))
}

dependencies {
    implementation(project(":composition"))

    implementation(project(":domain-news-api"))
    runtimeOnly(project(":domain-news-impl"))

    implementation(project(":domain-stock-api"))
    runtimeOnly(project(":domain-stock-impl"))

    implementation(project(":domain-theme-api"))
    runtimeOnly(project(":domain-theme-impl"))

    implementation(project(":domain-user-api"))
    runtimeOnly(project(":domain-user-impl"))

    implementation(project(":domain-favorite-api"))
    runtimeOnly(project(":domain-favorite-impl"))

    implementation(project(":domain-auth-api"))
    runtimeOnly(project(":domain-auth-impl"))

    implementation(project(":domain-briefing-api"))
    runtimeOnly(project(":domain-briefing-impl"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("org.postgresql:postgresql")

    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.bouncycastle:bcprov-jdk18on:1.81")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation(platform("software.amazon.awssdk:bom:$awsSdkVersion"))
    implementation("software.amazon.awssdk:sesv2") {
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")

    implementation("ai.koog:prompt-executor-bedrock-client:$koogVersion")
    implementation("ai.koog:prompt-executor-model:$koogVersion")
    implementation("ai.koog:prompt-structure:$koogVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")

    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    testImplementation("org.springframework.boot:spring-boot-starter-test")

    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-restclient")
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
}
