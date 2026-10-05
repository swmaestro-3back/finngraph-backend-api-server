package com.finngraph.web.relation

import com.finngraph.composition.relation.RelationEvidenceView
import java.time.LocalDate

data class RelationEvidenceResponse(
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
) {
    companion object {
        fun from(view: RelationEvidenceView) = RelationEvidenceResponse(
            type = view.type,
            subjectName = view.subjectName,
            subjectTicker = view.subjectTicker,
            objectName = view.objectName,
            objectTicker = view.objectTicker,
            sourceType = view.sourceType,
            mentionedAt = view.mentionedAt,
            evidence = view.evidence,
            sourceSentence = view.sourceSentence,
            item = view.item,
            polarity = view.polarity,
            tense = view.tense,
            subjectImpact = view.subjectImpact,
            objectImpact = view.objectImpact,
            newsId = view.newsId,
            title = view.title,
            url = view.url,
            press = view.press,
            rceptNo = view.rceptNo,
        )
    }
}
