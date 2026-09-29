package com.finngraph.news.model

import java.time.LocalDate

data class RelationSource(
    val id: Long,
    val sourceType: String,
    val newsId: Long?,
    val rceptNo: String?,
    val subjectName: String,
    val subjectCode: String?,
    val relation: String,
    val objectName: String,
    val objectCode: String?,
    val item: String?,
    val sourceSentence: String?,
    val polarity: String?,
    val tense: String?,
    val subjectImpact: String?,
    val objectImpact: String?,
    val mentionedAt: LocalDate,
)
