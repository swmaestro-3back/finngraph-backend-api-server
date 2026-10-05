package com.finngraph.news.port

import com.finngraph.news.model.RelationEvidence
import com.finngraph.news.model.RelationSource
import java.time.LocalDate

interface RelationSourcePort {

    fun findMentionedBetween(fromExclusive: LocalDate, toInclusive: LocalDate): List<RelationSource>

    fun findBetween(a: String, b: String, relation: String?): List<RelationEvidence>
}
