package com.finngraph.web.briefing

import com.finngraph.briefing.port.BriefingStorePort
import com.finngraph.support.AuthFixtures
import com.finngraph.support.AuthFixturesConfig
import com.finngraph.support.BriefingFixtures
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
import java.sql.DriverManager
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(AuthFixturesConfig::class)
class BriefingApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var store: BriefingStorePort

    @Autowired
    lateinit var auth: AuthFixtures

    private val latest = BriefingFixtures.sample(LocalDate.parse("2026-07-31"))
    private val older = BriefingFixtures.sample(LocalDate.parse("2026-07-30")).copy(headline = null)

    @BeforeAll
    fun seed() {
        clear()
        store.upsert(older)
        store.upsert(latest)
    }

    @AfterAll
    fun cleanup() = clear()

    @Test
    fun `비회원은 공개 필드만 받고 회원 전용 필드는 null 과 개수로 온다`() {
        val response = get("/api/v1/briefings/latest", null)

        assertEquals(HttpStatus.OK, response.statusCode)
        val data = response.data()
        assertEquals("2026-07-31", data["baseDate"])
        assertEquals("READY", data["status"])
        assertEquals(latest.headline?.text, (data["headline"] as Map<*, *>)["text"])
        assertEquals(60, (data["market"] as Map<*, *>)["upCount"])

        val issue = (data["issues"] as List<*>).single() as Map<*, *>
        assertEquals("시드 이슈 클러스터", issue["title"])
        assertEquals(1, (issue["articles"] as List<*>).size)
        assertEquals(1, (issue["stocks"] as List<*>).size)
        assertNull(issue["commentary"])

        assertNull(data["watchPoints"])
        assertNull(data["risks"])
        assertNull(data["relationGraph"])
        val analyzed = (data["analyzedNews"] as List<*>).single() as Map<*, *>
        assertEquals(1, analyzed["relationCount"])
        assertNull(analyzed["relations"])

        val locked = data["locked"] as Map<*, *>
        assertEquals(1, locked["commentaries"])
        assertEquals(1, locked["watchPoints"])
        assertEquals(2, locked["risks"])
        assertEquals(1, locked["relations"])
        assertEquals(1, locked["graphEdges"])
        assertEquals(1, (data["themes"] as List<*>).size)
    }

    @Test
    fun `회원은 해설·지켜볼 점·리스크·관계·그래프를 전부 받는다`() {
        val token = auth.verifiedSignup("brief01@finngraph.test").data()["accessToken"] as String

        val data = get("/api/v1/briefings/latest", token).data()

        val issue = (data["issues"] as List<*>).single() as Map<*, *>
        val commentary = issue["commentary"] as Map<*, *>
        val sentences = commentary["sentences"] as List<*>
        assertEquals(2, sentences.size)
        val citation = ((sentences[0] as Map<*, *>)["citations"] as List<*>).single() as Map<*, *>
        assertEquals("NEWS", citation["type"])
        assertEquals("https://seed.test/news/9001", citation["url"])

        assertEquals(1, (data["watchPoints"] as List<*>).size)
        assertEquals(2, (data["risks"] as List<*>).size)
        val analyzed = (data["analyzedNews"] as List<*>).single() as Map<*, *>
        val relation = (analyzed["relations"] as List<*>).single() as Map<*, *>
        assertEquals("SUPPLIES_TO", relation["relation"])
        assertEquals("시드파트너", (relation["object"] as Map<*, *>)["name"])
        val graph = data["relationGraph"] as Map<*, *>
        assertEquals(2, (graph["nodes"] as List<*>).size)
        assertEquals(1, (graph["edges"] as List<*>).size)
        assertNull(data["locked"])
    }

    @Test
    fun `깨진 토큰은 익명으로 취급한다`() {
        val data = get("/api/v1/briefings/latest", "not-a-jwt").data()

        assertNull(data["risks"])
        assertNotNull(data["locked"])
    }

    @Test
    fun `날짜로 조회하고 없으면 404`() {
        assertEquals("2026-07-30", get("/api/v1/briefings/2026-07-30", null).data()["baseDate"])

        val missing = get("/api/v1/briefings/2026-01-05", null)
        assertEquals(HttpStatus.NOT_FOUND, missing.statusCode)
        assertEquals(ErrorCode.BRIEFING_NOT_FOUND, (missing.body!!["error"] as Map<*, *>)["code"])
    }

    @Test
    fun `날짜 형식이 틀리면 400`() {
        assertEquals(HttpStatus.BAD_REQUEST, get("/api/v1/briefings/2026-1-5", null).statusCode)
    }

    @Test
    fun `목록은 최신순 요약이며 limit 범위를 검사한다`() {
        val rows = get("/api/v1/briefings?limit=30", null).body!!["data"] as List<*>

        assertEquals(listOf("2026-07-31", "2026-07-30"), rows.map { (it as Map<*, *>)["baseDate"] })
        assertEquals(latest.headline?.text, (rows[0] as Map<*, *>)["headline"])
        assertNull((rows[1] as Map<*, *>)["headline"])
        assertEquals(HttpStatus.BAD_REQUEST, get("/api/v1/briefings?limit=0", null).statusCode)
        assertEquals(HttpStatus.BAD_REQUEST, get("/api/v1/briefings?limit=91", null).statusCode)
    }

    private fun get(path: String, token: String?): ResponseEntity<Map<*, *>> {
        val headers = HttpHeaders().apply { token?.let { add(HttpHeaders.AUTHORIZATION, "Bearer $it") } }
        return rest.exchange(path, HttpMethod.GET, HttpEntity<Void>(headers), Map::class.java)
    }

    private fun ResponseEntity<Map<*, *>>.data(): Map<*, *> = body!!["data"] as Map<*, *>

    private fun clear() {
        val app = TestContainers.appPostgres
        DriverManager.getConnection(app.jdbcUrl, app.username, app.password).use { connection ->
            connection.createStatement().use { it.execute("DELETE FROM daily_briefings") }
        }
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
