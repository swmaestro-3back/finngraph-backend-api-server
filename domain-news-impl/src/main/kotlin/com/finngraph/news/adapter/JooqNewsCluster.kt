package com.finngraph.news.adapter

import com.finngraph.news.adapter.jooq.tables.references.NEWS
import com.finngraph.news.adapter.jooq.tables.references.NEWS_CLUSTERS
import com.finngraph.news.model.ClusterArticle
import com.finngraph.news.model.IssueTimeline
import com.finngraph.news.model.IssueTimelineNode
import com.finngraph.news.model.NewsCluster
import com.finngraph.news.port.NewsClusterPort
import org.jooq.CommonTableExpression
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.Record3
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
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

    override fun findTimeline(clusterId: Long, limit: Int): IssueTimeline? {
        val title = dsl.select(NEWS_CLUSTERS.TITLE)
            .from(NEWS_CLUSTERS)
            .where(NEWS_CLUSTERS.ID.eq(clusterId))
            .fetchOne() ?: return null
        // 제목 없는 클러스터는 사용자에게 보이는 이슈가 아니니 앞선 이슈가 이어져 있어도 타임라인을 주지 않는다.
        if (title.value1() == null) return IssueTimeline(clusterId = clusterId, nodes = emptyList())

        // story_root_id 로 묶으면 같은 루트의 다른 갈래까지 섞이고, 기사가 합류하며 first_published_at 이
        // 앞당겨지면 시각으로 자른 부모가 빠진다. 그래서 부모를 따라 올라간 깊이 순서로 준다.
        // 연결 전 클러스터는 부모가 없으니 자기 자신만 남는다.
        val chain = timelineChain(clusterId)
        val nodes = dsl.withRecursive(chain)
            .select(TIMELINE_FIELDS)
            .from(NEWS_CLUSTERS)
            .join(chain).on(NEWS_CLUSTERS.ID.eq(chain.field(CHAIN_ID, SQLDataType.BIGINT)))
            // 제목 없는 중간 노드는 사슬을 다 따라간 뒤에 빼야 그 너머의 앞선 이슈가 끊기지 않는다.
            .where(NEWS_CLUSTERS.TITLE.isNotNull)
            .orderBy(chain.field(CHAIN_DEPTH, SQLDataType.INTEGER))
            .limit(limit)
            .fetch { it.toTimelineNode(clusterId) }

        return IssueTimeline(clusterId = clusterId, nodes = nodes)
    }

    private fun timelineChain(clusterId: Long): CommonTableExpression<Record3<Long?, Long?, Int?>> {
        val parent = NEWS_CLUSTERS.`as`(PARENT)
        val chainParentId = DSL.field(DSL.name(CHAIN, CHAIN_PARENT_ID), SQLDataType.BIGINT)
        val chainDepth: Field<Int?> = DSL.field(DSL.name(CHAIN, CHAIN_DEPTH), SQLDataType.INTEGER)
        return DSL.name(CHAIN).fields(CHAIN_ID, CHAIN_PARENT_ID, CHAIN_DEPTH).`as`(
            DSL.select(NEWS_CLUSTERS.ID, NEWS_CLUSTERS.PARENT_CLUSTER_ID, DSL.inline(0, SQLDataType.INTEGER))
                .from(NEWS_CLUSTERS)
                .where(NEWS_CLUSTERS.ID.eq(clusterId))
                .unionAll(
                    DSL.select(parent.ID, parent.PARENT_CLUSTER_ID, chainDepth.plus(1))
                        .from(parent)
                        .join(DSL.table(DSL.name(CHAIN))).on(parent.ID.eq(chainParentId))
                        // 연결은 한 번만 쓰이고 부모는 늘 먼저 연결돼 순환이 없다. 깊이 상한은 잘못된 데이터 대비다.
                        .where(chainDepth.lt(MAX_CHAIN_DEPTH)),
                ),
        )
    }

    private fun Record.toTimelineNode(requestedId: Long): IssueTimelineNode {
        val id = requireNotNull(get(NEWS_CLUSTERS.ID))
        return IssueTimelineNode(
            clusterId = id,
            title = requireNotNull(get(NEWS_CLUSTERS.TITLE)),
            summary = get(NEWS_CLUSTERS.SUMMARY),
            firstPublishedAt = requireNotNull(get(NEWS_CLUSTERS.FIRST_PUBLISHED_AT)),
            lastPublishedAt = requireNotNull(get(NEWS_CLUSTERS.LAST_PUBLISHED_AT)),
            current = id == requestedId,
        )
    }

    private companion object {
        const val CHAIN = "chain"
        const val CHAIN_ID = "id"
        const val CHAIN_PARENT_ID = "parent_cluster_id"
        const val CHAIN_DEPTH = "depth"
        const val PARENT = "parent"
        const val MAX_CHAIN_DEPTH = 100

        val TIMELINE_FIELDS = listOf(
            NEWS_CLUSTERS.ID,
            NEWS_CLUSTERS.TITLE,
            NEWS_CLUSTERS.SUMMARY,
            NEWS_CLUSTERS.FIRST_PUBLISHED_AT,
            NEWS_CLUSTERS.LAST_PUBLISHED_AT,
        )
    }
}
