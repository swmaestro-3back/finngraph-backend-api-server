package com.finngraph.calendar.adapter

import com.finngraph.calendar.adapter.jooq.tables.references.MARKET_DAYS
import com.finngraph.calendar.port.MarketDayPort
import org.jooq.DSLContext
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class JooqMarketDayQuery(private val dsl: DSLContext) : MarketDayPort {

    override fun findClosedDates(from: LocalDate, to: LocalDate): List<LocalDate> =
        dsl.select(MARKET_DAYS.TRADE_DATE)
            .from(MARKET_DAYS)
            .where(MARKET_DAYS.TRADE_DATE.between(from, to))
            .and(MARKET_DAYS.IS_OPEN.eq(false))
            .orderBy(MARKET_DAYS.TRADE_DATE)
            .fetch { requireNotNull(it.value1()) }

    override fun findDays(from: LocalDate, to: LocalDate): Map<LocalDate, Boolean> =
        dsl.select(MARKET_DAYS.TRADE_DATE, MARKET_DAYS.IS_OPEN)
            .from(MARKET_DAYS)
            .where(MARKET_DAYS.TRADE_DATE.between(from, to))
            .fetch()
            .associate { requireNotNull(it.value1()) to requireNotNull(it.value2()) }
}
