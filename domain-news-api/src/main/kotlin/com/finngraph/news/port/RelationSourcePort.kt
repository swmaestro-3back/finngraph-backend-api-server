package com.finngraph.news.port

import com.finngraph.news.model.RelationEvidence
import com.finngraph.news.model.RelationSource

interface RelationSourcePort {

    fun findBetween(a: String, b: String, relation: String?): List<RelationEvidence>

    fun findByNews(newsIds: Collection<Long>): List<RelationSource>
}
