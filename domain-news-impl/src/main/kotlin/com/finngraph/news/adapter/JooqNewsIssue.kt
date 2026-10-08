package com.finngraph.news.adapter

import com.finngraph.news.adapter.jooq.tables.references.COMPANIES
import com.finngraph.news.adapter.jooq.tables.references.NEWS
import com.finngraph.news.adapter.jooq.tables.references.NEWS_CLUSTERS
import com.finngraph.news.adapter.jooq.tables.references.NEWS_COMPANIES
import com.finngraph.news.model.IssueArticle
import com.finngraph.news.model.IssueCluster
import com.finngraph.news.model.IssueMention
import com.finngraph.news.model.IssueNeighbors
import com.finngraph.news.port.NewsIssuePort
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class JooqNewsIssue(private val dsl: DSLContext) : NewsIssuePort {

    override fun findLatestPublishedAt(): OffsetDateTime? =
        dsl.select(DSL.max(NEWS.PUBLISHED_AT))
            .from(NEWS)
            .where(IN_VISIBLE)
            .fetchOne()
            ?.value1()

    override fun findPublishedBetween(from: OffsetDateTime, until: OffsetDateTime): List<IssueCluster> =
        selectClusters(
            NEWS_CLUSTERS.ID.`in`(
                DSL.select(NEWS.CLUSTER_ID)
                    .from(NEWS)
                    .where(IN_VISIBLE.and(NEWS.PUBLISHED_AT.ge(from)).and(NEWS.PUBLISHED_AT.lt(until))),
            ),
        )

    override fun findNeighbors(before: OffsetDateTime, from: OffsetDateTime): IssueNeighbors {
        val latestBefore = DSL.field(
            DSL.select(DSL.max(NEWS.PUBLISHED_AT))
                .from(NEWS)
                .where(IN_VISIBLE.and(NEWS.PUBLISHED_AT.lt(before))),
        )
        val earliestFrom = DSL.field(
            DSL.select(DSL.min(NEWS.PUBLISHED_AT))
                .from(NEWS)
                .where(IN_VISIBLE.and(NEWS.PUBLISHED_AT.ge(from))),
        )
        val row = dsl.select(latestBefore, earliestFrom).fetchOne()
        return IssueNeighbors(latestBefore = row?.value1(), earliestFrom = row?.value2())
    }

    override fun findById(id: Long): IssueCluster? =
        selectClusters(NEWS_CLUSTERS.ID.eq(id).and(VISIBLE)).firstOrNull()

    override fun findByIds(ids: List<Long>): List<IssueCluster> {
        if (ids.isEmpty()) return emptyList()
        return selectClusters(NEWS_CLUSTERS.ID.`in`(ids.distinct()).and(VISIBLE))
    }

    override fun findArticles(clusterIds: List<Long>): Map<Long, List<IssueArticle>> {
        if (clusterIds.isEmpty()) return emptyMap()

        return dsl.select(
            NEWS.ID,
            NEWS.CLUSTER_ID,
            NEWS.TITLE,
            NEWS.LINK,
            NEWS.ORIGINALLINK,
            NEWS.PUBLISHED_AT,
            NEWS.SUMMARY,
            NEWS.TRIPLE_EXTRACTED,
        )
            .from(NEWS)
            .where(NEWS.CLUSTER_ID.`in`(clusterIds.distinct()).and(IN_VISIBLE))
            .orderBy(NEWS.PUBLISHED_AT.asc().nullsLast(), NEWS.ID.asc())
            .fetch {
                IssueArticle(
                    id = requireNotNull(it.get(NEWS.ID)),
                    clusterId = requireNotNull(it.get(NEWS.CLUSTER_ID)),
                    title = it.get(NEWS.TITLE),
                    url = it.get(NEWS.ORIGINALLINK) ?: it.get(NEWS.LINK),
                    publishedAt = it.get(NEWS.PUBLISHED_AT),
                    summary = it.get(NEWS.SUMMARY),
                    tripleExtracted = it.get(NEWS.TRIPLE_EXTRACTED) == true,
                )
            }
            .groupBy { it.clusterId }
    }

    override fun findMentionedBetween(
        tickers: Collection<String>,
        from: OffsetDateTime,
        until: OffsetDateTime,
    ): List<IssueMention> {
        if (tickers.isEmpty()) return emptyList()

        val mentions = DSL.selectDistinct(NEWS.CLUSTER_ID, COMPANIES.TICKER)
            .from(NEWS)
            .join(NEWS_COMPANIES).on(NEWS_COMPANIES.NEWS_ID.eq(NEWS.ID))
            .join(COMPANIES).on(COMPANIES.ID.eq(NEWS_COMPANIES.COMPANY_ID))
            .where(IN_VISIBLE.and(COMPANIES.TICKER.`in`(tickers.distinct())))
            .asTable(MENTIONS)
        val clusterId = requireNotNull(mentions.field(NEWS.CLUSTER_ID))
        val ticker = requireNotNull(mentions.field(COMPANIES.TICKER))
        val lastPublishedAt = DSL.max(NEWS.PUBLISHED_AT)
        val publishedInRange = NEWS.PUBLISHED_AT.ge(from).and(NEWS.PUBLISHED_AT.lt(until))

        return dsl.select(ticker, clusterId, lastPublishedAt)
            .from(mentions)
            .join(NEWS).on(NEWS.CLUSTER_ID.eq(clusterId))
            .groupBy(ticker, clusterId)
            .having(DSL.condition(DSL.boolOr(publishedInRange)))
            .fetch {
                IssueMention(
                    ticker = requireNotNull(it.value1()),
                    clusterId = requireNotNull(it.value2()),
                    lastPublishedAt = it.value3(),
                )
            }
    }

    private fun selectClusters(condition: Condition): List<IssueCluster> =
        dsl.select(
            NEWS_CLUSTERS.ID,
            NEWS_CLUSTERS.TITLE,
            NEWS_CLUSTERS.KEYWORDS,
            NEWS_CLUSTERS.REPRESENTATIVE_NEWS_ID,
        )
            .from(NEWS_CLUSTERS)
            .where(condition)
            .fetch { it.toCluster() }

    private fun Record.toCluster() = IssueCluster(
        id = requireNotNull(get(NEWS_CLUSTERS.ID)),
        title = get(NEWS_CLUSTERS.TITLE),
        keywords = get(NEWS_CLUSTERS.KEYWORDS)?.filterNotNull().orEmpty(),
        representativeNewsId = get(NEWS_CLUSTERS.REPRESENTATIVE_NEWS_ID),
    )

    private companion object {
        const val MENTIONS = "mentions"

        val EVENT_CLUSTERS = NEWS_CLUSTERS.`as`("event_clusters")

        val EXTRACTED_NEWS = NEWS.`as`("extracted_news")

        val VISIBLE: Condition = NEWS_CLUSTERS.REPRESENTATIVE_NEWS_ID.isNotNull.and(NEWS_CLUSTERS.TITLE.isNotNull)
            .or(
                DSL.exists(
                    DSL.selectOne()
                        .from(EXTRACTED_NEWS)
                        .where(EXTRACTED_NEWS.CLUSTER_ID.eq(NEWS_CLUSTERS.ID).and(EXTRACTED_NEWS.TRIPLE_EXTRACTED.eq(true))),
                ),
            )

        val IN_VISIBLE: Condition = NEWS.CLUSTER_ID.`in`(
            DSL.select(EVENT_CLUSTERS.ID)
                .from(EVENT_CLUSTERS)
                .where(EVENT_CLUSTERS.REPRESENTATIVE_NEWS_ID.isNotNull.and(EVENT_CLUSTERS.TITLE.isNotNull))
                .union(
                    DSL.select(EXTRACTED_NEWS.CLUSTER_ID)
                        .from(EXTRACTED_NEWS)
                        .where(EXTRACTED_NEWS.CLUSTER_ID.isNotNull.and(EXTRACTED_NEWS.TRIPLE_EXTRACTED.eq(true))),
                ),
        )
    }
}
