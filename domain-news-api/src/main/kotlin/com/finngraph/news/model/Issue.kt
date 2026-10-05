package com.finngraph.news.model

import java.time.OffsetDateTime

data class IssueCluster(
    val id: Long,
    val title: String?,
    val keywords: List<String>,
    val representativeNewsId: Long?,
)

data class IssueArticle(
    val id: Long,
    val clusterId: Long,
    val title: String?,
    val url: String?,
    val publishedAt: OffsetDateTime?,
    val summary: String?,
    val tripleExtracted: Boolean,
)

data class IssueNeighbors(
    val latestBefore: OffsetDateTime?,
    val earliestFrom: OffsetDateTime?,
)

data class IssueMention(
    val ticker: String,
    val clusterId: Long,
    val lastPublishedAt: OffsetDateTime?,
)

// ETL 이 이슈를 앞선 이슈(부모)에 이을 때 남기는 관계. SAME_EVENT 는 한 사건이 클러스터 둘로 갈라진 것이라
// 타임라인에서 한 노드로 합친다.
enum class IssueLinkRelation { FOLLOW_UP, SAME_EVENT }

// 부모 사슬의 한 칸. relation 은 이 이슈와 그 부모 사이의 관계이고, 루트이거나 아직 잇지 않았으면 null 이다.
// startedAt 은 클러스터 첫 기사 시각(news_clusters.first_published_at)으로, ETL 이 부모를 고를 때 쓰는 순서다.
data class IssueChainLink(
    val clusterId: Long,
    val relation: IssueLinkRelation?,
    val originalSize: Int,
    val startedAt: OffsetDateTime,
)
