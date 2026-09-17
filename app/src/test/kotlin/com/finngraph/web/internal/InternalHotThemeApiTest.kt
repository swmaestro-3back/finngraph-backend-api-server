package com.finngraph.web.internal

import com.finngraph.hotthemes.RedisHotThemePublisher
import com.finngraph.support.HotThemeSeed
import com.finngraph.support.TestContainers
import com.finngraph.web.security.InternalTokenFilter
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class InternalHotThemeApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var redis: StringRedisTemplate

    @Test
    fun `토큰 없이 호출하면 401`() {
        val response = rest.postForEntity(PATH, HttpEntity<Void>(HttpHeaders()), String::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `잘못된 토큰은 401`() {
        val response = rest.postForEntity(PATH, request("wrong-token"), String::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `테마가 없으면 200 skipped`() {
        val response = rest.postForEntity(PATH, request(TOKEN), String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.contains("\"skipped\""))
    }

    @Test
    fun `정상 토큰이면 발행하고 결과를 반환한다`() {
        HotThemeSeed.seed()
        redis.delete(RedisHotThemePublisher.KEY)
        try {
            val response = rest.postForEntity(PATH, request(TOKEN), String::class.java)

            assertEquals(HttpStatus.OK, response.statusCode)
            assertTrue(response.body!!.contains("\"published\""))
            assertTrue(response.body!!.contains("\"2026-07-31\""))
            assertNotNull(redis.opsForValue().get(RedisHotThemePublisher.KEY))
        } finally {
            redis.delete(RedisHotThemePublisher.KEY)
            HotThemeSeed.cleanup()
        }
    }

    private fun request(token: String) = HttpEntity<Void>(
        HttpHeaders().apply { add(InternalTokenFilter.TOKEN_HEADER, token) },
    )

    companion object {
        private const val PATH = "/internal/hot-themes/publish"
        private const val TOKEN = "test-internal-token"

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            TestContainers.register(registry)
            registry.add("app.internal.token") { TOKEN }
        }
    }
}
