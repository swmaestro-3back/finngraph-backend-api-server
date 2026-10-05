package com.finngraph.composition.issue

import com.finngraph.news.model.IssueArticle
import com.finngraph.news.model.IssueCluster
import com.finngraph.news.model.NewsId
import com.finngraph.news.model.PageResult
import com.finngraph.news.port.NewsCompanyPort
import com.finngraph.news.port.NewsIssuePort
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockQueryPort
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class IssueComposer(
    private val issues: NewsIssuePort,
    private val newsCompany: NewsCompanyPort,
    private val stockQuery: StockQueryPort,
) {

    fun page(date: LocalDate?, sort: IssueSort, page: Int, size: Int): IssuePage {
        val day = date ?: issues.findLatestPublishedAt()?.let(IssueRules::kstDate)
            ?: return IssuePage(null, null, null, PageResult(emptyList(), page, size, 0))

        val window = IssueRules.dayWindow(day)
        val clusters = issues.findPublishedBetween(window.start, window.end)
        val articles = issues.findArticles(clusters.map { it.id })

        val ranked = clusters
            .mapNotNull { cluster -> articles[cluster.id]?.takeIf { it.isNotEmpty() }?.let { summarize(cluster, it) } }
            .sortedWith(IssueRules.order(sort))
        val offset = page.toLong() * size
        val slice = if (offset >= ranked.size) emptyList() else ranked.subList(offset.toInt(), minOf(ranked.size, offset.toInt() + size))

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
    }
}
