package com.finngraph.composition.issue

import com.finngraph.news.model.IssueArticle
import com.finngraph.news.model.IssueMention
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

enum class IssueSort { MEDIA, RECENT }

enum class IssueTitleSource { CLUSTER, ARTICLE }

data class IssueTitle(val text: String?, val source: IssueTitleSource)

data class DayWindow(val start: OffsetDateTime, val end: OffsetDateTime)

data class DateRange(val from: LocalDate, val to: LocalDate)

data class PublishedSpan(val first: OffsetDateTime?, val last: OffsetDateTime?)

object IssueRules {

    val KST: ZoneOffset = ZoneOffset.ofHours(9)

    const val STOCK_RANGE_DAYS = 365L
    const val LATEST_RANGE_DAYS = 30L

    private val HOST = Regex("^[A-Za-z][A-Za-z0-9+.-]*://(?:[^/?#@]*@)?([^/?#:]+)")
    private const val WWW = "www."

    private val EARLIEST: Comparator<IssueArticle> =
        compareBy<IssueArticle, OffsetDateTime?>(nullsLast()) { it.publishedAt }.thenBy { it.id }

    private fun <T> latestFirst(lastPublishedAt: (T) -> OffsetDateTime?, id: (T) -> Long): Comparator<T> =
        compareBy(nullsLast(reverseOrder()), lastPublishedAt).thenByDescending(id)

    private val BY_RECENT: Comparator<IssueSummary> = latestFirst({ it.lastPublishedAt }, { it.id })

    private val BY_MEDIA: Comparator<IssueSummary> =
        compareByDescending<IssueSummary> { it.mediaCount }
            .thenByDescending { it.articleCount }
            .then(BY_RECENT)

    private val MENTION_BY_RECENT: Comparator<IssueMention> = latestFirst({ it.lastPublishedAt }, { it.clusterId })

    fun dayWindow(date: LocalDate): DayWindow = window(DateRange(date, date))

    fun window(range: DateRange): DayWindow =
        DayWindow(range.from.atStartOfDay().atOffset(KST), range.to.plusDays(1).atStartOfDay().atOffset(KST))

    fun stockRange(from: LocalDate?, to: LocalDate?, today: LocalDate): DateRange {
        val end = to ?: today
        return DateRange(from ?: end.minusDays(STOCK_RANGE_DAYS), end)
    }

    fun latestRange(today: LocalDate): DateRange = DateRange(today.minusDays(LATEST_RANGE_DAYS), today)

    fun kstDate(at: OffsetDateTime): LocalDate = at.atZoneSameInstant(KST).toLocalDate()

    fun adjacentDate(nearestPublishedAt: OffsetDateTime?): LocalDate? = nearestPublishedAt?.let(::kstDate)

    fun span(articles: List<IssueArticle>): PublishedSpan {
        val times = articles.mapNotNull { it.publishedAt }
        return PublishedSpan(times.minOrNull(), times.maxOrNull())
    }

    fun press(url: String?): String? {
        val host = url?.let { HOST.find(it.trim()) }?.groupValues?.get(1)?.lowercase() ?: return null
        return host.removePrefix(WWW).takeIf { it.isNotBlank() }
    }

    fun mediaCount(articles: List<IssueArticle>): Int =
        articles.mapNotNullTo(HashSet()) { press(it.url) }.size

    fun representative(representativeNewsId: Long?, articles: List<IssueArticle>): IssueArticle? =
        articles.firstOrNull { it.id == representativeNewsId } ?: articles.minWithOrNull(EARLIEST)

    fun title(clusterTitle: String?, representative: IssueArticle?, articles: List<IssueArticle>): IssueTitle {
        clusterTitle?.takeIf { it.isNotBlank() }?.let { return IssueTitle(it, IssueTitleSource.CLUSTER) }
        val fromArticle = representative?.title?.takeIf { it.isNotBlank() }
            ?: articles.sortedWith(EARLIEST).firstNotNullOfOrNull { it.title?.takeIf(String::isNotBlank) }
        return IssueTitle(fromArticle, IssueTitleSource.ARTICLE)
    }

    fun companies(articleTickers: List<Collection<String>>, names: Map<String, String>, limit: Int?): List<IssueCompany> {
        val ranked = articleTickers
            .flatMap { it.toSet() }
            .groupingBy { it }
            .eachCount()
            .mapNotNull { (ticker, count) -> names[ticker]?.let { IssueCompany(ticker, it, count) } }
            .sortedWith(compareByDescending<IssueCompany> { it.mentionCount }.thenBy { it.ticker })
        return if (limit == null) ranked else ranked.take(limit)
    }

    fun order(sort: IssueSort): Comparator<IssueSummary> = when (sort) {
        IssueSort.MEDIA -> BY_MEDIA
        IssueSort.RECENT -> BY_RECENT
    }

    fun mentionOrder(): Comparator<IssueMention> = MENTION_BY_RECENT
}
