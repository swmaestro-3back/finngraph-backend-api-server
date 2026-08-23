plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "backend-server"

include(
    "app",
    "domain-news-api",
    "domain-news-impl",
)
