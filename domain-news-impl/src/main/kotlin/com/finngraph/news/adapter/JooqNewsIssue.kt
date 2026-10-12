package com.finngraph.news.adapter

import com.finngraph.news.adapter.jooq.tables.references.COMPANIES
import com.finngraph.news.adapter.jooq.tables.references.NEWS
import com.finngraph.news.adapter.jooq.tables.references.NEWS_CLUSTERS
import com.finngraph.news.adapter.jooq.tables.references.NEWS_COMPANIES
import com.finngraph.news.model.IssueArticle
import com.finngraph.news.model.IssueChainLink
import com.finngraph.news.model.IssueCluster
import com.finngraph.news.model.IssueDescendant
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
            NEWS.SUMMARY_POINTS,
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
                    summaryPoints = SummaryPointJson.parse(it.get(NEWS.SUMMARY_POINTS)?.data()),
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

    override fun findChain(id: Long, maxDepth: Int): List<IssueChainLink> {
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
            .distinctBy { it.clusterId }
    }

    override fun findSameEventTrees(ids: List<Long>, maxDepth: Int): Map<Long, List<IssueChainLink>> {
        if (ids.isEmpty()) return emptyMap()

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

    override fun findDescendants(ids: List<Long>, maxDepth: Int): List<IssueDescendant> {
        if (ids.isEmpty()) return emptyList()

        val child = NEWS_CLUSTERS.`as`(CHILD)
        val downId = DSL.field(DSL.name(DOWN, CHAIN_ID), SQLDataType.BIGINT)
        val downDepth = DSL.field(DSL.name(DOWN, CHAIN_DEPTH), SQLDataType.INTEGER)
        val down = DSL.name(DOWN)
            .fields(CHAIN_ID, CHAIN_PARENT_ID, CHAIN_RELATION, CHAIN_ORIGINAL_SIZE, CHAIN_STARTED_AT, CHAIN_DEPTH)
            .`as`(
                DSL.select(
                    NEWS_CLUSTERS.ID,
                    NEWS_CLUSTERS.PARENT_CLUSTER_ID,
                    NEWS_CLUSTERS.LINK_RELATION,
                    NEWS_CLUSTERS.ORIGINAL_SIZE,
                    NEWS_CLUSTERS.FIRST_PUBLISHED_AT,
                    DSL.inline(1, SQLDataType.INTEGER),
                )
                    .from(NEWS_CLUSTERS)
                    .where(NEWS_CLUSTERS.PARENT_CLUSTER_ID.`in`(ids.distinct()))
                    .unionAll(
                        DSL.select(
                            child.ID,
                            child.PARENT_CLUSTER_ID,
                            child.LINK_RELATION,
                            child.ORIGINAL_SIZE,
                            child.FIRST_PUBLISHED_AT,
                            downDepth.plus(1),
                        )
                            .from(child)
                            .join(DSL.table(DSL.name(DOWN))).on(child.PARENT_CLUSTER_ID.eq(downId))
                            .where(downDepth.lt(maxDepth)),
                    ),
            )

        return dsl.withRecursive(down)
            .select(
                down.field(CHAIN_ID, SQLDataType.BIGINT),
                down.field(CHAIN_PARENT_ID, SQLDataType.BIGINT),
                down.field(CHAIN_RELATION, SQLDataType.CLOB),
                down.field(CHAIN_ORIGINAL_SIZE, SQLDataType.INTEGER),
                down.field(CHAIN_STARTED_AT, SQLDataType.TIMESTAMPWITHTIMEZONE),
            )
            .from(down)
            .orderBy(down.field(CHAIN_DEPTH, SQLDataType.INTEGER))
            .fetch {
                IssueDescendant(
                    link = chainLink(it.value1(), it.value3(), it.value4(), it.value5()),
                    parentId = requireNotNull(it.value2()),
                )
            }
            .distinctBy { it.link.clusterId }
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
        const val DOWN = "down"

        const val SAME_EVENT = "same_event"

        val RELATIONS: Map<String, IssueLinkRelation> = mapOf(
            "follow_up" to IssueLinkRelation.FOLLOW_UP,
            SAME_EVENT to IssueLinkRelation.SAME_EVENT,
        )

        val CHANGE_POINT = DSL.field(
            "jsonb_path_query_first({0}, 'lax $[*] ? (@.kind == \"CHANGE\" && @.text.type() == \"string\").text') #>> '{}'",
            SQLDataType.CLOB,
            NEWS.SUMMARY_POINTS,
        )

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
