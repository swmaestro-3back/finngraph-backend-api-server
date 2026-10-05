package com.finngraph.news.adapter

import com.finngraph.news.adapter.jooq.tables.references.COMPANIES
import com.finngraph.news.adapter.jooq.tables.references.NEWS
import com.finngraph.news.adapter.jooq.tables.references.NEWS_CLUSTERS
import com.finngraph.news.adapter.jooq.tables.references.NEWS_COMPANIES
import com.finngraph.news.model.IssueArticle
import com.finngraph.news.model.IssueChainLink
import com.finngraph.news.model.IssueCluster
import com.finngraph.news.model.IssueLinkRelation
import com.finngraph.news.model.IssueMention
import com.finngraph.news.model.IssueNeighbors
import com.finngraph.news.port.NewsIssuePort
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
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

    override fun findByIds(ids: List<Long>): List<IssueCluster> {
        if (ids.isEmpty()) return emptyList()
        return selectClusters(NEWS_CLUSTERS.ID.`in`(ids.distinct()).and(HAS_PUBLIC_ARTICLE))
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
            .where(CLUSTERED_PUBLIC.and(COMPANIES.TICKER.`in`(tickers.distinct())))
            .asTable(MENTIONS)
        val clusterId = requireNotNull(mentions.field(NEWS.CLUSTER_ID))
        val ticker = requireNotNull(mentions.field(COMPANIES.TICKER))
        val lastPublishedAt = DSL.max(NEWS.PUBLISHED_AT)
        val publishedInRange = NEWS.PUBLISHED_AT.ge(from).and(NEWS.PUBLISHED_AT.lt(until))

        return dsl.select(ticker, clusterId, lastPublishedAt)
            .from(mentions)
            .join(NEWS).on(NEWS.CLUSTER_ID.eq(clusterId).and(PUBLIC))
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

    override fun findChain(id: Long, maxDepth: Int): List<IssueChainLink> {
        // story_root_id 로 묶으면 같은 이야기에서 갈라진 다른 갈래까지 섞이므로 부모만 따라 올라간다.
        // 깊이 순서가 곧 요청 이슈 → 과거 순서다.
        val parent = NEWS_CLUSTERS.`as`(PARENT)
        val chainParentId = DSL.field(DSL.name(CHAIN, CHAIN_PARENT_ID), SQLDataType.BIGINT)
        val chainDepth = DSL.field(DSL.name(CHAIN, CHAIN_DEPTH), SQLDataType.INTEGER)
        val chain = DSL.name(CHAIN)
            .fields(CHAIN_ID, CHAIN_PARENT_ID, CHAIN_RELATION, CHAIN_ORIGINAL_SIZE, CHAIN_STARTED_AT, CHAIN_DEPTH)
            .`as`(
                DSL.select(
                    NEWS_CLUSTERS.ID,
                    NEWS_CLUSTERS.PARENT_CLUSTER_ID,
                    NEWS_CLUSTERS.LINK_RELATION,
                    NEWS_CLUSTERS.ORIGINAL_SIZE,
                    NEWS_CLUSTERS.FIRST_PUBLISHED_AT,
                    DSL.inline(0, SQLDataType.INTEGER),
                )
                    .from(NEWS_CLUSTERS)
                    .where(NEWS_CLUSTERS.ID.eq(id))
                    .unionAll(
                        DSL.select(
                            parent.ID,
                            parent.PARENT_CLUSTER_ID,
                            parent.LINK_RELATION,
                            parent.ORIGINAL_SIZE,
                            parent.FIRST_PUBLISHED_AT,
                            chainDepth.plus(1),
                        )
                            .from(parent)
                            .join(DSL.table(DSL.name(CHAIN))).on(parent.ID.eq(chainParentId))
                            // ETL 은 먼저 시작한 이슈에만 이어 순환이 생기지 않는다. 깊이 상한은 잘못된 데이터 대비다.
                            .where(chainDepth.lt(maxDepth)),
                    ),
            )

        return dsl.withRecursive(chain)
            .select(
                chain.field(CHAIN_ID, SQLDataType.BIGINT),
                chain.field(CHAIN_RELATION, SQLDataType.CLOB),
                chain.field(CHAIN_ORIGINAL_SIZE, SQLDataType.INTEGER),
                chain.field(CHAIN_STARTED_AT, SQLDataType.TIMESTAMPWITHTIMEZONE),
            )
            .from(chain)
            .orderBy(chain.field(CHAIN_DEPTH, SQLDataType.INTEGER))
            .fetch { chainLink(it.value1(), it.value2(), it.value3(), it.value4()) }
            // 순환이 있으면 상한까지 같은 이슈가 되풀이되므로 처음 나온 칸까지만 쓴다.
            .distinctBy { it.clusterId }
    }

    override fun findSameEventTrees(ids: List<Long>, maxDepth: Int): Map<Long, List<IssueChainLink>> {
        if (ids.isEmpty()) return emptyMap()

        // 클러스터마다 부모가 하나뿐이라, same_event 연결만 따라 내려가면 한 사건으로 갈린 클러스터가 저마다 한 번씩 나온다.
        // 순환은 findChain 과 같은 깊이 상한과 중복 제거로 막는다.
        val child = NEWS_CLUSTERS.`as`(CHILD)
        val treeTopId = DSL.field(DSL.name(TREE, TREE_TOP_ID), SQLDataType.BIGINT)
        val treeId = DSL.field(DSL.name(TREE, CHAIN_ID), SQLDataType.BIGINT)
        val treeDepth = DSL.field(DSL.name(TREE, CHAIN_DEPTH), SQLDataType.INTEGER)
        val tree = DSL.name(TREE)
            .fields(TREE_TOP_ID, CHAIN_ID, CHAIN_RELATION, CHAIN_ORIGINAL_SIZE, CHAIN_STARTED_AT, CHAIN_DEPTH)
            .`as`(
                DSL.select(
                    NEWS_CLUSTERS.ID,
                    NEWS_CLUSTERS.ID,
                    NEWS_CLUSTERS.LINK_RELATION,
                    NEWS_CLUSTERS.ORIGINAL_SIZE,
                    NEWS_CLUSTERS.FIRST_PUBLISHED_AT,
                    DSL.inline(0, SQLDataType.INTEGER),
                )
                    .from(NEWS_CLUSTERS)
                    .where(NEWS_CLUSTERS.ID.`in`(ids.distinct()))
                    .unionAll(
                        DSL.select(
                            treeTopId,
                            child.ID,
                            child.LINK_RELATION,
                            child.ORIGINAL_SIZE,
                            child.FIRST_PUBLISHED_AT,
                            treeDepth.plus(1),
                        )
                            .from(child)
                            .join(DSL.table(DSL.name(TREE))).on(child.PARENT_CLUSTER_ID.eq(treeId))
                            .where(child.LINK_RELATION.eq(SAME_EVENT).and(treeDepth.lt(maxDepth))),
                    ),
            )

        return dsl.withRecursive(tree)
            .select(
                tree.field(TREE_TOP_ID, SQLDataType.BIGINT),
                tree.field(CHAIN_ID, SQLDataType.BIGINT),
                tree.field(CHAIN_RELATION, SQLDataType.CLOB),
                tree.field(CHAIN_ORIGINAL_SIZE, SQLDataType.INTEGER),
                tree.field(CHAIN_STARTED_AT, SQLDataType.TIMESTAMPWITHTIMEZONE),
            )
            .from(tree)
            .orderBy(tree.field(TREE_TOP_ID, SQLDataType.BIGINT), tree.field(CHAIN_DEPTH, SQLDataType.INTEGER))
            .fetch { requireNotNull(it.value1()) to chainLink(it.value2(), it.value3(), it.value4(), it.value5()) }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, links) -> links.distinctBy { it.clusterId } }
    }

    override fun findChangePoints(newsIds: List<Long>): Map<Long, String> {
        if (newsIds.isEmpty()) return emptyMap()

        return dsl.select(NEWS.ID, CHANGE_POINT)
            .from(NEWS)
            .where(NEWS.ID.`in`(newsIds.distinct()))
            .fetch()
            .mapNotNull { row ->
                val text = row.value2()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                requireNotNull(row.value1()) to text
            }
            .toMap()
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

    private fun chainLink(id: Long?, relation: String?, originalSize: Int?, startedAt: OffsetDateTime?) = IssueChainLink(
        clusterId = requireNotNull(id),
        relation = relation?.let(RELATIONS::get),
        originalSize = originalSize ?: 0,
        startedAt = requireNotNull(startedAt),
    )

    private fun Record.toCluster() = IssueCluster(
        id = requireNotNull(get(NEWS_CLUSTERS.ID)),
        title = get(NEWS_CLUSTERS.TITLE),
        keywords = get(NEWS_CLUSTERS.KEYWORDS)?.filterNotNull().orEmpty(),
        representativeNewsId = get(NEWS_CLUSTERS.REPRESENTATIVE_NEWS_ID),
    )

    private companion object {
        const val MENTIONS = "mentions"

        const val CHAIN = "chain"
        const val CHAIN_ID = "id"
        const val CHAIN_PARENT_ID = "parent_cluster_id"
        const val CHAIN_RELATION = "link_relation"
        const val CHAIN_ORIGINAL_SIZE = "original_size"
        const val CHAIN_STARTED_AT = "first_published_at"
        const val CHAIN_DEPTH = "depth"
        const val PARENT = "parent"

        const val TREE = "tree"
        const val TREE_TOP_ID = "top_id"
        const val CHILD = "child"

        const val SAME_EVENT = "same_event"

        val RELATIONS: Map<String, IssueLinkRelation> = mapOf(
            "follow_up" to IssueLinkRelation.FOLLOW_UP,
            SAME_EVENT to IssueLinkRelation.SAME_EVENT,
        )

        // summary_points 는 [{"kind": "CHANGE", "text": "…"}] 배열이다. lax 모드 jsonpath 는 배열이 아니거나
        // 키가 빠진 값에서도 오류 없이 비므로 형식이 어긋난 행이 조회 전체를 깨지 않는다.
        val CHANGE_POINT = DSL.field(
            "jsonb_path_query_first({0}, 'lax $[*] ? (@.kind == \"CHANGE\" && @.text.type() == \"string\").text') #>> '{}'",
            SQLDataType.CLOB,
            NEWS.SUMMARY_POINTS,
        )

        val PUBLIC: Condition = NEWS.TRIPLE_EXTRACTED.eq(true)

        val CLUSTERED_PUBLIC: Condition = NEWS.CLUSTER_ID.isNotNull.and(PUBLIC)

        val HAS_PUBLIC_ARTICLE: Condition = DSL.exists(
            DSL.selectOne()
                .from(NEWS)
                .where(NEWS.CLUSTER_ID.eq(NEWS_CLUSTERS.ID).and(PUBLIC)),
        )
    }
}
