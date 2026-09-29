package com.finngraph.composition.briefing

import com.finngraph.briefing.model.BriefingStock
import com.finngraph.briefing.model.BriefingStockRef
import com.finngraph.briefing.model.Citation
import com.finngraph.briefing.model.WatchKind
import com.finngraph.news.model.ClusterArticle
import com.finngraph.news.model.NewsCluster
import com.finngraph.news.model.RelationSource
import com.finngraph.stock.model.SupplyContract

data class SentenceDraft(
    val text: String,
    val citationKeys: List<String>,
)

data class HeadlineDraft(
    val text: String,
    val citationKeys: List<String>,
)

data class WatchDraft(
    val candidateIndex: Int,
    val text: String,
    val citationKeys: List<String>,
)

data class IssueCandidate(
    val cluster: NewsCluster,
    val articles: List<ClusterArticle>,
    val stocks: List<BriefingStock>,
    val relations: List<RelationSource>,
    val contracts: List<SupplyContract>,
)

data class WatchCandidate(
    val kind: WatchKind,
    val factText: String,
    val citation: Citation,
    val stocks: List<BriefingStockRef>,
)
