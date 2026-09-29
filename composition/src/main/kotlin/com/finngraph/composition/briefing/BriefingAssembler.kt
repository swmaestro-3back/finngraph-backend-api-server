package com.finngraph.composition.briefing

import com.finngraph.briefing.model.BriefingArticle
import com.finngraph.briefing.model.BriefingIssue
import com.finngraph.briefing.model.Citation
import com.finngraph.briefing.model.CitationType
import com.finngraph.briefing.model.DailyBriefing
import com.finngraph.composition.port.ArticleBrief
import com.finngraph.composition.port.CommentaryInput
import com.finngraph.composition.port.ContractBrief
import com.finngraph.composition.port.HeadlineInput
import com.finngraph.composition.port.IssueBrief
import com.finngraph.composition.port.RelationBrief
import com.finngraph.composition.port.WatchInput
import java.time.OffsetDateTime

data class BuiltBriefing(
    val briefing: DailyBriefing,
    val droppedSentences: Int,
)

data class BriefingDraftSet(
    val headline: HeadlineDraft?,
    val commentaries: Map<Long, List<SentenceDraft>?>,
    val watchPoints: List<WatchDraft>?,
    val calls: Int,
)

object BriefingAssembler {

    fun headlineInput(input: BriefingInput) = HeadlineInput(
        baseDate = input.baseDate,
        market = input.market,
        themes = input.themes,
        issues = input.issues.map { IssueBrief(it.cluster.id, it.cluster.title ?: "이슈 ${it.cluster.id}", it.cluster.memberCount) },
    )

    fun commentaryInput(issue: IssueCandidate) = CommentaryInput(
        clusterId = issue.cluster.id,
        title = issue.cluster.title ?: "이슈 ${issue.cluster.id}",
        keywords = issue.cluster.keywords,
        articles = issue.articles.map {
            ArticleBrief(it.newsId, it.title ?: "(제목 없음)", it.publishedAt, it.summary ?: "")
        },
        stocks = issue.stocks,
        relations = issue.relations.map {
            RelationBrief(
                id = it.id,
                sentence = it.sourceSentence ?: "${it.subjectName} → ${it.objectName} ${BriefingRules.relationLabel(it.relation)}",
                polarity = it.polarity ?: "affirmed",
                tense = it.tense ?: "past_or_present_fact",
            )
        },
        contracts = issue.contracts.map {
            ContractBrief(it.rceptNo, it.reportName, it.contractName, it.contractAmountText, it.salesRatioText, it.isCorrection)
        },
    )

    fun watchInput(input: BriefingInput) = WatchInput(input.watchCandidates)

    fun build(
        input: BriefingInput,
        drafts: BriefingDraftSet,
        generatedAt: OffsetDateTime,
    ): BuiltBriefing {
        var dropped = 0

        val headlineAllowed = input.citations.filterValues { it.type == CitationType.CLUSTER }
        val headline = BriefingValidator.headline(drafts.headline, headlineAllowed)
        dropped += headline.dropped

        val issues = input.issues.map { candidate ->
            val allowed = commentaryAllowed(candidate, input.citations)
            val validated = drafts.commentaries[candidate.cluster.id]?.let { BriefingValidator.commentary(it, allowed) }
            dropped += validated?.dropped ?: 0
            BriefingIssue(
                clusterId = candidate.cluster.id,
                title = candidate.cluster.title ?: "이슈 ${candidate.cluster.id}",
                keywords = candidate.cluster.keywords.take(KEYWORD_LIMIT),
                newsCount = candidate.cluster.memberCount,
                firstPublishedAt = candidate.cluster.firstPublishedAt,
                lastPublishedAt = candidate.cluster.lastPublishedAt,
                stocks = candidate.stocks,
                articles = candidate.articles.map { BriefingArticle(it.newsId, it.title ?: "(제목 없음)", it.url, it.publishedAt) },
                commentary = validated?.sentences?.takeIf { it.isNotEmpty() },
            )
        }

        val watch = BriefingValidator.watchPoints(drafts.watchPoints, input.watchCandidates)
        dropped += watch.dropped

        val briefing = DailyBriefing(
            baseDate = input.baseDate,
            previousTradingDate = input.previousTradingDate,
            status = BriefingValidator.status(headline.headline, issues),
            generatedAt = generatedAt,
            market = input.market,
            headline = headline.headline,
            issues = issues,
            themes = input.themes,
            watchPoints = watch.points,
            risks = input.risks,
            analyzedNews = input.analyzedNews,
            relationGraph = input.relationGraph,
            flaggedSnapshot = input.flagged,
        )
        return BuiltBriefing(briefing, dropped)
    }

    private fun commentaryAllowed(issue: IssueCandidate, citations: Map<String, Citation>): Map<String, Citation> {
        val keys = buildSet {
            issue.articles.forEach { add("${CitationType.NEWS.name}:${it.newsId}") }
            issue.relations.forEach { add("${CitationType.RELATION.name}:${it.id}") }
            issue.contracts.forEach { add("${CitationType.DISCLOSURE.name}:${it.rceptNo}") }
        }
        return citations.filterKeys { it in keys }
    }

    private const val KEYWORD_LIMIT = 6
}
