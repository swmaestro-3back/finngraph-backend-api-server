package com.finngraph.briefing

import com.finngraph.news.port.NewsClusterPort
import com.finngraph.news.port.RelationSourcePort
import com.finngraph.stock.port.StockContractPort
import com.finngraph.stock.port.StockQueryPort
import com.finngraph.support.BriefingSeed
import com.finngraph.support.TestContainers
import com.finngraph.theme.port.ThemeQueryPort
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BriefingPortsTest {

    @Autowired
    lateinit var clusters: NewsClusterPort

    @Autowired
    lateinit var relations: RelationSourcePort

    @Autowired
    lateinit var stocks: StockQueryPort

    @Autowired
    lateinit var themes: ThemeQueryPort

    @Autowired
    lateinit var contracts: StockContractPort

    @BeforeAll
    fun seed() = BriefingSeed.seed()

    @AfterAll
    fun cleanup() = BriefingSeed.cleanup()

    private val since = OffsetDateTime.of(LocalDate.parse(BriefingSeed.PREV_DATE).atTime(18, 0), ZoneOffset.ofHours(9))

    @Test
    fun `창 안의 3건 이상 클러스터만 기사 수 순으로 돌려준다`() {
        val found = clusters.findRecent(since, minMembers = 3, limit = 5)

        assertEquals(listOf(BriefingSeed.HOT_CLUSTER), found.map { it.id })
        val hot = found.single()
        assertEquals("시드 이슈 클러스터", hot.title)
        assertEquals(listOf("배터리", "공급"), hot.keywords)
        assertEquals(3, hot.memberCount)
    }

    @Test
    fun `기준을 2건으로 낮추면 소형 클러스터가 뒤에 붙는다`() {
        val found = clusters.findRecent(since, minMembers = 2, limit = 5)

        assertEquals(listOf(BriefingSeed.HOT_CLUSTER, BriefingSeed.SMALL_CLUSTER), found.map { it.id })
    }

    @Test
    fun `클러스터 기사는 최신순으로 개수를 제한하고 요약만 싣는다`() {
        val articles = clusters.findArticles(listOf(BriefingSeed.HOT_CLUSTER, BriefingSeed.SMALL_CLUSTER), perCluster = 2)

        val hot = articles.getValue(BriefingSeed.HOT_CLUSTER)
        assertEquals(listOf(BriefingSeed.NEWS_C, BriefingSeed.NEWS_B), hot.map { it.newsId })
        assertNull(hot[0].summary)
        assertEquals("https://seed.test/news/${BriefingSeed.NEWS_B}", hot[1].url)
        assertEquals(listOf(BriefingSeed.NEWS_D), articles.getValue(BriefingSeed.SMALL_CLUSTER).map { it.newsId })
    }

    @Test
    fun `원문 링크가 있으면 원문을 우선한다`() {
        val a = clusters.findArticles(listOf(BriefingSeed.HOT_CLUSTER), perCluster = 5)
            .getValue(BriefingSeed.HOT_CLUSTER)
            .single { it.newsId == BriefingSeed.NEWS_A }

        assertEquals("https://press.test/a", a.url)
    }

    @Test
    fun `관계 원장은 창 안의 뉴스·공시 근거를 모두 돌려준다`() {
        val rows = relations.findMentionedBetween(
            fromExclusive = LocalDate.parse(BriefingSeed.PREV_DATE),
            toInclusive = LocalDate.parse(BriefingSeed.BASE_DATE),
        )

        assertEquals(
            listOf(BriefingSeed.REL_SUPPLY, BriefingSeed.REL_PLANNED, BriefingSeed.REL_TERMINATED),
            rows.map { it.id }.sorted(),
        )
        val supply = rows.single { it.id == BriefingSeed.REL_SUPPLY }
        assertEquals(BriefingSeed.NEWS_A, supply.newsId)
        assertEquals(BriefingSeed.SUPPLIER_TICKER, supply.subjectCode)
        assertEquals("배터리", supply.item)
        val terminated = rows.single { it.id == BriefingSeed.REL_TERMINATED }
        assertEquals(BriefingSeed.CORRECTION_RCEPT, terminated.rceptNo)
        assertEquals("terminated", terminated.polarity)
    }

    @Test
    fun `플래그 종목은 활성 종목 중 관리·정지·정리매매만 티커순으로 돌려준다`() {
        val flagged = stocks.findFlagged().filter { it.ticker.startsWith("9393") }

        assertEquals(
            listOf(BriefingSeed.ADMIN_TICKER, BriefingSeed.SUSPENDED_TICKER, BriefingSeed.DELISTING_TICKER),
            flagged.map { it.ticker },
        )
        assertTrue(flagged[0].underAdministration)
        assertTrue(flagged[1].tradingSuspended)
        assertTrue(flagged[2].delistingTrade)
    }

    @Test
    fun `종료일이 30일 안인 계약은 접수일과 무관하게 찾는다`() {
        val base = LocalDate.parse(BriefingSeed.BASE_DATE)

        val ending = contracts.findEndingBetween(base, base.plusDays(30)).filter { it.rceptNo.startsWith("9998") }

        assertEquals(listOf(BriefingSeed.CORRECTION_RCEPT), ending.map { it.rceptNo })
    }

    @Test
    fun `기준일과 전 거래일을 테마 도메인 정의로 준다`() {
        val basis = themes.pricingBasis()

        assertEquals(LocalDate.parse(BriefingSeed.BASE_DATE), basis.baseDate)
        assertEquals(LocalDate.parse(BriefingSeed.PREV_DATE), basis.prevTradingDate)
    }

    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = TestContainers.register(registry)
    }
}
