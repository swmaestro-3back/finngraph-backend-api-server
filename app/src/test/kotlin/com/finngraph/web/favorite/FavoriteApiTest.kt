package com.finngraph.web.favorite

import com.finngraph.support.FavoriteSeed
import com.finngraph.support.TestContainers
import com.finngraph.web.common.ErrorCode
import com.finngraph.composition.port.KakaoOAuthPort
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.sql.DriverManager
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FavoriteApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @MockitoBean
    lateinit var kakaoClient: KakaoOAuthPort

    @BeforeAll
    fun seed() = FavoriteSeed.seed()

    @AfterAll
    fun cleanup() = FavoriteSeed.cleanup()

    @Test
    fun `F-01 종목을 등록하면 목록에 현재가와 함께 나타난다`() {
        val token = signup()

        val put = put("/api/v1/me/favorites/STOCK/${FavoriteSeed.ACTIVE_TICKER}", token)
        assertEquals(HttpStatus.OK, put.statusCode)
        assertEquals("STOCK", put.data()["type"])
        assertEquals(FavoriteSeed.ACTIVE_TICKER, put.data()["key"])
        assertNotNull(put.data()["createdAt"])

        val list = get("/api/v1/me/favorites", token)
        assertEquals(HttpStatus.OK, list.statusCode)
        assertEquals(1, list.data()["count"])
        assertEquals(50, list.data()["limit"])
        val item = list.items().single()
        assertEquals("STOCK", item["type"])
        assertEquals(FavoriteSeed.ACTIVE_TICKER, item["key"])
        assertEquals(true, item["resolved"])
        val stock = item["stock"] as Map<*, *>
        assertEquals("관심시드1", stock["name"])
        assertEquals(110.0, (stock["price"] as Number).toDouble())
        assertEquals(10.0, (stock["change"] as Number).toDouble())
        assertNull(item["theme"])
    }

    @Test
    fun `F-02 같은 대상을 다시 등록해도 200이고 한 건으로 유지된다`() {
        val token = signup()
        val first = put("/api/v1/me/favorites/STOCK/${FavoriteSeed.ACTIVE_TICKER}", token)
        val second = put("/api/v1/me/favorites/STOCK/${FavoriteSeed.ACTIVE_TICKER}", token)

        assertEquals(HttpStatus.OK, second.statusCode)
        assertEquals(first.data()["createdAt"], second.data()["createdAt"])
        assertEquals(1, get("/api/v1/me/favorites", token).data()["count"])
    }

    @Test
    fun `F-03 테마를 등록하면 목록에 테마 정보가 붙는다`() {
        val token = signup()
        assertEquals(HttpStatus.OK, put("/api/v1/me/favorites/THEME/${FavoriteSeed.THEME_ID}", token).statusCode)

        val item = get("/api/v1/me/favorites", token).items().single()
        assertEquals("THEME", item["type"])
        assertEquals("${FavoriteSeed.THEME_ID}", item["key"])
        assertEquals(true, item["resolved"])
        val theme = item["theme"] as Map<*, *>
        assertEquals(FavoriteSeed.THEME_ID.toInt(), theme["id"])
        assertEquals("관심시드테마", theme["name"])
        assertEquals(1, theme["stockCount"])
        assertNull(item["stock"])
    }

    @Test
    fun `F-04 존재하지 않는 종목이나 테마는 404`() {
        val token = signup()
        val stock = put("/api/v1/me/favorites/STOCK/${FavoriteSeed.MISSING_TICKER}", token)
        assertEquals(HttpStatus.NOT_FOUND, stock.statusCode)
        assertEquals(ErrorCode.FAVORITE_TARGET_NOT_FOUND, stock.errorCode())

        val theme = put("/api/v1/me/favorites/THEME/${FavoriteSeed.MISSING_THEME_ID}", token)
        assertEquals(HttpStatus.NOT_FOUND, theme.statusCode)
        assertEquals(ErrorCode.FAVORITE_TARGET_NOT_FOUND, theme.errorCode())
        assertEquals(0, get("/api/v1/me/favorites", token).data()["count"])
    }

    @Test
    fun `F-05 유형이나 키 형식이 틀리면 400 fieldErrors`() {
        val token = signup()
        val lower = put("/api/v1/me/favorites/stock/${FavoriteSeed.ACTIVE_TICKER}", token)
        assertEquals(HttpStatus.BAD_REQUEST, lower.statusCode)
        assertEquals(ErrorCode.INVALID_PARAMETER, lower.errorCode())
        assertNotNull(lower.fieldErrors()["type"])

        assertEquals(HttpStatus.BAD_REQUEST, put("/api/v1/me/favorites/NEWS/1", token).statusCode)

        val badTheme = put("/api/v1/me/favorites/THEME/abc", token)
        assertEquals(HttpStatus.BAD_REQUEST, badTheme.statusCode)
        assertNotNull(badTheme.fieldErrors()["key"])

        assertEquals(HttpStatus.BAD_REQUEST, put("/api/v1/me/favorites/STOCK/00-59", token).statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, delete("/api/v1/me/favorites/THEME/0", token).statusCode)
    }

    @Test
    fun `F-06 합계 50건을 넘는 신규 등록은 409`() {
        val token = signup()
        fillTo(token, 50)

        val over = put("/api/v1/me/favorites/STOCK/${FavoriteSeed.ACTIVE_TICKER_2}", token)
        assertEquals(HttpStatus.CONFLICT, over.statusCode)
        assertEquals(ErrorCode.FAVORITE_LIMIT_EXCEEDED, over.errorCode())
        assertEquals(50, over.errorDetails()["limit"])
        assertEquals(50, over.errorDetails()["count"])
        assertEquals(50, get("/api/v1/me/favorites", token).data()["count"])
    }

    @Test
    fun `F-07 50건 상태에서 이미 등록된 대상의 재등록은 200`() {
        val token = signup()
        fillTo(token, 50)

        assertEquals(HttpStatus.OK, put("/api/v1/me/favorites/STOCK/${FavoriteSeed.ACTIVE_TICKER}", token).statusCode)
        assertEquals(50, get("/api/v1/me/favorites", token).data()["count"])
    }

    @Test
    fun `F-08 해제는 멱등이고 해제 후 재등록할 수 있다`() {
        val token = signup()
        put("/api/v1/me/favorites/STOCK/${FavoriteSeed.ACTIVE_TICKER}", token)

        assertEquals(HttpStatus.NO_CONTENT, delete("/api/v1/me/favorites/STOCK/${FavoriteSeed.ACTIVE_TICKER}", token).statusCode)
        assertEquals(HttpStatus.NO_CONTENT, delete("/api/v1/me/favorites/STOCK/${FavoriteSeed.ACTIVE_TICKER}", token).statusCode)
        assertEquals(0, get("/api/v1/me/favorites", token).data()["count"])
        assertEquals(HttpStatus.OK, put("/api/v1/me/favorites/STOCK/${FavoriteSeed.ACTIVE_TICKER}", token).statusCode)
        assertEquals(1, get("/api/v1/me/favorites", token).data()["count"])
    }

    @Test
    fun `F-09 토큰이 없으면 네 경로 모두 401`() {
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/me/favorites", null).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, put("/api/v1/me/favorites/STOCK/${FavoriteSeed.ACTIVE_TICKER}", null).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, delete("/api/v1/me/favorites/STOCK/${FavoriteSeed.ACTIVE_TICKER}", null).statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, get("/api/v1/me/favorites/news", null).statusCode)
    }

    @Test
    fun `F-10 관심종목 뉴스 피드는 처리된 뉴스만 종목 합집합으로 내려주고 미처리는 제외한다`() {
        val token = signup()
        put("/api/v1/me/favorites/STOCK/${FavoriteSeed.ACTIVE_TICKER}", token)
        put("/api/v1/me/favorites/STOCK/${FavoriteSeed.ACTIVE_TICKER_2}", token)

        val page = get("/api/v1/me/favorites/news", token)
        assertEquals(HttpStatus.OK, page.statusCode)
        val ids = page.pageData().map { (it["id"] as Number).toLong() }
        assertEquals(setOf(9301L, 9302L, 9304L), ids.toSet())
        assertEquals(3, (page.body!!["pagination"] as Map<*, *>)["totalElements"])
    }

    @Test
    fun `F-11 관심 종목이 없으면 뉴스 피드는 빈 페이지`() {
        val token = signup()
        put("/api/v1/me/favorites/THEME/${FavoriteSeed.THEME_ID}", token)

        val page = get("/api/v1/me/favorites/news", token)
        assertEquals(HttpStatus.OK, page.statusCode)
        assertTrue(page.pageData().isEmpty())
        assertEquals(0, (page.body!!["pagination"] as Map<*, *>)["totalElements"])
    }

    @Test
    fun `F-12 탈퇴하면 즐겨찾기가 함께 삭제된다`() {
        val token = signup()
        put("/api/v1/me/favorites/STOCK/${FavoriteSeed.ACTIVE_TICKER}", token)
        val userId = countFavoritesOwner()

        assertEquals(HttpStatus.NO_CONTENT, delete("/api/v1/me", token).statusCode)
        assertEquals(0, countFavorites(userId))
    }

    @Test
    fun `F-13 다른 사용자의 즐겨찾기는 보이지 않는다`() {
        val a = signup()
        val b = signup()
        put("/api/v1/me/favorites/STOCK/${FavoriteSeed.ACTIVE_TICKER}", a)

        assertEquals(0, get("/api/v1/me/favorites", b).data()["count"])
        assertEquals(HttpStatus.NO_CONTENT, delete("/api/v1/me/favorites/STOCK/${FavoriteSeed.ACTIVE_TICKER}", b).statusCode)
        assertEquals(1, get("/api/v1/me/favorites", a).data()["count"])
    }

    @Test
    fun `F-14 ETL에서 사라진 대상은 resolved=false로 남는다`() {
        val token = signup()
        assertEquals(HttpStatus.OK, put("/api/v1/me/favorites/STOCK/${FavoriteSeed.DELISTED_TICKER}", token).statusCode)
        FavoriteSeed.delistStock()

        val item = get("/api/v1/me/favorites", token).items().single()
        assertEquals(FavoriteSeed.DELISTED_TICKER, item["key"])
        assertEquals(false, item["resolved"])
        assertNull(item["stock"])
        assertFalse(item.containsKey("missing"))
    }

    @Test
    fun `F-15 같은 대상을 동시에 등록해도 한 건만 남고 전부 200`() {
        val token = signup()
        val statuses = concurrently(10) { put("/api/v1/me/favorites/STOCK/${FavoriteSeed.ACTIVE_TICKER}", token).statusCode }

        assertTrue(statuses.all { it == HttpStatus.OK }, "statuses=$statuses")
        assertEquals(1, get("/api/v1/me/favorites", token).data()["count"])
    }

    @Test
    fun `F-16 상한 근처에서 동시에 등록해도 정확히 50건에서 멈춘다`() {
        val token = signup()
        fillTo(token, 45)
        val fresh = (FavoriteSeed.THEME_ID + 50..FavoriteSeed.THEME_ID + 59).toList()

        val statuses = concurrently(fresh.size) { i -> put("/api/v1/me/favorites/THEME/${fresh[i]}", token).statusCode }

        assertEquals(5, statuses.count { it == HttpStatus.OK }, "statuses=$statuses")
        assertEquals(5, statuses.count { it == HttpStatus.CONFLICT }, "statuses=$statuses")
        assertEquals(50, get("/api/v1/me/favorites", token).data()["count"])
    }

    private fun concurrently(workers: Int, action: (Int) -> HttpStatusCode): List<HttpStatusCode> {
        val gate = java.util.concurrent.CountDownLatch(1)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(workers)
        try {
            val futures = (0 until workers).map { i ->
                pool.submit<HttpStatusCode> {
                    gate.await()
                    action(i)
                }
            }
            gate.countDown()
            return futures.map { it.get() }
        } finally {
            pool.shutdown()
        }
    }

    private fun fillTo(token: String, total: Int) {
        assertEquals(HttpStatus.OK, put("/api/v1/me/favorites/STOCK/${FavoriteSeed.ACTIVE_TICKER}", token).statusCode)
        (FavoriteSeed.THEME_ID until FavoriteSeed.THEME_ID + total - 1).forEach { themeId ->
            assertEquals(HttpStatus.OK, put("/api/v1/me/favorites/THEME/$themeId", token).statusCode)
        }
        assertEquals(total, get("/api/v1/me/favorites", token).data()["count"])
    }

    private fun signup(): String {
        val email = "fav${SEQ.incrementAndGet()}@finngraph.test"
        val response = rest.postForEntity(
            "/api/v1/auth/signup",
            mapOf("email" to email, "password" to PASSWORD, "nickname" to "관심테스터"),
            Map::class.java,
        )
        assertEquals(HttpStatus.CREATED, response.statusCode)
        return response.data()["accessToken"] as String
    }

    private fun get(path: String, token: String?) =
        rest.exchange(path, HttpMethod.GET, HttpEntity<Void>(bearer(token)), Map::class.java)

    private fun put(path: String, token: String?) =
        rest.exchange(path, HttpMethod.PUT, HttpEntity<Void>(bearer(token)), Map::class.java)

    private fun delete(path: String, token: String?) =
        rest.exchange(path, HttpMethod.DELETE, HttpEntity<Void>(bearer(token)), Map::class.java)

    private fun bearer(token: String?) = HttpHeaders().apply {
        token?.let { add(HttpHeaders.AUTHORIZATION, "Bearer $it") }
    }

    private fun ResponseEntity<Map<*, *>>.data(): Map<*, *> = body!!["data"] as Map<*, *>

    private fun ResponseEntity<Map<*, *>>.items(): List<Map<*, *>> =
        (data()["items"] as List<*>).map { it as Map<*, *> }

    private fun ResponseEntity<Map<*, *>>.pageData(): List<Map<*, *>> =
        (body!!["data"] as List<*>).map { it as Map<*, *> }

    private fun ResponseEntity<Map<*, *>>.errorCode(): String =
        (body!!["error"] as Map<*, *>)["code"] as String

    private fun ResponseEntity<Map<*, *>>.errorDetails(): Map<*, *> =
        (body!!["error"] as Map<*, *>)["details"] as Map<*, *>

    private fun ResponseEntity<Map<*, *>>.fieldErrors(): Map<*, *> =
        errorDetails()["fieldErrors"] as Map<*, *>

    private fun countFavoritesOwner(): Long = appQuery("SELECT user_id FROM favorites ORDER BY id DESC LIMIT 1")

    private fun countFavorites(userId: Long): Long =
        appQuery("SELECT count(*) FROM favorites WHERE user_id = $userId")

    private fun appQuery(sql: String): Long {
        val app = TestContainers.appPostgres
        DriverManager.getConnection(app.jdbcUrl, app.username, app.password).use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rs ->
                    rs.next()
                    return rs.getLong(1)
                }
            }
        }
    }

    companion object {
        private const val PASSWORD = "password1234"
        private val SEQ = AtomicInteger()

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
