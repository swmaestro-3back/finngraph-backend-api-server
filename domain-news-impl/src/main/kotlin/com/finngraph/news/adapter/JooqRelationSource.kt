package com.finngraph.news.adapter

import com.finngraph.news.adapter.jooq.tables.references.RELATION_SOURCES
import com.finngraph.news.model.RelationSource
import com.finngraph.news.port.RelationSourcePort
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class JooqRelationSource(private val dsl: DSLContext) : RelationSourcePort {

    override fun findMentionedBetween(fromExclusive: LocalDate, toInclusive: LocalDate): List<RelationSource> =
        dsl.selectFrom(RELATION_SOURCES)
            .where(RELATION_SOURCES.MENTIONED_AT.gt(fromExclusive).and(RELATION_SOURCES.MENTIONED_AT.le(toInclusive)))
            .orderBy(RELATION_SOURCES.MENTIONED_AT.desc(), RELATION_SOURCES.ID.asc())
            .fetch {
                RelationSource(
                    id = requireNotNull(it.id),
                    sourceType = requireNotNull(it.sourceType),
                    newsId = it.newsId,
                    rceptNo = it.rceptNo,
                    subjectName = requireNotNull(it.subjectName),
                    subjectCode = it.subjectCode,
                    relation = requireNotNull(it.relation),
                    objectName = requireNotNull(it.objectName),
                    objectCode = it.objectCode,
                    item = it.item,
                    sourceSentence = it.sourceSentence,
                    polarity = it.polarity,
                    tense = it.tense,
                    subjectImpact = it.subjectImpact,
                    objectImpact = it.objectImpact,
                    mentionedAt = requireNotNull(it.mentionedAt),
                )
            }
}
