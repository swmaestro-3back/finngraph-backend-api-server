package com.finngraph.web.theme

import com.finngraph.support.HotThemeSeed
import com.finngraph.support.TestContainers
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ThemeHotApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var mapper: ObjectMapper

    @Test
    fun `기본 count는 20이며 무토큰으로 200`() {
        val response = rest.getForEntity("/api/v1/themes/hot", String::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.body!!.contains("\"data\""))
    }

    @Test
    fun `허용된 count 3종은 200`() {
        listOf(10, 20, 30).forEach { count ->
            val response = rest.getForEntity("/api/v1/themes/hot?count=$count", String::class.java)
            assertEquals(HttpStatus.OK, response.statusCode, "count=$count")
        }
    }

    @Test
    fun `응답 순서는 선정 순서와 같다`() {
        HotThemeSeed.seed()
        try {
            val response = rest.getForEntity("/api/v1/themes/hot?count=20", String::class.java)

            assertEquals(HttpStatus.OK, response.statusCode)
            val payload = mapper.readValue(response.body, Map::class.java)
            val names = (payload["data"] as List<*>).map { (it as Map<*, *>)["name"] }
            assertEquals(listOf("시드급등테마", "시드상승테마", "시드하락테마"), names)
        } finally {
            HotThemeSeed.cleanup()
        }
    }

    @Test
    fun `허용되지 않은 count는 400`() {
        listOf(0, 15, 40, 100).forEach { count ->
            val response = rest.getForEntity("/api/v1/themes/hot?count=$count", String::class.java)
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode, "count=$count")
            assertTrue(response.body!!.contains("count"), "count=$count")
        }
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
