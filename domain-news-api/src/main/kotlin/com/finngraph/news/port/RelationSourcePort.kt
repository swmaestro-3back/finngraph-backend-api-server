package com.finngraph.news.port

import com.finngraph.news.model.RelationSource
import java.time.LocalDate

interface RelationSourcePort {

    fun findMentionedBetween(fromExclusive: LocalDate, toInclusive: LocalDate): List<RelationSource>
}
