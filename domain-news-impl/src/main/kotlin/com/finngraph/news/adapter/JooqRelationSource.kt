package com.finngraph.news.adapter

import com.finngraph.news.adapter.jooq.tables.references.NEWS
import com.finngraph.news.adapter.jooq.tables.references.RELATION_SOURCES
import com.finngraph.news.model.RelationEvidence
import com.finngraph.news.model.RelationSource
import com.finngraph.news.port.RelationSourcePort
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
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

    override fun findBetween(a: String, b: String, relation: String?): List<RelationEvidence> {
        val forward = subject(a).and(objectOf(b))
        val backward = subject(b).and(objectOf(a))
        val typed = relation?.let { RELATION_SOURCES.RELATION.eq(it) } ?: DSL.noCondition()

        return dsl.select(
            RELATION_SOURCES.RELATION,
            RELATION_SOURCES.SUBJECT_NAME,
            RELATION_SOURCES.SUBJECT_CODE,
            RELATION_SOURCES.OBJECT_NAME,
            RELATION_SOURCES.OBJECT_CODE,
            RELATION_SOURCES.SOURCE_TYPE,
            RELATION_SOURCES.MENTIONED_AT,
            RELATION_SOURCES.EVIDENCE,
            RELATION_SOURCES.SOURCE_SENTENCE,
            RELATION_SOURCES.ITEM,
            RELATION_SOURCES.POLARITY,
            RELATION_SOURCES.TENSE,
            RELATION_SOURCES.SUBJECT_IMPACT,
            RELATION_SOURCES.OBJECT_IMPACT,
            RELATION_SOURCES.NEWS_ID,
            RELATION_SOURCES.RCEPT_NO,
            NEWS.TITLE,
            NEWS.LINK,
            NEWS.ORIGINALLINK,
        )
            .from(RELATION_SOURCES)
            .leftJoin(NEWS).on(NEWS.ID.eq(RELATION_SOURCES.NEWS_ID))
            .where(forward.or(backward))
            .and(typed)
            .and(RELATION_SOURCES.SOURCE_TYPE.eq(DISCLOSURE).or(NEWS.TRIPLE_EXTRACTED.eq(true)))
            .orderBy(RELATION_SOURCES.MENTIONED_AT.desc(), RELATION_SOURCES.ID.desc())
            .fetch {
                RelationEvidence(
                    relation = requireNotNull(it.get(RELATION_SOURCES.RELATION)),
                    subjectName = requireNotNull(it.get(RELATION_SOURCES.SUBJECT_NAME)),
                    subjectCode = it.get(RELATION_SOURCES.SUBJECT_CODE),
                    objectName = requireNotNull(it.get(RELATION_SOURCES.OBJECT_NAME)),
                    objectCode = it.get(RELATION_SOURCES.OBJECT_CODE),
                    sourceType = requireNotNull(it.get(RELATION_SOURCES.SOURCE_TYPE)),
                    mentionedAt = requireNotNull(it.get(RELATION_SOURCES.MENTIONED_AT)),
                    evidence = it.get(RELATION_SOURCES.EVIDENCE),
                    sourceSentence = it.get(RELATION_SOURCES.SOURCE_SENTENCE),
                    item = it.get(RELATION_SOURCES.ITEM),
                    polarity = it.get(RELATION_SOURCES.POLARITY),
                    tense = it.get(RELATION_SOURCES.TENSE),
                    subjectImpact = it.get(RELATION_SOURCES.SUBJECT_IMPACT),
                    objectImpact = it.get(RELATION_SOURCES.OBJECT_IMPACT),
                    newsId = it.get(RELATION_SOURCES.NEWS_ID),
                    newsTitle = it.get(NEWS.TITLE),
                    newsUrl = it.get(NEWS.ORIGINALLINK) ?: it.get(NEWS.LINK),
                    rceptNo = it.get(RELATION_SOURCES.RCEPT_NO),
                )
            }
    }

    private fun subject(key: String): Condition = endpoint(RELATION_SOURCES.SUBJECT_CODE, RELATION_SOURCES.SUBJECT_NAME, key)

    private fun objectOf(key: String): Condition = endpoint(RELATION_SOURCES.OBJECT_CODE, RELATION_SOURCES.OBJECT_NAME, key)

    private fun endpoint(code: Field<String?>, name: Field<String?>, key: String): Condition =
        code.eq(key).or(name.eq(key))

    private companion object {
        const val DISCLOSURE = "disclosure"
    }
}
