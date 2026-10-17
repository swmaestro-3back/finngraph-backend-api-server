package com.finngraph

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.jooq.autoconfigure.JooqAutoConfiguration
import org.springframework.boot.runApplication

@SpringBootApplication(exclude = [JooqAutoConfiguration::class])
class BackendServerApplication

fun main(args: Array<String>) {
    runApplication<BackendServerApplication>(*args)
}
