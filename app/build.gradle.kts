import org.springframework.boot.gradle.tasks.run.BootRun

plugins {
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

tasks.named<BootRun>("bootRun") {
    systemProperty("spring.profiles.active", System.getProperty("spring.profiles.active") ?: "local")
}

tasks.named<Test>("test") {
    systemProperty("finngraph.app.migrations.dir", rootProject.file("db/migration-app").absolutePath)
    systemProperty("finngraph.etl.schema.sql", rootProject.file("db/migration/V1__schema.sql").absolutePath)
    inputs.dir(rootProject.file("db/migration-app"))
    inputs.file(rootProject.file("db/migration/V1__schema.sql"))
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

    implementation(project(":domain-auth-api"))
    runtimeOnly(project(":domain-auth-impl"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    runtimeOnly("org.postgresql:postgresql")

    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.security:spring-security-oauth2-jose")
    implementation("org.bouncycastle:bcprov-jdk18on:1.81")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")

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
