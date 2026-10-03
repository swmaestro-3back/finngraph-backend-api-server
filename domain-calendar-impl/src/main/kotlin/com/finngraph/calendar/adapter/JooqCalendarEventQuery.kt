package com.finngraph.calendar.adapter

import com.finngraph.calendar.adapter.jooq.tables.references.STOCK_CALENDAR_EVENTS
import com.finngraph.calendar.model.CalendarEvent
import com.finngraph.calendar.model.EventKind
import com.finngraph.calendar.port.CalendarEventPort
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Component
class JooqCalendarEventQuery(private val dsl: DSLContext) : CalendarEventPort {

    override fun findByTickers(tickers: Collection<String>, from: LocalDate, to: LocalDate): List<CalendarEvent> {
        if (tickers.isEmpty()) return emptyList()
        val e = STOCK_CALENDAR_EVENTS
        return select(e.TICKER.`in`(tickers.distinct()).and(e.EVENT_DATE.between(from, to)))
    }

    override fun findByTicker(ticker: String, basisFrom: LocalDate, basisTo: LocalDate): List<CalendarEvent> {
        val e = STOCK_CALENDAR_EVENTS
        return select(e.TICKER.eq(ticker).and(e.BASIS_DATE.between(basisFrom, basisTo)))
    }

    override fun findLatestUpdatedAt(): OffsetDateTime? =
        dsl.select(DSL.max(STOCK_CALENDAR_EVENTS.UPDATED_AT))
            .from(STOCK_CALENDAR_EVENTS)
            .fetchOne()
            ?.value1()
            ?.withOffsetSameInstant(KST)

    private fun select(condition: Condition): List<CalendarEvent> {
        val e = STOCK_CALENDAR_EVENTS
        return dsl.select(
            e.EVENT_DATE,
            e.KIND,
            e.TICKER,
            e.STOCK_NAME,
            e.BASIS_DATE,
            e.END_DATE,
            e.AMOUNT,
            e.RATIO,
            e.LABEL,
            AGENDA,
            AGENDA_TRUNCATED,
            ESTIMATED,
        )
            .from(e)
            .where(condition)
            .and(e.KIND.`in`(KNOWN_KINDS))
            .orderBy(e.EVENT_DATE, e.TICKER, e.KIND)
            .fetch {
                CalendarEvent(
                    date = requireNotNull(it[e.EVENT_DATE]),
                    kind = EventKind.valueOf(requireNotNull(it[e.KIND])),
                    ticker = requireNotNull(it[e.TICKER]),
                    stockName = requireNotNull(it[e.STOCK_NAME]),
                    basisDate = requireNotNull(it[e.BASIS_DATE]),
                    endDate = it[e.END_DATE],
                    amount = it[e.AMOUNT],
                    ratio = it[e.RATIO],
                    label = it[e.LABEL],
                    agenda = it[AGENDA]?.filterNotNull() ?: emptyList(),
                    agendaTruncated = it[AGENDA_TRUNCATED] == true,
                    estimated = it[ESTIMATED] == true,
                )
            }
    }

    private companion object {
        val KST: ZoneOffset = ZoneOffset.ofHours(9)

        val KNOWN_KINDS: List<String> = EventKind.entries.map { it.name }

        val AGENDA = DSL.field(
            "array(select jsonb_array_elements_text(coalesce({0} -> 'agenda', '[]'::jsonb)))",
            SQLDataType.VARCHAR.array(),
            STOCK_CALENDAR_EVENTS.DETAIL,
        )

        val AGENDA_TRUNCATED = DSL.field(
            "coalesce(({0} ->> 'agenda_truncated')::boolean, false)",
            SQLDataType.BOOLEAN,
            STOCK_CALENDAR_EVENTS.DETAIL,
        )

        val ESTIMATED = DSL.field(
            "coalesce(({0} ->> 'estimated')::boolean, false)",
            SQLDataType.BOOLEAN,
            STOCK_CALENDAR_EVENTS.DETAIL,
        )
    }
}
