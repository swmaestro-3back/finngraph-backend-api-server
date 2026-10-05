package com.finngraph.composition.issue

import com.finngraph.news.model.IssueArticle
import com.finngraph.news.model.IssueChainLink
import com.finngraph.news.model.IssueLinkRelation
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

    // 같은 사건으로 합친 이슈는 ETL 이 부모를 고르는 순서(클러스터 첫 기사 시각, 같으면 id)의 역순, 곧 최신순으로 둔다.
    private val EVENT_LATEST_FIRST: Comparator<IssueChainLink> = latestFirst({ it.startedAt }, { it.clusterId })

    private val TIMELINE_LEAD: Comparator<TimelineMember> =
        compareByDescending<TimelineMember> { it.originalSize }
            .thenBy(nullsLast()) { it.issue.firstPublishedAt }
            .thenBy { it.issue.id }

    // 문장 끝 부호 뒤에 공백이나 끝이 와야 문장 끝으로 본다. 0.9% 같은 소수점에서 끊지 않기 위해서다.
    private val SENTENCE_END = Regex("[.!?]['\"’”)\\]]*(?=\\s|$)")

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

    // 부모 사슬을 같은 사건 묶음으로 나눈다. 앞 칸이 부모와 SAME_EVENT 로 이어졌으면 부모도 같은 묶음이다.
    // 노드가 못 되는 칸도 묶음 경계를 정하는 데는 쓰므로 거르기 전 사슬 전체로 나눈다.
    fun timelineGroups(chain: List<IssueChainLink>): List<List<IssueChainLink>> {
        val groups = mutableListOf<MutableList<IssueChainLink>>()
        chain.forEachIndexed { index, link ->
            if (index > 0 && chain[index - 1].relation == IssueLinkRelation.SAME_EVENT) groups.last().add(link)
            else groups.add(mutableListOf(link))
        }
        return groups
    }

    // 묶음을 같은 사건 전체로 넓힌다. 사슬은 위로만 올라가므로 묶음 맨 위 이슈의 same_event 자손(trees)을 더해야
    // 사건의 어느 이슈를 요청해도 같은 노드가 나온다. 앞(최신) 묶음에 이미 든 이슈는 뺀다(순환 같은 잘못된 데이터 대비).
    fun timelineEvents(
        groups: List<List<IssueChainLink>>,
        trees: Map<Long, List<IssueChainLink>>,
    ): List<List<IssueChainLink>> {
        val seen = HashSet<Long>()
        return groups.map { group ->
            (group + trees[group.last().clusterId].orEmpty())
                .filter { seen.add(it.clusterId) }
                .sortedWith(EVENT_LATEST_FIRST)
        }
    }

    // 맨 앞이 노드 대표다. 대표에게 없는 값(요약)은 이 순서로 다음 이슈에서 찾는다.
    fun timelineLeadOrder(members: List<TimelineMember>): List<TimelineMember> = members.sortedWith(TIMELINE_LEAD)

    fun firstSentence(text: String?): String? {
        val trimmed = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val end = SENTENCE_END.find(trimmed)?.range?.last ?: return trimmed
        return trimmed.substring(0, end + 1)
    }
}
