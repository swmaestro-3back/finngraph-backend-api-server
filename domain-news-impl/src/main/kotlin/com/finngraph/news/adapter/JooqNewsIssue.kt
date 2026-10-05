package com.finngraph.news.adapter

import com.finngraph.news.adapter.jooq.tables.references.NEWS
import com.finngraph.news.adapter.jooq.tables.references.NEWS_CLUSTERS
import com.finngraph.news.model.IssueArticle
import com.finngraph.news.model.IssueCluster
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
            .where(CLUSTERED_PUBLIC)
            .fetchOne()
            ?.value1()

    override fun findPublishedBetween(from: OffsetDateTime, until: OffsetDateTime): List<IssueCluster> =
        selectClusters(
            NEWS_CLUSTERS.ID.`in`(
                DSL.select(NEWS.CLUSTER_ID)
                    .from(NEWS)
                    .where(CLUSTERED_PUBLIC.and(NEWS.PUBLISHED_AT.ge(from)).and(NEWS.PUBLISHED_AT.lt(until))),
            ),
        )

    override fun findNeighbors(before: OffsetDateTime, from: OffsetDateTime): IssueNeighbors {
        val latestBefore = DSL.field(
            DSL.select(DSL.max(NEWS.PUBLISHED_AT))
                .from(NEWS)
                .where(CLUSTERED_PUBLIC.and(NEWS.PUBLISHED_AT.lt(before))),
        )
        val earliestFrom = DSL.field(
            DSL.select(DSL.min(NEWS.PUBLISHED_AT))
                .from(NEWS)
                .where(CLUSTERED_PUBLIC.and(NEWS.PUBLISHED_AT.ge(from))),
        )
        val row = dsl.select(latestBefore, earliestFrom).fetchOne()
        return IssueNeighbors(latestBefore = row?.value1(), earliestFrom = row?.value2())
    }

    override fun findById(id: Long): IssueCluster? =
        selectClusters(NEWS_CLUSTERS.ID.eq(id).and(HAS_PUBLIC_ARTICLE)).firstOrNull()

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
            .where(NEWS.CLUSTER_ID.`in`(clusterIds.distinct()).and(PUBLIC))
            .orderBy(NEWS.PUBLISHED_AT.asc().nullsLast(), NEWS.ID.asc())
            .fetch {
                IssueArticle(
                    id = requireNotNull(it.get(NEWS.ID)),
                    clusterId = requireNotNull(it.get(NEWS.CLUSTER_ID)),
                    title = it.get(NEWS.TITLE),
                    url = it.get(NEWS.ORIGINALLINK) ?: it.get(NEWS.LINK),
                    publishedAt = it.get(NEWS.PUBLISHED_AT),
                    summary = it.get(NEWS.SUMMARY),
                    tripleExtracted = requireNotNull(it.get(NEWS.TRIPLE_EXTRACTED)),
                )
            }
            .groupBy { it.clusterId }
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
        val PUBLIC: Condition = NEWS.TRIPLE_EXTRACTED.eq(true)

        val CLUSTERED_PUBLIC: Condition = NEWS.CLUSTER_ID.isNotNull.and(PUBLIC)

        val HAS_PUBLIC_ARTICLE: Condition = DSL.exists(
            DSL.selectOne()
                .from(NEWS)
                .where(NEWS.CLUSTER_ID.eq(NEWS_CLUSTERS.ID).and(PUBLIC)),
        )
    }
}
