plugins {
    kotlin("plugin.spring")
    id("io.spring.dependency-management")
}

dependencies {
    api(project(":domain-news-api"))
    api(project(":domain-stock-api"))
    api(project(":domain-theme-api"))
    api(project(":domain-user-api"))
    api(project(":domain-auth-api"))

    implementation("org.springframework:spring-context")
    implementation("org.springframework:spring-tx")
    implementation("org.slf4j:slf4j-api")
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}
