package com.finngraph.stock.adapter

import com.finngraph.stock.adapter.jooq.tables.references.STOCK_CANDLES_DAILY
import com.finngraph.stock.adapter.jooq.tables.references.STOCK_VALUATIONS_DAILY
import com.finngraph.stock.model.DailyCandleCount
import com.finngraph.stock.model.PriceCalendar
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.LocalDate

internal data class PriceDates(val price: LocalDate?, val valuation: LocalDate?)

internal object StockPricing {

    private const val CANDLE_WINDOW_DAYS = 60L

    fun priceDate(dsl: DSLContext): LocalDate? {
        val latest = dsl.select(DSL.max(STOCK_CANDLES_DAILY.TRADE_DATE))
            .from(STOCK_CANDLES_DAILY)
            .fetchOne()
            ?.value1()
            ?: return null
        val counts = dsl.select(STOCK_CANDLES_DAILY.TRADE_DATE, DSL.count())
            .from(STOCK_CANDLES_DAILY)
            .where(STOCK_CANDLES_DAILY.TRADE_DATE.between(latest.minusDays(CANDLE_WINDOW_DAYS), latest))
            .groupBy(STOCK_CANDLES_DAILY.TRADE_DATE)
            .fetch { DailyCandleCount(requireNotNull(it.value1()), it.value2()) }
        return PriceCalendar.priceDate(counts)
    }

    fun dates(dsl: DSLContext, asOf: LocalDate? = null): PriceDates {
        val price = asOf ?: priceDate(dsl) ?: return PriceDates(null, null)
        val valuation = dsl.select(DSL.max(STOCK_VALUATIONS_DAILY.TRADE_DATE))
            .from(STOCK_VALUATIONS_DAILY)
            .where(STOCK_VALUATIONS_DAILY.TRADE_DATE.le(price))
            .fetchOne()
            ?.value1()
        return PriceDates(price, valuation)
    }
}
