package com.finngraph.composition.issue

import com.finngraph.news.model.IssueArticle
import com.finngraph.news.model.IssueCluster
import com.finngraph.news.model.IssueMention
import com.finngraph.news.model.NewsId
import com.finngraph.news.model.PageResult
import com.finngraph.news.port.NewsCompanyPort
import com.finngraph.news.port.NewsIssuePort
import com.finngraph.news.port.RelationSourcePort
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
    private val relationSources: RelationSourcePort,
    private val clock: Clock = Clock.system(IssueRules.KST),
) {

    fun page(date: LocalDate?, sort: IssueSort, page: Int, size: Int): IssuePage {
        val day = date ?: latestDay()
            ?: return IssuePage(null, null, null, PageResult(emptyList(), page, size, 0))

        val window = IssueRules.dayWindow(day)
        val events = sameEvents(issues.findPublishedBetween(window.start, window.end))
        val articles = mergedArticles(events)

        val ranked = summaries(events.map { it.lead }, articles).sortedWith(IssueRules.order(sort))
        val slice = ranked.pageSlice(page, size)

        val mentions = mentions(slice.flatMap { articles[it.id].orEmpty() })
        val positions = positions(slice)
        val content = slice.map {
            it.copy(
                companies = mentions.companies(articles[it.id].orEmpty(), LIST_COMPANY_LIMIT),
                timelinePosition = positions[it.id] ?: 1,
            )
        }

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
        val memberIds = eventMembers(id)
        val found = issues.findArticles(memberIds)
        if (found[id].isNullOrEmpty()) return null
        val articles = IssueRules.mergeArticles(memberIds.map { found[it].orEmpty() })

        val summary = summarize(cluster, articles)
        val sources = relationSources.findByNews(articles.map { it.id })
        val mentions = mentions(articles)
        val companies = mentions.companies(articles, null)
        val roles = IssueRules.roles(companies.map { it.ticker }, sources, summary.representativeNewsId)
        val issue = summary.copy(
            companies = companies.map { it.copy(role = roles[it.ticker]) },
            timelinePosition = positions(listOf(summary))[id] ?: 1,
        )
        return IssueDetail(
            issue = issue,
            summaryPoints = articles.firstOrNull { it.id == issue.representativeNewsId }?.summaryPoints.orEmpty(),
            articles = articles.map { it.toView().copy(tickers = mentions.listed(it)) },
        )
    }

    fun stockPage(ticker: Ticker, from: LocalDate?, to: LocalDate?, page: Int, size: Int): PageResult<StockIssue>? {
        if (!stockQuery.exists(ticker)) return null

        val window = IssueRules.window(IssueRules.stockRange(from, to, LocalDate.now(clock)))
        val mentioned = issues.findMentionedBetween(listOf(ticker.value), window.start, window.end)
        val events = eventsOf(mentioned.map { it.clusterId })
        val ranked = byEvent(mentioned, events).sortedWith(IssueRules.mentionOrder())
        val slice = ranked.pageSlice(page, size)
        val built = stockIssues(slice, events)

        return PageResult(slice.mapNotNull(built::get), page, size, ranked.size.toLong())
    }

    fun stockFlows(ticker: Ticker, from: LocalDate?, to: LocalDate?): List<StockIssueFlow>? {
        if (!stockQuery.exists(ticker)) return null

        val window = IssueRules.window(IssueRules.flowRange(from, to, LocalDate.now(clock)))
        val mentioned = issues.findMentionedBetween(listOf(ticker.value), window.start, window.end)
        val events = eventsOf(mentioned.map { it.clusterId })
        val picks = byEvent(mentioned, events)
        val rootOf = picks.associate { pick ->
            val lead = events.getValue(pick.clusterId).lead
            pick.clusterId to (lead.storyRootId ?: lead.id)
        }
        val flowing = rootOf.values.groupingBy { it }.eachCount().filterValues { it >= IssueRules.MIN_FLOW_ISSUES }.keys
        val built = stockIssues(picks.filter { rootOf[it.clusterId] in flowing }, events).values
        val latest = issues.findLatestPublishedAt()
        return built
            .groupBy { it.issue.storyRootId ?: it.issue.id }
            .filterValues { it.size >= IssueRules.MIN_FLOW_ISSUES }
            .map { (root, found) ->
                val ordered = found.sortedWith(
                    compareBy(nullsLast()) { it: StockIssue -> it.issue.firstPublishedAt }.thenBy { it.issue.id },
                )
                val last = ordered.mapNotNull { it.issue.lastPublishedAt }.maxOrNull()
                StockIssueFlow(root, ordered.firstNotNullOfOrNull { it.storyTitle }, ordered, IssueRules.ongoing(last, latest))
            }
            .sortedWith(
                compareByDescending(nullsLast()) { flow: StockIssueFlow ->
                    flow.issues.mapNotNull { it.issue.lastPublishedAt }.maxOrNull()
                }.thenByDescending { it.id },
            )
    }

    fun search(query: String, limit: Int): List<IssueSummary> {
        val events = sameEvents(issues.search(query, limit))
        val articles = mergedArticles(events)
        val found = summaries(events.map { it.lead }, articles)
        val mentions = mentions(found.flatMap { articles[it.id].orEmpty() })
        val positions = positions(found)
        return found.map {
            it.copy(
                companies = mentions.companies(articles[it.id].orEmpty(), LIST_COMPANY_LIMIT),
                timelinePosition = positions[it.id] ?: 1,
            )
        }
    }

    fun latestByStocks(tickers: List<String>): List<LatestStockIssue> {
        val active = stockQuery.findNamesByTickers(tickers.map(::Ticker)).keys.map { it.value }
        val window = IssueRules.window(IssueRules.latestRange(LocalDate.now(clock)))
        val mentioned = issues.findMentionedBetween(active, window.start, window.end)
        val events = eventsOf(mentioned.map { it.clusterId })
        val picks = byEvent(mentioned, events)
            .groupBy { it.ticker }
            .mapValues { (_, found) -> found.minWith(IssueRules.mentionOrder()) }
        val built = stockIssues(picks.values.toList(), events)

        return tickers.map { ticker -> LatestStockIssue(ticker, picks[ticker]?.let(built::get)) }
    }

    fun themeBoard(themeIds: List<ThemeId>, date: LocalDate?): ThemeIssueBoard {
        val day = date ?: latestDay()
            ?: return ThemeIssueBoard(null, themeIds.map { ThemeIssues(it.value, 0, emptyList(), emptyList()) })

        val window = IssueRules.dayWindow(day)
        val events = sameEvents(issues.findPublishedBetween(window.start, window.end))
        val articles = mergedArticles(events)
        val ranked = summaries(events.map { it.lead }, articles).sortedWith(IssueRules.order(IssueSort.MEDIA))

        val mentions = mentions(ranked.flatMap { articles[it.id].orEmpty() })
        val mentioned = ranked.associate { issue ->
            issue.id to mentions.companies(articles[issue.id].orEmpty(), null).mapTo(HashSet()) { it.ticker }
        }
        val members = themeStock.findTickers(themeIds)

        val matches = themeIds.associateWith { themeId ->
            val tickers = members[themeId].orEmpty()
            ranked.filter { issue -> tickers.any { it in mentioned.getValue(issue.id) } }
        }
        val positions = positions(matches.values.flatMap { it.take(THEME_ISSUE_LIMIT) }.distinctBy { it.id })
        val themes = themeIds.map { themeId ->
            val matched = matches.getValue(themeId)
            ThemeIssues(
                themeId = themeId.value,
                issueCount = matched.size,
                issueIds = matched.map { it.id },
                issues = matched.take(THEME_ISSUE_LIMIT).map {
                    it.copy(
                        companies = mentions.companies(articles[it.id].orEmpty(), LIST_COMPANY_LIMIT),
                        timelinePosition = positions[it.id] ?: 1,
                    )
                },
            )
        }
        return ThemeIssueBoard(day, themes)
    }

    fun timeline(id: Long, limit: Int): IssueTimeline? {
        val chain = issues.findChain(id, TIMELINE_MAX_DEPTH)
        val groups = IssueRules.timelineGroups(chain)
        val trees = issues.findSameEventTrees(groups.map { it.last().clusterId }, TIMELINE_MAX_DEPTH)
        val past = IssueRules.timelineEvents(groups, trees)
        val known = past.flatten().mapTo(HashSet()) { it.clusterId }
        val current = past.firstOrNull().orEmpty().map { it.clusterId }
        val events = IssueRules.followUpEvents(known, issues.findDescendants(current, TIMELINE_MAX_DEPTH)) + past
        val ids = events.flatten().map { it.clusterId }
        val articles = issues.findArticles(ids)
        if (articles[id].isNullOrEmpty()) return null

        val titled = summaries(issues.findByIds(ids), articles)
            .filter { it.titleSource == IssueTitleSource.CLUSTER }
            .associateBy { it.id }
        val all = events
            .map { event -> event.mapNotNull { link -> titled[link.clusterId]?.let { TimelineMember(it, link.originalSize) } } }
            .filter { it.isNotEmpty() }
        val center = all.indexOfFirst { node -> node.any { it.issue.id == id } }.takeIf { it >= 0 }
            ?: all.indexOfFirst { node -> node.all { it.issue.id in known } }.takeIf { it >= 0 }
            ?: 0
        val start = (center - (limit - 1) / 2).coerceIn(0, maxOf(0, all.size - limit))
        val nodes = all.drop(start).take(limit)

        val points = issues.findChangePoints(nodes.flatten().map { it.issue.representativeNewsId })
        val nodeArticles = nodes.map { node -> node.flatMap { articles[it.issue.id].orEmpty() } }
        val mentions = mentions(nodeArticles.flatten())
        return IssueTimeline(
            issueId = id,
            nodes = nodes.zip(nodeArticles) { node, found -> timelineNode(id, node, found, points, mentions) },
            companies = IssueRules.companies(nodeArticles.map(mentions::tickers), mentions.names, STORY_COMPANY_LIMIT)
                .map { IssueStoryCompany(it.ticker, it.name, it.mentionCount) },
        )
    }

    private fun timelineNode(
        id: Long,
        node: List<TimelineMember>,
        articles: List<IssueArticle>,
        points: Map<Long, String>,
        mentions: Mentions,
    ): IssueTimelineNode {
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
            articleCount = articles.size,
            mediaCount = IssueRules.mediaCount(articles),
            companies = mentions.companies(articles, LIST_COMPANY_LIMIT),
            companyCount = mentions.companies(articles, null).size,
        )
    }

    private fun latestDay(): LocalDate? = issues.findLatestPublishedAt()?.let(IssueRules::kstDate)

    private fun stockIssues(picks: List<IssueMention>, events: Map<Long, SameEvent>): Map<IssueMention, StockIssue> {
        val chosen = picks.mapNotNull { events[it.clusterId] }.distinctBy { it.lead.id }
        val articles = mergedArticles(chosen)
        val summaries = summaries(chosen.map { it.lead }, articles).associateBy { it.id }
        val mentions = mentions(summaries.keys.flatMap { articles[it].orEmpty() })
        val positions = positions(summaries.values.toList())
        val roots = summaries.values.mapNotNull { summary -> summary.storyRootId?.takeIf { it != summary.id } }.distinct()
        val storyTitles = if (roots.isEmpty()) emptyMap() else issues.findByIds(roots).mapNotNull { root -> root.title?.let { root.id to it } }.toMap()

        return picks.mapNotNull { pick ->
            val summary = summaries[pick.clusterId] ?: return@mapNotNull null
            val issueArticles = articles[pick.clusterId].orEmpty()
            pick to StockIssue(
                issue = summary.copy(
                    companies = mentions.companies(issueArticles, LIST_COMPANY_LIMIT),
                    timelinePosition = positions[summary.id] ?: 1,
                ),
                mentionCount = mentions.mentionCount(issueArticles, pick.ticker),
                storyTitle = summary.storyRootId?.takeIf { it != summary.id }?.let(storyTitles::get),
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
            kind = cluster.kind,
            storyRootId = cluster.storyRootId,
        )
    }

    private data class SameEvent(val lead: IssueCluster, val memberIds: List<Long>)

    private fun sameEvents(clusters: List<IssueCluster>): List<SameEvent> {
        if (clusters.isEmpty()) return emptyList()
        val chains = issues.findChains(clusters.map { it.id }, TIMELINE_MAX_DEPTH)
        val roots = clusters.associate { it.id to IssueRules.sameEventRoot(it.id, chains[it.id].orEmpty()) }
        val trees = issues.findSameEventTrees(roots.values.distinct(), TIMELINE_MAX_DEPTH)
        val known = clusters.associateBy { it.id }
        val outside = trees.values.flatten().map { it.clusterId }.filterNot(known::containsKey).distinct()
        val visible = known + issues.findByIds(outside).associateBy { it.id }
        return clusters.groupBy { roots.getValue(it.id) }
            .map { (root, own) ->
                val links = trees[root].orEmpty().filter { it.clusterId in visible }
                val leadId = IssueRules.eventLead(links) { !visible[it]?.title.isNullOrBlank() } ?: own.first().id
                SameEvent(visible.getValue(leadId), (links.map { it.clusterId } + own.map { it.id }).distinct())
            }
            .groupBy { it.lead.id }
            .map { (_, same) -> SameEvent(same.first().lead, same.flatMap { it.memberIds }.distinct()) }
    }

    private fun eventsOf(ids: List<Long>): Map<Long, SameEvent> =
        sameEvents(issues.findByIds(ids.distinct()))
            .flatMap { event -> event.memberIds.map { it to event } }
            .toMap()

    private fun byEvent(mentions: List<IssueMention>, events: Map<Long, SameEvent>): List<IssueMention> =
        mentions
            .mapNotNull { mention -> events[mention.clusterId]?.let { mention.copy(clusterId = it.lead.id) } }
            .groupBy { it.ticker to it.clusterId }
            .map { (_, same) -> same.minWith(IssueRules.mentionOrder()) }

    private fun mergedArticles(events: Collection<SameEvent>): Map<Long, List<IssueArticle>> {
        val found = issues.findArticles(events.flatMap { it.memberIds }.distinct())
        return events.associate { event -> event.lead.id to IssueRules.mergeArticles(event.memberIds.map { found[it].orEmpty() }) }
    }

    private fun eventMembers(id: Long): List<Long> {
        val steps = issues.findChains(listOf(id), TIMELINE_MAX_DEPTH)[id].orEmpty()
        val root = IssueRules.sameEventRoot(id, steps)
        val tree = issues.findSameEventTrees(listOf(root), TIMELINE_MAX_DEPTH)[root].orEmpty().map { it.clusterId }
        val visible = issues.findByIds(tree).mapTo(HashSet()) { it.id }
        return (listOf(id) + tree.filter { it in visible }).distinct()
    }

    private fun positions(summaries: List<IssueSummary>): Map<Long, Int> =
        issues.findChains(summaries.map { it.id }, TIMELINE_MAX_DEPTH)
            .mapValues { (_, steps) -> IssueRules.timelinePosition(steps) }

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
        val names: Map<String, String>,
    ) {
        fun companies(articles: List<IssueArticle>, limit: Int?): List<IssueCompany> =
            IssueRules.companies(articles.map { tickersByArticle[it.id].orEmpty() }, names, limit)

        fun tickers(articles: List<IssueArticle>): Set<String> =
            articles.flatMapTo(HashSet()) { tickersByArticle[it.id].orEmpty() }

        fun mentionCount(articles: List<IssueArticle>, ticker: String): Int =
            companies(articles, null).firstOrNull { it.ticker == ticker }?.mentionCount ?: 0

        fun listed(article: IssueArticle): List<String> =
            tickersByArticle[article.id].orEmpty().filter(names::containsKey).distinct()
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
        const val STORY_COMPANY_LIMIT = 5
    }
}
