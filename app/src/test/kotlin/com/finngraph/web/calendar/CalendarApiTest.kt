package com.finngraph.web.calendar

import com.finngraph.composition.port.KakaoOAuthPort
import com.finngraph.support.AuthFixtures
import com.finngraph.support.AuthFixturesConfig
import com.finngraph.support.CalendarSeed
import com.finngraph.support.TestContainers
import com.finngraph.web.common.ErrorCode
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.resttestclient.TestRestTemplate
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(AuthFixturesConfig::class)
class CalendarApiTest {

    @Autowired
    lateinit var auth: AuthFixtures

    @Autowired
    lateinit var rest: TestRestTemplate

    @MockitoBean
    lateinit var kakaoClient: KakaoOAuthPort

    @BeforeAll
    fun seed() = CalendarSeed.seed()

    @AfterAll
    fun cleanup() = CalendarSeed.cleanup()

    @Test
    fun `C-01 공개 캘린더는 활성 KRX300 종목 일정과 휴장일만 준다`() {
        val data = get(NOVEMBER, null).data()

        assertEquals(setOf(CalendarSeed.KRX300_TICKER), data.events().map { it["ticker"] }.toSet())
        assertEquals(listOf("DIV_EX", "DIV_RECORD"), data.events().map { it["kind"] })
        assertEquals(listOf("2026-11-07", "2026-11-08", "2026-11-11"), data["closedDates"])
        assertEquals("2026-11-01", data["from"])
        assertEquals("2026-11-30", data["to"])
        assertNotNull(data["asOf"])
    }

    @Test
    fun `C-02 일정 필드는 raw 값으로 내려준다`() {
        val ex = get(NOVEMBER, null).data().events().first { it["kind"] == "DIV_EX" }

        assertEquals("2026-11-09", ex["date"])
        assertNull(ex["amount"])
        assertEquals("분기", ex["label"])
        assertEquals(false, ex["estimated"])
        assertEquals(false, ex["favorite"])
        assertEquals(emptyList<Any>(), ex["agenda"])
        assertEquals(false, ex["agendaTruncated"])
    }

    @Test
    fun `C-03 종목명은 stocks 이름으로 덮어쓴다`() {
        val names = get(NOVEMBER, null).data().events().map { it["stockName"] }.toSet()

        assertEquals(setOf("캘린더대형"), names)
    }

    @Test
    fun `C-04 회원 캘린더는 토큰이 없으면 401`() {
        assertEquals(HttpStatus.UNAUTHORIZED, rest.getForEntity("/api/v1/me/calendar$NOVEMBER", Map::class.java).statusCode)
    }

    @Test
    fun `C-05 회원 캘린더는 관심종목 일정을 favorite=true로 더하고 관심 먼저 정렬한다`() {
        val token = signup()
        assertEquals(HttpStatus.OK, put("/api/v1/me/favorites/STOCK/${CalendarSeed.FAVORITE_TICKER}", token).statusCode)

        val events = getMe(NOVEMBER, token).data().events()

        assertEquals(
            listOf(
                Triple("2026-11-09", "AGM", CalendarSeed.FAVORITE_TICKER),
                Triple("2026-11-09", "DIV_EX", CalendarSeed.KRX300_TICKER),
                Triple("2026-11-10", "DIV_RECORD", CalendarSeed.KRX300_TICKER),
            ),
            events.map { Triple(it["date"], it["kind"], it["ticker"]) },
        )
        val agm = events.first()
        assertEquals(true, agm["favorite"])
        assertEquals("캘린더관심", agm["stockName"])
        assertEquals(listOf("정관변경", "이사선임"), agm["agenda"])
        assertEquals(true, agm["agendaTruncated"])
        assertEquals("임시총회", agm["label"])
    }

    @Test
    fun `C-06 관심종목이 KRX300이어도 일정은 한 번만 favorite=true로 나온다`() {
        val token = signup()
        put("/api/v1/me/favorites/STOCK/${CalendarSeed.KRX300_TICKER}", token)

        val events = getMe(NOVEMBER, token).data().events()

        assertEquals(2, events.size)
        assertTrue(events.all { it["favorite"] == true })
    }

    @Test
    fun `C-07 관심종목이 없는 회원은 공개 캘린더와 같다`() {
        val token = signup()

        val mine = getMe(NOVEMBER, token).data().events()
        val public = get(NOVEMBER, null).data().events()

        assertEquals(public, mine)
    }

    @Test
    fun `C-08 기간은 양끝 포함 62일까지`() {
        assertEquals(HttpStatus.OK, get("?from=2026-11-01&to=2027-01-01", null).statusCode)

        val tooLong = get("?from=2026-11-01&to=2027-01-02", null)
        assertEquals(HttpStatus.BAD_REQUEST, tooLong.statusCode)
        assertEquals(ErrorCode.INVALID_PARAMETER, tooLong.errorCode())
    }

    @Test
    fun `C-09 날짜 형식 오류·순서 오류·누락은 400`() {
        listOf("?from=2026-11-31&to=2026-12-01", "?from=2026-12-01&to=2026-11-01", "?from=2026-11-01", "").forEach { query ->
            val response = get(query, null)
            assertEquals(HttpStatus.BAD_REQUEST, response.statusCode, query)
            assertEquals(ErrorCode.INVALID_PARAMETER, response.errorCode(), query)
        }
    }

    @Test
    fun `C-10 공모주는 오늘 기준 -14 ~ +30일 창에서 상태와 함께 준다`() {
        val response = rest.getForEntity("/api/v1/ipos", Map::class.java)
        assertEquals(HttpStatus.OK, response.statusCode)
        val data = response.data()
        assertNotNull(data["asOf"])
        val offerings = (data["offerings"] as List<*>).map { it as Map<*, *> }
            .filter { (it["ticker"] as String).startsWith("9601") }

        assertEquals(
            mapOf(
                "960101" to "UPCOMING",
                "960102" to "SUBSCRIBING",
                "960103" to "LISTING_PENDING",
                "960104" to "LISTED",
            ),
            offerings.associate { (it["ticker"] as String) to (it["status"] as String) },
        )
        val subscribing = offerings.first { it["ticker"] == "960102" }
        assertEquals(23500, subscribing["offerPrice"])
        assertEquals("테스트증권", subscribing["leadManagers"])
        assertNull(subscribing["listingDate"])
        assertFalse(offerings.any { it["ticker"] == "960105" })
    }

    @Test
    fun `C-11 관심종목이 상장폐지돼 종목명이 없으면 예탁원 이름으로 폴백한다`() {
        val token = signup()
        assertEquals(HttpStatus.OK, put("/api/v1/me/favorites/STOCK/${CalendarSeed.OTHER_TICKER}", token).statusCode)
        CalendarSeed.delistOther()

        val bonus = getMe(NOVEMBER, token).data().events().first { it["ticker"] == CalendarSeed.OTHER_TICKER }

        assertEquals("예탁원기타법인명", bonus["stockName"])
        assertEquals(true, bonus["favorite"])
        assertEquals(50.0, (bonus["ratio"] as Number).toDouble())
    }

    @Test
    fun `C-12 모르는 kind가 적재돼도 캘린더는 아는 일정만 준다`() {
        CalendarSeed.insertUnknownKind()
        try {
            val response = get(NOVEMBER, null)

            assertEquals(HttpStatus.OK, response.statusCode)
            assertFalse(response.data().events().any { it["kind"] == "EARNINGS" })
            assertEquals(listOf("DIV_EX", "DIV_RECORD"), response.data().events().map { it["kind"] })
        } finally {
            CalendarSeed.removeUnknownKind()
        }
    }

    private fun signup(): String {
        val email = "cal${SEQ.incrementAndGet()}@finngraph.test"
        val response = auth.verifiedSignup(email, nickname = "캘린더테스터")
        assertEquals(HttpStatus.CREATED, response.statusCode)
        return response.data()["accessToken"] as String
    }

    private fun get(query: String, token: String?): ResponseEntity<Map<*, *>> =
        rest.exchange("/api/v1/calendar$query", HttpMethod.GET, HttpEntity<Void>(bearer(token)), Map::class.java)

    private fun getMe(query: String, token: String): ResponseEntity<Map<*, *>> =
        rest.exchange("/api/v1/me/calendar$query", HttpMethod.GET, HttpEntity<Void>(bearer(token)), Map::class.java)

    private fun put(path: String, token: String): ResponseEntity<Map<*, *>> =
        rest.exchange(path, HttpMethod.PUT, HttpEntity<Void>(bearer(token)), Map::class.java)

    private fun bearer(token: String?) = HttpHeaders().apply { token?.let { setBearerAuth(it) } }

    private fun ResponseEntity<Map<*, *>>.data(): Map<*, *> = body!!["data"] as Map<*, *>

    private fun Map<*, *>.events(): List<Map<*, *>> = (this["events"] as List<*>).map { it as Map<*, *> }

    private fun ResponseEntity<Map<*, *>>.errorCode(): String = (body!!["error"] as Map<*, *>)["code"] as String

    companion object {
        private const val NOVEMBER = "?from=2026-11-01&to=2026-11-30"
        private val SEQ = AtomicInteger()

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
