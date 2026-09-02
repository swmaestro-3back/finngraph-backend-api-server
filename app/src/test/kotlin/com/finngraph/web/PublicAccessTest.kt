package com.finngraph.web

import com.finngraph.support.TestContainers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.DockerClientFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PublicAccessTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Test
    fun `조회 목록 3종은 무토큰으로 200 반환`() {
        LIST_ENDPOINTS.forEach { path ->
            assertEquals(HttpStatus.OK, get(path).statusCode, path)
        }
    }

    @Test
    fun `조회 상세는 무토큰으로 404이며 401이 아님.`() {
        val authFailures = (PRIMARY_DETAIL_ENDPOINTS + SUB_RESOURCE_ENDPOINTS)
            .associateWith { get(it).statusCode }
            .filterValues { it.isAuthFailure() }

        assertTrue(authFailures.isEmpty(), "Security를 통과하지 못한 조회 경로: $authFailures")

        PRIMARY_DETAIL_ENDPOINTS.forEach { path ->
            assertEquals(HttpStatus.NOT_FOUND, get(path).statusCode, path)
        }
    }

    @Test
    fun `깨진 토큰을 달아도 공개 조회는 익명으로 통과함`() {
        val broken = HttpHeaders().apply { add(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt") }

        LIST_ENDPOINTS.forEach { path ->
            assertEquals(HttpStatus.OK, get(path, broken).statusCode, path)
        }
        PRIMARY_DETAIL_ENDPOINTS.forEach { path ->
            assertEquals(HttpStatus.NOT_FOUND, get(path, broken).statusCode, path)
        }
    }

    @Test
    fun `미기재 경로는 익명이면 404가 아니라 401`() {
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/me").statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/nothing-here").statusCode)
    }

    @Test
    fun `redis가 죽어도 조회 영향을 받지 않음.`() {
        rest.postForEntity(
            "/api/v1/auth/signup",
            mapOf(
                "email" to "u20b@finngraph.test",
                "password" to "password1234",
                "nickname" to "레디스",
            ),
            Map::class.java,
        )

        val docker = DockerClientFactory.instance().client()
        val containerId = TestContainers.redis.containerId

        docker.pauseContainerCmd(containerId).exec()
        try {
            val login = rest.postForEntity(
                "/api/v1/auth/login",
                mapOf("email" to "u20b@finngraph.test", "password" to "password1234"),
                Map::class.java,
            )
            assertTrue(login.statusCode.is5xxServerError, "Redis 다운 시 로그인은 실패해야 함.")

            LIST_ENDPOINTS.forEach { path ->
                assertEquals(HttpStatus.OK, get(path).statusCode, path)
            }
            PRIMARY_DETAIL_ENDPOINTS.forEach { path ->
                assertEquals(HttpStatus.NOT_FOUND, get(path).statusCode, path)
            }
            SUB_RESOURCE_ENDPOINTS.forEach { path ->
                assertFalse(get(path).statusCode.is5xxServerError, path)
            }
        } finally {
            docker.unpauseContainerCmd(containerId).exec()
        }
    }

    private fun get(path: String, headers: HttpHeaders = HttpHeaders()) =
        rest.exchange(path, HttpMethod.GET, HttpEntity<Void>(headers), String::class.java)

    private fun HttpStatusCode.isAuthFailure() =
        this == HttpStatus.UNAUTHORIZED || this == HttpStatus.FORBIDDEN

    companion object {
        private val LIST_ENDPOINTS = listOf(
            "/api/v1/themes",
            "/api/v1/stocks",
            "/api/v1/news",
        )

        private val PRIMARY_DETAIL_ENDPOINTS = listOf(
            "/api/v1/themes/없는테마",
            "/api/v1/stocks/000000",
            "/api/v1/news/999999",
        )

        private val SUB_RESOURCE_ENDPOINTS = listOf(
            "/api/v1/themes/없는테마/stocks",
            "/api/v1/themes/없는테마/news",
            "/api/v1/stocks/000000/candles",
            "/api/v1/stocks/000000/investor-flows",
            "/api/v1/stocks/000000/financials",
            "/api/v1/stocks/000000/news",
            "/api/v1/news/999999/companies",
        )

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
