package com.finngraph.composition.port

import com.finngraph.briefing.model.BriefingStock
import com.finngraph.briefing.model.BriefingTheme
import com.finngraph.briefing.model.MarketSnapshot
import com.finngraph.composition.briefing.HeadlineDraft
import com.finngraph.composition.briefing.SentenceDraft
import com.finngraph.composition.briefing.WatchCandidate
import com.finngraph.composition.briefing.WatchDraft
import java.time.LocalDate
import java.time.OffsetDateTime

data class IssueBrief(
    val clusterId: Long,
    val title: String,
    val newsCount: Int,
)

data class HeadlineInput(
    val baseDate: LocalDate,
    val market: MarketSnapshot,
    val themes: List<BriefingTheme>,
    val issues: List<IssueBrief>,
)

data class ArticleBrief(
    val newsId: Long,
    val title: String,
    val publishedAt: OffsetDateTime?,
    val text: String,
)

data class RelationBrief(
    val id: Long,
    val sentence: String,
    val polarity: String,
    val tense: String,
)

data class ContractBrief(
    val rceptNo: String,
    val reportName: String,
    val contractName: String?,
    val contractAmountText: String?,
    val salesRatioText: String?,
    val isCorrection: Boolean,
)

data class CommentaryInput(
    val clusterId: Long,
    val title: String,
    val keywords: List<String>,
    val articles: List<ArticleBrief>,
    val stocks: List<BriefingStock>,
    val relations: List<RelationBrief>,
    val contracts: List<ContractBrief>,
)

data class WatchInput(
    val candidates: List<WatchCandidate>,
)

interface BriefingWriterPort {

    val enabled: Boolean

    fun headline(input: HeadlineInput): HeadlineDraft?
    fun commentary(input: CommentaryInput): List<SentenceDraft>?
    fun watchPoints(input: WatchInput): List<WatchDraft>?
}
