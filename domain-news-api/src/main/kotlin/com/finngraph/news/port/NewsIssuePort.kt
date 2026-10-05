package com.finngraph.news.port

import com.finngraph.news.model.IssueArticle
import com.finngraph.news.model.IssueChainLink
import com.finngraph.news.model.IssueCluster
import com.finngraph.news.model.IssueMention
import com.finngraph.news.model.IssueNeighbors
import java.time.OffsetDateTime

interface NewsIssuePort {

    fun findLatestPublishedAt(): OffsetDateTime?
    fun findPublishedBetween(from: OffsetDateTime, until: OffsetDateTime): List<IssueCluster>
    fun findNeighbors(before: OffsetDateTime, from: OffsetDateTime): IssueNeighbors
    fun findById(id: Long): IssueCluster?
    fun findByIds(ids: List<Long>): List<IssueCluster>
    fun findArticles(clusterIds: List<Long>): Map<Long, List<IssueArticle>>
    fun findMentionedBetween(tickers: Collection<String>, from: OffsetDateTime, until: OffsetDateTime): List<IssueMention>

    // 요청 이슈부터 parent_cluster_id 를 따라 올라간 사슬. 요청 이슈가 맨 앞이고 같은 이슈는 한 번만 나온다.
    // 없는 이슈면 빈 목록이다.
    fun findChain(id: Long, maxDepth: Int): List<IssueChainLink>

    // 이슈마다 그 이슈와, 거기서 same_event 연결로만 내려간 자손 전부. 한 사건이 여러 클러스터로 갈린 묶음이다.
    // 이슈 자신이 맨 앞이고 같은 이슈는 한 번만 나온다. 없는 이슈는 맵에서 빠진다.
    fun findSameEventTrees(ids: List<Long>, maxDepth: Int): Map<Long, List<IssueChainLink>>

    // 기사 요약 핵심 포인트(news.summary_points) 중 CHANGE 항목의 문장. 없는 기사는 맵에서 빠진다.
    fun findChangePoints(newsIds: List<Long>): Map<Long, String>
}
