package com.finngraph.web.internal

import com.finngraph.briefing.model.BriefingStatus
import com.finngraph.briefing.model.RiskKind
import com.finngraph.briefing.model.WatchKind
import com.finngraph.briefing.port.BriefingStorePort
import com.finngraph.support.BriefingSeed
import com.finngraph.support.FakeBriefingWriter
import com.finngraph.support.FakeBriefingWriterConfig
import com.finngraph.support.TestContainers
import com.finngraph.web.security.InternalTokenFilter
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
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.sql.DriverManager
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(FakeBriefingWriterConfig::class)
class InternalBriefingApiTest {

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var store: BriefingStorePort

    @Autowired
    lateinit var writer: FakeBriefingWriter

    @BeforeAll
    fun seed() {
        BriefingSeed.seed()
        clearBriefings()
    }

    @AfterAll
    fun cleanup() {
        BriefingSeed.cleanup()
        clearBriefings()
    }

    @Test
    fun `토큰 없이 호출하면 401`() {
        val response = rest.postForEntity(PATH, HttpEntity<Void>(HttpHeaders()), String::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `생성하면 이슈·관계·리스크·지켜볼 점이 저장되고 검증 탈락 문장은 빠진다`() {
        clearBriefings()
        writer.enabled = true

        val response = rest.postForEntity(PATH, request(TOKEN), Map::class.java)

        assertEquals(HttpStatus.OK, response.statusCode)
        val data = response.body!!["data"] as Map<*, *>
        assertEquals("GENERATED", data["result"])
        assertEquals(BriefingSeed.BASE_DATE, data["baseDate"])
        assertEquals("READY", data["status"])
        assertEquals(1, data["issueCount"])
        assertEquals(2, data["droppedSentences"])

        val briefing = assertNotNull(store.findByDate(LocalDate.parse(BriefingSeed.BASE_DATE)))
        assertEquals(BriefingStatus.READY, briefing.status)
        assertEquals(LocalDate.parse(BriefingSeed.PREV_DATE), briefing.previousTradingDate)

        val issue = briefing.issues.single()
        assertEquals(BriefingSeed.HOT_CLUSTER, issue.clusterId)
        assertEquals(3, issue.newsCount)
        assertEquals(listOf(BriefingSeed.NEWS_C, BriefingSeed.NEWS_B, BriefingSeed.NEWS_A), issue.articles.map { it.newsId })
        assertEquals(listOf(BriefingSeed.SUPPLIER_TICKER, BriefingSeed.PARTNER_TICKER), issue.stocks.map { it.ticker })
        val commentary = assertNotNull(issue.commentary)
        assertEquals(2, commentary.size)
        assertEquals("NEWS:${BriefingSeed.NEWS_C}", commentary[0].citations.single().key())
        assertEquals("RELATION:${BriefingSeed.REL_SUPPLY}", commentary[1].citations.single().key())

        assertEquals("CLUSTER:${BriefingSeed.HOT_CLUSTER}", assertNotNull(briefing.headline).citations.single().key())

        assertEquals(listOf(BriefingSeed.NEWS_D, BriefingSeed.NEWS_A), briefing.analyzedNews.map { it.newsId })
        val analyzed = briefing.analyzedNews.single { it.newsId == BriefingSeed.NEWS_A }
        assertEquals("https://press.test/a", analyzed.url)
        assertEquals(BriefingSeed.PARTNER_TICKER, analyzed.relations.single().target.ticker)

        val edges = briefing.relationGraph.edges.associateBy { it.id }
        val supply = assertNotNull(edges["T:${BriefingSeed.SUPPLIER_TICKER}|SUPPLIES_TO|T:${BriefingSeed.PARTNER_TICKER}"])
        assertEquals(2, supply.mentionedCount)
        assertEquals("terminated", supply.polarity)
        assertEquals(listOf("NEWS:${BriefingSeed.NEWS_A}", "DISCLOSURE:${BriefingSeed.CORRECTION_RCEPT}"), supply.sources.map { it.key() })
        assertNotNull(edges["T:${BriefingSeed.OTHER_TICKER}|INVESTS_IN|N:시드비상장"])
        assertEquals(2, edges.size)

        assertEquals(listOf(RiskKind.CORRECTION, RiskKind.RELATION_TERMINATED), briefing.risks.map { it.kind })
        assertEquals("계약 종료일 변경", briefing.risks[0].detail)

        assertEquals(
            listOf(WatchKind.CORRECTION, WatchKind.CONTRACT_END, WatchKind.PLANNED_RELATION),
            briefing.watchPoints.map { it.kind },
        )
        assertEquals(listOf(BriefingSeed.ADMIN_TICKER, BriefingSeed.SUSPENDED_TICKER, BriefingSeed.DELISTING_TICKER), briefing.flaggedSnapshot.map { it.ticker })
        assertTrue(briefing.themes.isEmpty())
    }

    @Test
    fun `이미 있으면 건너뛰고 force 면 다시 만든다`() {
        clearBriefings()
        writer.enabled = true
        rest.postForEntity(PATH, request(TOKEN), Map::class.java)

        val again = rest.postForEntity(PATH, request(TOKEN), Map::class.java).body!!["data"] as Map<*, *>
        assertEquals("SKIPPED", again["result"])
        assertEquals("EXISTS", again["reason"])

        val forced = rest.postForEntity("$PATH?force=true", request(TOKEN), Map::class.java).body!!["data"] as Map<*, *>
        assertEquals("GENERATED", forced["result"])
    }

    @Test
    fun `요청 날짜가 기준일과 다르면 건너뛴다`() {
        val data = rest.postForEntity("$PATH?date=2026-01-02", request(TOKEN), Map::class.java).body!!["data"] as Map<*, *>

        assertEquals("SKIPPED", data["result"])
        assertEquals("DATE_MISMATCH", data["reason"])
        assertEquals(BriefingSeed.BASE_DATE, data["baseDate"])
    }

    @Test
    fun `날짜 형식이 틀리면 400`() {
        val response = rest.postForEntity("$PATH?date=20260102", request(TOKEN), String::class.java)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `LLM 이 꺼져 있으면 건너뛴다`() {
        clearBriefings()
        writer.enabled = false
        try {
            val data = rest.postForEntity(PATH, request(TOKEN), Map::class.java).body!!["data"] as Map<*, *>

            assertEquals("SKIPPED", data["result"])
            assertEquals("LLM_DISABLED", data["reason"])
            assertNull(store.findLatest())
        } finally {
            writer.enabled = true
        }
    }

    private fun request(token: String) = HttpEntity<Void>(
        HttpHeaders().apply { add(InternalTokenFilter.TOKEN_HEADER, token) },
    )

    private fun clearBriefings() {
        val app = TestContainers.appPostgres
        DriverManager.getConnection(app.jdbcUrl, app.username, app.password).use { connection ->
            connection.createStatement().use { it.execute("DELETE FROM daily_briefings") }
        }
    }

    companion object {
        private const val PATH = "/internal/briefings/generate"
        private const val TOKEN = "test-internal-token"

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            TestContainers.register(registry)
            registry.add("app.internal.token") { TOKEN }
        }
    }
}
