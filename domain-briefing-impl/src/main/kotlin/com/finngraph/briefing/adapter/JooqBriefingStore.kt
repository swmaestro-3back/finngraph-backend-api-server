package com.finngraph.briefing.adapter

import com.finngraph.briefing.adapter.jooq.tables.references.DAILY_BRIEFINGS
import com.finngraph.briefing.model.AnalyzedNews
import com.finngraph.briefing.model.BriefingHeadline
import com.finngraph.briefing.model.BriefingIssue
import com.finngraph.briefing.model.BriefingStatus
import com.finngraph.briefing.model.BriefingSummary
import com.finngraph.briefing.model.BriefingTheme
import com.finngraph.briefing.model.DailyBriefing
import com.finngraph.briefing.model.FlagSnapshotEntry
import com.finngraph.briefing.model.MarketSnapshot
import com.finngraph.briefing.model.RelationGraph
import com.finngraph.briefing.model.RiskItem
import com.finngraph.briefing.model.WatchPoint
import com.finngraph.briefing.port.BriefingStorePort
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.time.ZoneOffset

@Component
class JooqBriefingStore(
    @Qualifier("appDslContext") private val dsl: DSLContext,
    springMapper: JsonMapper,
) : BriefingStorePort {

    private val mapper: ObjectMapper = springMapper.rebuild()
        .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
        .build()

    override fun findLatest(): DailyBriefing? =
        selectBriefing()
            .orderBy(DAILY_BRIEFINGS.BASE_DATE.desc())
            .limit(1)
            .fetchOne { it.toBriefing() }

    override fun findByDate(date: LocalDate): DailyBriefing? =
        selectBriefing()
            .where(DAILY_BRIEFINGS.BASE_DATE.eq(date))
            .fetchOne { it.toBriefing() }

    override fun findPreviousBefore(date: LocalDate): DailyBriefing? =
        selectBriefing()
            .where(DAILY_BRIEFINGS.BASE_DATE.lt(date))
            .orderBy(DAILY_BRIEFINGS.BASE_DATE.desc())
            .limit(1)
            .fetchOne { it.toBriefing() }

    override fun listSummaries(limit: Int): List<BriefingSummary> =
        dsl.select(DAILY_BRIEFINGS.BASE_DATE, DAILY_BRIEFINGS.STATUS, DAILY_BRIEFINGS.GENERATED_AT, DAILY_BRIEFINGS.HEADLINE)
            .from(DAILY_BRIEFINGS)
            .orderBy(DAILY_BRIEFINGS.BASE_DATE.desc())
            .limit(limit)
            .fetch {
                BriefingSummary(
                    baseDate = requireNotNull(it[DAILY_BRIEFINGS.BASE_DATE]),
                    status = BriefingStatus.valueOf(requireNotNull(it[DAILY_BRIEFINGS.STATUS])),
                    generatedAt = requireNotNull(it[DAILY_BRIEFINGS.GENERATED_AT]).withOffsetSameInstant(KST),
                    headline = it[DAILY_BRIEFINGS.HEADLINE]?.let { json -> read<BriefingHeadline>(json).text },
                )
            }

    override fun upsert(briefing: DailyBriefing) {
        dsl.insertInto(DAILY_BRIEFINGS)
            .set(DAILY_BRIEFINGS.BASE_DATE, briefing.baseDate)
            .set(values(briefing))
            .onConflict(DAILY_BRIEFINGS.BASE_DATE)
            .doUpdate()
            .set(values(briefing))
            .execute()
    }

    private fun values(briefing: DailyBriefing): Map<org.jooq.Field<*>, Any?> = mapOf(
        DAILY_BRIEFINGS.PREVIOUS_TRADING_DATE to briefing.previousTradingDate,
        DAILY_BRIEFINGS.STATUS to briefing.status.name,
        DAILY_BRIEFINGS.GENERATED_AT to briefing.generatedAt,
        DAILY_BRIEFINGS.MARKET to json(briefing.market),
        DAILY_BRIEFINGS.HEADLINE to briefing.headline?.let(::json),
        DAILY_BRIEFINGS.ISSUES to json(briefing.issues),
        DAILY_BRIEFINGS.THEMES to json(briefing.themes),
        DAILY_BRIEFINGS.WATCH_POINTS to json(briefing.watchPoints),
        DAILY_BRIEFINGS.RISKS to json(briefing.risks),
        DAILY_BRIEFINGS.ANALYZED_NEWS to json(briefing.analyzedNews),
        DAILY_BRIEFINGS.RELATION_GRAPH to json(briefing.relationGraph),
        DAILY_BRIEFINGS.FLAGGED_SNAPSHOT to json(briefing.flaggedSnapshot),
    )

    private fun selectBriefing() = dsl.selectFrom(DAILY_BRIEFINGS)

    private fun Record.toBriefing() = DailyBriefing(
        baseDate = requireNotNull(get(DAILY_BRIEFINGS.BASE_DATE)),
        previousTradingDate = get(DAILY_BRIEFINGS.PREVIOUS_TRADING_DATE),
        status = BriefingStatus.valueOf(requireNotNull(get(DAILY_BRIEFINGS.STATUS))),
        generatedAt = requireNotNull(get(DAILY_BRIEFINGS.GENERATED_AT)).withOffsetSameInstant(KST),
        market = read<MarketSnapshot>(requireNotNull(get(DAILY_BRIEFINGS.MARKET))),
        headline = get(DAILY_BRIEFINGS.HEADLINE)?.let { read<BriefingHeadline>(it) },
        issues = readList(requireNotNull(get(DAILY_BRIEFINGS.ISSUES)), ISSUES),
        themes = readList(requireNotNull(get(DAILY_BRIEFINGS.THEMES)), THEMES),
        watchPoints = readList(requireNotNull(get(DAILY_BRIEFINGS.WATCH_POINTS)), WATCH_POINTS),
        risks = readList(requireNotNull(get(DAILY_BRIEFINGS.RISKS)), RISKS),
        analyzedNews = readList(requireNotNull(get(DAILY_BRIEFINGS.ANALYZED_NEWS)), ANALYZED_NEWS),
        relationGraph = read<RelationGraph>(requireNotNull(get(DAILY_BRIEFINGS.RELATION_GRAPH))),
        flaggedSnapshot = readList(requireNotNull(get(DAILY_BRIEFINGS.FLAGGED_SNAPSHOT)), FLAGS),
    )

    private fun json(value: Any): JSONB = JSONB.valueOf(mapper.writeValueAsString(value))

    private inline fun <reified T> read(json: JSONB): T = mapper.readValue(json.data(), T::class.java)

    private fun <T> readList(json: JSONB, type: TypeReference<List<T>>): List<T> = mapper.readValue(json.data(), type)

    private companion object {
        private val KST: ZoneOffset = ZoneOffset.ofHours(9)
        val ISSUES = object : TypeReference<List<BriefingIssue>>() {}
        val THEMES = object : TypeReference<List<BriefingTheme>>() {}
        val WATCH_POINTS = object : TypeReference<List<WatchPoint>>() {}
        val RISKS = object : TypeReference<List<RiskItem>>() {}
        val ANALYZED_NEWS = object : TypeReference<List<AnalyzedNews>>() {}
        val FLAGS = object : TypeReference<List<FlagSnapshotEntry>>() {}
    }
}
