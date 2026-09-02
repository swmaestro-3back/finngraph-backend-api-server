plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "backend-server"

include(
    "app",
    "domain-news-api",
    "domain-news-impl",
    "domain-stock-api",
    "domain-stock-impl",
    "domain-theme-api",
    "domain-theme-impl",
    "domain-user-api",
    "domain-user-impl",
    "domain-auth-api",
    "domain-auth-impl",
)
