package com.finngraph.news.model

import java.time.LocalDate

data class RelationEvidence(
    val relation: String,
    val subjectName: String,
    val subjectCode: String?,
    val objectName: String,
    val objectCode: String?,
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
    val newsTitle: String?,
    val newsUrl: String?,
    val rceptNo: String?,
)
