package com.finngraph.news.adapter

import com.finngraph.news.adapter.jooq.tables.references.NEWS
import com.finngraph.news.adapter.jooq.tables.references.NEWS_CLUSTERS
import com.finngraph.news.model.ClusterArticle
import com.finngraph.news.model.NewsCluster
import com.finngraph.news.port.NewsClusterPort
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class JooqNewsCluster(private val dsl: DSLContext) : NewsClusterPort {

    override fun findRecent(since: OffsetDateTime, minMembers: Int, limit: Int): List<NewsCluster> =
        dsl.select(
            NEWS_CLUSTERS.ID,
            NEWS_CLUSTERS.TITLE,
            NEWS_CLUSTERS.KEYWORDS,
            NEWS_CLUSTERS.MEMBER_COUNT,
            NEWS_CLUSTERS.FIRST_PUBLISHED_AT,
            NEWS_CLUSTERS.LAST_PUBLISHED_AT,
        )
            .from(NEWS_CLUSTERS)
            .where(NEWS_CLUSTERS.LAST_PUBLISHED_AT.gt(since).and(NEWS_CLUSTERS.MEMBER_COUNT.ge(minMembers)))
            .orderBy(NEWS_CLUSTERS.MEMBER_COUNT.desc(), NEWS_CLUSTERS.LAST_PUBLISHED_AT.desc(), NEWS_CLUSTERS.ID.desc())
            .limit(limit)
            .fetch {
                NewsCluster(
                    id = requireNotNull(it.get(NEWS_CLUSTERS.ID)),
                    title = it.get(NEWS_CLUSTERS.TITLE),
                    keywords = it.get(NEWS_CLUSTERS.KEYWORDS)?.filterNotNull().orEmpty(),
                    memberCount = it.get(NEWS_CLUSTERS.MEMBER_COUNT) ?: 0,
                    firstPublishedAt = requireNotNull(it.get(NEWS_CLUSTERS.FIRST_PUBLISHED_AT)),
                    lastPublishedAt = requireNotNull(it.get(NEWS_CLUSTERS.LAST_PUBLISHED_AT)),
                )
            }

    override fun findArticles(clusterIds: List<Long>, perCluster: Int): Map<Long, List<ClusterArticle>> {
        if (clusterIds.isEmpty()) return emptyMap()

        return dsl.select(
            NEWS.ID,
            NEWS.CLUSTER_ID,
            NEWS.TITLE,
            NEWS.LINK,
            NEWS.ORIGINALLINK,
            NEWS.PUBLISHED_AT,
            NEWS.SUMMARY,
        )
            .from(NEWS)
            .where(NEWS.CLUSTER_ID.`in`(clusterIds.distinct()))
            .orderBy(NEWS.PUBLISHED_AT.desc().nullsLast(), NEWS.ID.desc())
            .fetch {
                ClusterArticle(
                    newsId = requireNotNull(it.get(NEWS.ID)),
                    clusterId = requireNotNull(it.get(NEWS.CLUSTER_ID)),
                    title = it.get(NEWS.TITLE),
                    url = it.get(NEWS.ORIGINALLINK) ?: it.get(NEWS.LINK),
                    publishedAt = it.get(NEWS.PUBLISHED_AT),
                    summary = it.get(NEWS.SUMMARY),
                )
            }
            .groupBy { it.clusterId }
            .mapValues { (_, articles) -> articles.take(perCluster) }
    }
}
