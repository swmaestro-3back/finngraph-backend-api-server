package com.finngraph

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

import org.testcontainers.postgresql.PostgreSQLContainer

@SpringBootTest
@Testcontainers
class BackendServerApplicationTests {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:17")
    }

    @Test
    fun contextLoads() {
    }
}
