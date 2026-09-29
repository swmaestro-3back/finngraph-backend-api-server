package com.finngraph.briefing

import com.finngraph.briefing.model.BriefingStatus
import com.finngraph.briefing.port.BriefingStorePort
import com.finngraph.support.BriefingFixtures
import com.finngraph.support.TestContainers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.sql.DriverManager
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
class BriefingStoreTest {

    @Autowired
    lateinit var store: BriefingStorePort

    @AfterEach
    fun cleanup() {
        val app = TestContainers.appPostgres
        DriverManager.getConnection(app.jdbcUrl, app.username, app.password).use { connection ->
            connection.createStatement().use { it.execute("DELETE FROM daily_briefings WHERE base_date >= '2026-07-01'") }
        }
    }

    @Test
    fun `저장한 브리핑을 같은 값으로 다시 읽는다`() {
        val briefing = BriefingFixtures.sample()

        store.upsert(briefing)

        assertEquals(briefing, store.findByDate(briefing.baseDate))
        assertEquals(briefing, store.findLatest())
    }

    @Test
    fun `같은 기준일에 다시 저장하면 덮어쓴다`() {
        val first = BriefingFixtures.sample()
        val second = first.copy(status = BriefingStatus.PARTIAL, headline = null, issues = emptyList())

        store.upsert(first)
        store.upsert(second)

        assertEquals(second, store.findByDate(first.baseDate))
        assertEquals(1, store.listSummaries(10).count { it.baseDate == first.baseDate })
    }

    @Test
    fun `직전 브리핑은 기준일보다 앞선 최신 행이다`() {
        val d1 = BriefingFixtures.sample(LocalDate.parse("2026-07-29"))
        val d2 = BriefingFixtures.sample(LocalDate.parse("2026-07-30"))
        val d3 = BriefingFixtures.sample(LocalDate.parse("2026-07-31"))
        listOf(d1, d2, d3).forEach(store::upsert)

        assertEquals(d2.baseDate, assertNotNull(store.findPreviousBefore(d3.baseDate)).baseDate)
        assertNull(store.findPreviousBefore(d1.baseDate))
    }

    @Test
    fun `요약 목록은 최신순이고 헤드라인 문장만 담는다`() {
        val d1 = BriefingFixtures.sample(LocalDate.parse("2026-07-29"))
        val d2 = BriefingFixtures.sample(LocalDate.parse("2026-07-30")).copy(headline = null)
        listOf(d1, d2).forEach(store::upsert)

        val summaries = store.listSummaries(2)

        assertEquals(listOf(d2.baseDate, d1.baseDate), summaries.map { it.baseDate })
        assertNull(summaries[0].headline)
        assertEquals(d1.headline?.text, summaries[1].headline)
        assertEquals(BriefingStatus.READY, summaries[1].status)
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
