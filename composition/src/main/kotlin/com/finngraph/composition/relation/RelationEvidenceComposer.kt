package com.finngraph.composition.relation

import com.finngraph.composition.issue.IssueRules
import com.finngraph.news.model.RelationEvidence
import com.finngraph.news.port.RelationSourcePort
import com.finngraph.stock.model.SupplyContract
import com.finngraph.stock.port.StockContractPort
import org.springframework.stereotype.Component
import java.time.LocalDate

data class RelationEvidenceView(
    val type: String,
    val subjectName: String,
    val subjectTicker: String?,
    val objectName: String,
    val objectTicker: String?,
    val sourceType: String,
    val mentionedAt: LocalDate,
    val evidence: String?,
    val sourceSentence: String?,
    val item: String?,
    val polarity: String?,
    val tense: String?,
    val subjectImpact: String?,
    val objectImpact: String?,
    val newsId: Long?,
    val title: String?,
    val url: String?,
    val press: String?,
    val rceptNo: String?,
)

@Component
class RelationEvidenceComposer(
    private val relationSources: RelationSourcePort,
    private val stockContract: StockContractPort,
) {

    fun between(a: String, b: String, relation: String?): List<RelationEvidenceView> {
        val rows = relationSources.findBetween(a, b, relation)
        val disclosures = stockContract.findByRceptNos(rows.mapNotNull { it.rceptNo }.distinct())
            .associateBy { it.rceptNo }
        return rows.map { it.toView(it.rceptNo?.let(disclosures::get)) }
    }

    private fun RelationEvidence.toView(disclosure: SupplyContract?): RelationEvidenceView {
        val fromDisclosure = rceptNo != null
        return RelationEvidenceView(
            type = relation,
            subjectName = subjectName,
            subjectTicker = subjectCode,
            objectName = objectName,
            objectTicker = objectCode,
            sourceType = sourceType,
            mentionedAt = mentionedAt,
            evidence = evidence,
            sourceSentence = sourceSentence,
            item = item,
            polarity = polarity,
            tense = tense,
            subjectImpact = subjectImpact,
            objectImpact = objectImpact,
            newsId = newsId,
            title = if (fromDisclosure) disclosure?.reportName else newsTitle,
            url = if (fromDisclosure) disclosure?.link else newsUrl,
            press = if (fromDisclosure) null else IssueRules.press(newsUrl),
            rceptNo = rceptNo,
        )
    }
}
