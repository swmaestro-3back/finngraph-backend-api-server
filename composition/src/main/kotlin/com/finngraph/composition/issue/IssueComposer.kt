package com.finngraph.composition.issue

import com.finngraph.news.model.IssueArticle
import com.finngraph.news.model.IssueCluster
import com.finngraph.news.model.IssueMention
import com.finngraph.news.model.NewsId
import com.finngraph.news.model.PageResult
import com.finngraph.news.port.NewsCompanyPort
import com.finngraph.news.port.NewsIssuePort
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockQueryPort
import com.finngraph.theme.model.ThemeId
import com.finngraph.theme.port.ThemeStockPort
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

@Component
class IssueComposer(
    private val issues: NewsIssuePort,
    private val newsCompany: NewsCompanyPort,
    private val stockQuery: StockQueryPort,
    private val themeStock: ThemeStockPort,
    private val clock: Clock = Clock.system(IssueRules.KST),
) {

    fun page(date: LocalDate?, sort: IssueSort, page: Int, size: Int): IssuePage {
        val day = date ?: latestDay()
            ?: return IssuePage(null, null, null, PageResult(emptyList(), page, size, 0))

        val window = IssueRules.dayWindow(day)
        val clusters = issues.findPublishedBetween(window.start, window.end)
        val articles = issues.findArticles(clusters.map { it.id })

        val ranked = summaries(clusters, articles).sortedWith(IssueRules.order(sort))
        val slice = ranked.pageSlice(page, size)

        val mentions = mentions(slice.flatMap { articles[it.id].orEmpty() })
        val content = slice.map { it.copy(companies = mentions.companies(articles[it.id].orEmpty(), LIST_COMPANY_LIMIT)) }

        val neighbors = issues.findNeighbors(window.start, window.end)
        return IssuePage(
            date = day,
            prevDate = IssueRules.adjacentDate(neighbors.latestBefore),
            nextDate = IssueRules.adjacentDate(neighbors.earliestFrom),
            result = PageResult(content, page, size, ranked.size.toLong()),
        )
    }

    fun detail(id: Long): IssueDetail? {
        val cluster = issues.findById(id) ?: return null
        val articles = issues.findArticles(listOf(id))[id]?.takeIf { it.isNotEmpty() } ?: return null

        return IssueDetail(
            issue = summarize(cluster, articles).copy(companies = mentions(articles).companies(articles, null)),
            articles = articles.map { it.toView() },
        )
    }

    fun stockPage(ticker: Ticker, from: LocalDate?, to: LocalDate?, page: Int, size: Int): PageResult<StockIssue>? {
        if (!stockQuery.exists(ticker)) return null

        val window = IssueRules.window(IssueRules.stockRange(from, to, LocalDate.now(clock)))
        val ranked = issues.findMentionedBetween(listOf(ticker.value), window.start, window.end)
            .sortedWith(IssueRules.mentionOrder())
        val slice = ranked.pageSlice(page, size)
        val built = stockIssues(slice)

        return PageResult(slice.mapNotNull(built::get), page, size, ranked.size.toLong())
    }

    fun latestByStocks(tickers: List<String>): List<LatestStockIssue> {
        val active = stockQuery.findNamesByTickers(tickers.map(::Ticker)).keys.map { it.value }
        val window = IssueRules.window(IssueRules.latestRange(LocalDate.now(clock)))
        val picks = issues.findMentionedBetween(active, window.start, window.end)
            .groupBy { it.ticker }
            .mapValues { (_, found) -> found.minWith(IssueRules.mentionOrder()) }
        val built = stockIssues(picks.values.toList())

        return tickers.map { ticker -> LatestStockIssue(ticker, picks[ticker]?.let(built::get)) }
    }

    fun themeBoard(themeIds: List<ThemeId>, date: LocalDate?): ThemeIssueBoard {
        val day = date ?: latestDay()
            ?: return ThemeIssueBoard(null, themeIds.map { ThemeIssues(it.value, 0, emptyList(), emptyList()) })

        val window = IssueRules.dayWindow(day)
        val clusters = issues.findPublishedBetween(window.start, window.end)
        val articles = issues.findArticles(clusters.map { it.id })
        val ranked = summaries(clusters, articles).sortedWith(IssueRules.order(IssueSort.MEDIA))

        val mentions = mentions(ranked.flatMap { articles[it.id].orEmpty() })
        val mentioned = ranked.associate { issue ->
            issue.id to mentions.companies(articles[issue.id].orEmpty(), null).mapTo(HashSet()) { it.ticker }
        }
        val members = themeStock.findTickers(themeIds)

        val themes = themeIds.map { themeId ->
            val tickers = members[themeId].orEmpty()
            val matched = ranked.filter { issue -> tickers.any { it in mentioned.getValue(issue.id) } }
            ThemeIssues(
                themeId = themeId.value,
                issueCount = matched.size,
                issueIds = matched.map { it.id },
                issues = matched.take(THEME_ISSUE_LIMIT).map {
                    it.copy(companies = mentions.companies(articles[it.id].orEmpty(), LIST_COMPANY_LIMIT))
                },
            )
        }
        return ThemeIssueBoard(day, themes)
    }

    fun timeline(id: Long, limit: Int): IssueTimeline? {
        val chain = issues.findChain(id, TIMELINE_MAX_DEPTH)
        val groups = IssueRules.timelineGroups(chain)
        val trees = issues.findSameEventTrees(groups.map { it.last().clusterId }, TIMELINE_MAX_DEPTH)
        val events = IssueRules.timelineEvents(groups, trees)
        val ids = events.flatten().map { it.clusterId }
        val articles = issues.findArticles(ids)
        if (articles[id].isNullOrEmpty()) return null

        // 노드는 제목(클러스터 제목)이 붙고 공개 기사가 있는 이슈만이다. 못 되는 칸은 건너뛰되 사슬은 끝까지 따라간다.
        val titled = summaries(issues.findByIds(ids), articles)
            .filter { it.titleSource == IssueTitleSource.CLUSTER }
            .associateBy { it.id }
        val nodes = events
            .map { event -> event.mapNotNull { link -> titled[link.clusterId]?.let { TimelineMember(it, link.originalSize) } } }
            .filter { it.isNotEmpty() }
            .take(limit)

        val points = issues.findChangePoints(nodes.flatten().map { it.issue.representativeNewsId })
        return IssueTimeline(id, nodes.map { timelineNode(id, it, points) })
    }

    // 제목은 대표 이슈의 것을 쓰고, 요약은 대표 순서대로 처음 나오는 것을 쓴다. 같은 사건이라 다른 이슈의 요약도 맞고,
    // 대표 이슈의 대표 기사가 아직 공개 전이면 요약 없는 가장 이른 공개 기사로 대신해 요약이 비는 일이 잦기 때문이다.
    private fun timelineNode(id: Long, node: List<TimelineMember>, points: Map<Long, String>): IssueTimelineNode {
        val byLead = IssueRules.timelineLeadOrder(node)
        val lead = byLead.first().issue
        val members = node.map { it.issue }
        val first = members.mapNotNull { it.firstPublishedAt }.minOrNull()
        return IssueTimelineNode(
            issueId = lead.id,
            title = requireNotNull(lead.title),
            summary = byLead.firstNotNullOfOrNull { points[it.issue.representativeNewsId] ?: IssueRules.firstSentence(it.issue.summary) },
            date = first?.let(IssueRules::kstDate),
            firstPublishedAt = first,
            lastPublishedAt = members.mapNotNull { it.lastPublishedAt }.maxOrNull(),
            current = members.any { it.id == id },
            mergedIssueIds = members.map { it.id },
        )
    }

    private fun latestDay(): LocalDate? = issues.findLatestPublishedAt()?.let(IssueRules::kstDate)

    private fun stockIssues(picks: List<IssueMention>): Map<IssueMention, StockIssue> {
        val ids = picks.map { it.clusterId }.distinct()
        val articles = issues.findArticles(ids)
        val summaries = summaries(issues.findByIds(ids), articles).associateBy { it.id }
        val mentions = mentions(summaries.keys.flatMap { articles[it].orEmpty() })

        return picks.mapNotNull { pick ->
            val summary = summaries[pick.clusterId] ?: return@mapNotNull null
            val issueArticles = articles[pick.clusterId].orEmpty()
            pick to StockIssue(
                issue = summary.copy(companies = mentions.companies(issueArticles, LIST_COMPANY_LIMIT)),
                mentionCount = mentions.mentionCount(issueArticles, pick.ticker),
            )
        }.toMap()
    }

    private fun summaries(clusters: List<IssueCluster>, articles: Map<Long, List<IssueArticle>>): List<IssueSummary> =
        clusters.mapNotNull { cluster -> articles[cluster.id]?.takeIf { it.isNotEmpty() }?.let { summarize(cluster, it) } }

    private fun summarize(cluster: IssueCluster, articles: List<IssueArticle>): IssueSummary {
        val representative = requireNotNull(IssueRules.representative(cluster.representativeNewsId, articles))
        val title = IssueRules.title(cluster.title, representative, articles)
        val span = IssueRules.span(articles)
        return IssueSummary(
            id = cluster.id,
            title = title.text,
            titleSource = title.source,
            articleCount = articles.size,
            mediaCount = IssueRules.mediaCount(articles),
            firstPublishedAt = span.first,
            lastPublishedAt = span.last,
            keywords = cluster.keywords,
            summary = representative.summary,
            representativeNewsId = representative.id,
            companies = emptyList(),
        )
    }

    private fun mentions(articles: List<IssueArticle>): Mentions {
        val refs = newsCompany.findByNewsIds(articles.map { NewsId(it.id) }.distinct())
        val tickersByArticle = refs.entries.associate { (newsId, companies) ->
            newsId.value to companies.mapNotNull { it.ticker?.takeIf(String::isNotBlank) }
        }
        val tickers = tickersByArticle.values.flatten().distinct().map(::Ticker)
        val names = stockQuery.findNamesByTickers(tickers).mapKeys { it.key.value }
        return Mentions(tickersByArticle, names)
    }

    private class Mentions(
        private val tickersByArticle: Map<Long, List<String>>,
        private val names: Map<String, String>,
    ) {
        fun companies(articles: List<IssueArticle>, limit: Int?): List<IssueCompany> =
            IssueRules.companies(articles.map { tickersByArticle[it.id].orEmpty() }, names, limit)

        fun mentionCount(articles: List<IssueArticle>, ticker: String): Int =
            companies(articles, null).firstOrNull { it.ticker == ticker }?.mentionCount ?: 0
    }

    private fun <T> List<T>.pageSlice(page: Int, size: Int): List<T> {
        val offset = page.toLong() * size
        return if (offset >= this.size) emptyList() else subList(offset.toInt(), minOf(this.size, offset.toInt() + size))
    }

    private fun IssueArticle.toView() = IssueArticleView(
        id = id,
        title = title,
        url = url,
        press = IssueRules.press(url),
        publishedAt = publishedAt,
        summary = summary,
        tripleExtracted = tripleExtracted,
    )

    companion object {
        const val LIST_COMPANY_LIMIT = 5
        const val THEME_ISSUE_LIMIT = 3
        const val TIMELINE_MAX_LIMIT = 20
        const val TIMELINE_MAX_DEPTH = 100
    }
}
