plugins {
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":domain-news-api"))
    implementation("org.springframework.boot:spring-boot-starter-jooq")
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

tasks.named<Test>("test") {
    systemProperty("finngraph.schema.sql", rootProject.file("db/migration/V1__schema.sql").absolutePath)
}
