package com.finngraph.theme.adapter

import com.finngraph.theme.adapter.jooq.tables.references.STOCKS
import com.finngraph.theme.adapter.jooq.tables.references.STOCK_CANDLES_DAILY
import com.finngraph.theme.adapter.jooq.tables.references.STOCK_VALUATIONS_DAILY
import com.finngraph.theme.model.CandleDayCount
import com.finngraph.theme.model.PricingBasis
import com.finngraph.theme.model.StockObservation
import com.finngraph.theme.model.TradeValueAverage
import com.finngraph.theme.model.TradingCalendar
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import java.math.BigDecimal
import java.time.LocalDate

internal object ThemeQuerySupport {

    private const val BASE_CANDLES = "base_candles"
    private const val BASE_VALUATIONS = "base_valuations"
    private const val LATEST_CANDLES = "lc"
    private const val PREV_CANDLES = "pc"
    private const val PREV = "prev"
    private const val PREV_DATE = "prev_date"
    private const val PREV_CLOSE = "prev_close"
    private const val CANDLE_WINDOW_DAYS = 60L

    private val LATEST_CANDLE_DATE: Field<LocalDate?> = STOCK_CANDLES_DAILY.`as`(BASE_CANDLES).let { dc ->
        DSL.field(DSL.select(DSL.max(dc.TRADE_DATE)).from(dc))
    }

    private val LATEST_VALUATION_DATE: Field<LocalDate?> = STOCK_VALUATIONS_DAILY.`as`(BASE_VALUATIONS).let { dv ->
        DSL.field(DSL.select(DSL.max(dv.TRADE_DATE)).from(dv))
    }

    val BASE_DATE: Field<LocalDate?> = DSL.least(LATEST_CANDLE_DATE, LATEST_VALUATION_DATE)

    private val PREV_DATE_FIELD: Field<LocalDate?> = DSL.field(DSL.name(PREV, PREV_DATE), SQLDataType.LOCALDATE)
    private val PREV_CLOSE_FIELD: Field<BigDecimal?> = DSL.field(DSL.name(PREV, PREV_CLOSE), SQLDataType.NUMERIC)

    fun pricingBasis(dsl: DSLContext): PricingBasis {
        val baseDate = dsl.select(BASE_DATE).fetchOne()?.value1() ?: return PricingBasis(null, null)
        val counts = candleCounts(dsl, baseDate)
        return PricingBasis(baseDate, TradingCalendar.previousTradingDate(baseDate, counts), counts)
    }

    fun activeStocks(dsl: DSLContext, baseDate: LocalDate?, filter: Condition = DSL.noCondition()): List<StockObservation> {
        val base = DSL.`val`(baseDate, SQLDataType.LOCALDATE)
        val lc = STOCK_CANDLES_DAILY.`as`(LATEST_CANDLES)
        val pc = STOCK_CANDLES_DAILY.`as`(PREV_CANDLES)
        val prev = DSL.lateral(
            DSL.select(pc.TRADE_DATE.`as`(PREV_DATE), pc.CLOSE.`as`(PREV_CLOSE))
                .from(pc)
                .where(pc.STOCK_ID.eq(STOCKS.ID).and(pc.TRADE_DATE.lt(base)))
                .orderBy(pc.TRADE_DATE.desc())
                .limit(1)
                .asTable(PREV),
        )

        return dsl.select(
            STOCKS.ID,
            STOCKS.TICKER,
            STOCKS.NAME,
            STOCKS.MARKET,
            STOCKS.IS_ACTIVE,
            STOCKS.TRADING_SUSPENDED,
            STOCKS.UNDER_ADMINISTRATION,
            STOCKS.DELISTING_TRADE,
            STOCKS.PREFERRED_STOCK,
            STOCKS.ETP,
            STOCKS.SPAC,
            lc.CLOSE,
            lc.VOLUME,
            lc.TRADE_VALUE,
            PREV_DATE_FIELD,
            PREV_CLOSE_FIELD,
            STOCK_VALUATIONS_DAILY.MARKET_CAP,
            STOCK_VALUATIONS_DAILY.R_1W,
            STOCK_VALUATIONS_DAILY.R_1M,
            STOCK_VALUATIONS_DAILY.R_3M,
        )
            .from(STOCKS)
            .leftJoin(lc).on(lc.STOCK_ID.eq(STOCKS.ID).and(lc.TRADE_DATE.eq(base)))
            .leftJoin(prev).on(DSL.trueCondition())
            .leftJoin(STOCK_VALUATIONS_DAILY)
            .on(STOCK_VALUATIONS_DAILY.LISTING_ID.eq(STOCKS.ID).and(STOCK_VALUATIONS_DAILY.TRADE_DATE.eq(base)))
            .where(STOCKS.IS_ACTIVE.eq(true).and(filter))
            .orderBy(STOCKS.ID.asc())
            .fetch {
                StockObservation(
                    id = requireNotNull(it.get(STOCKS.ID)),
                    ticker = requireNotNull(it.get(STOCKS.TICKER)),
                    name = requireNotNull(it.get(STOCKS.NAME)),
                    market = requireNotNull(it.get(STOCKS.MARKET)),
                    isActive = it.get(STOCKS.IS_ACTIVE) == true,
                    tradingSuspended = it.get(STOCKS.TRADING_SUSPENDED) == true,
                    underAdministration = it.get(STOCKS.UNDER_ADMINISTRATION) == true,
                    delistingTrade = it.get(STOCKS.DELISTING_TRADE) == true,
                    preferredStock = it.get(STOCKS.PREFERRED_STOCK) == true,
                    etp = it.get(STOCKS.ETP) == true,
                    spac = it.get(STOCKS.SPAC) == true,
                    close = it.get(lc.CLOSE),
                    volume = it.get(lc.VOLUME),
                    tradeValue = it.get(lc.TRADE_VALUE),
                    prevDate = it.get(PREV_DATE_FIELD),
                    prevClose = it.get(PREV_CLOSE_FIELD),
                    marketCap = it.get(STOCK_VALUATIONS_DAILY.MARKET_CAP),
                    r1w = it.get(STOCK_VALUATIONS_DAILY.R_1W),
                    r1m = it.get(STOCK_VALUATIONS_DAILY.R_1M),
                    r3m = it.get(STOCK_VALUATIONS_DAILY.R_3M),
                )
            }
    }

    fun averageTradeValues(dsl: DSLContext, basis: PricingBasis, filter: Condition = DSL.noCondition()): Map<Long, TradeValueAverage> {
        val baseDate = basis.baseDate ?: return emptyMap()
        val dates = TradingCalendar.tradingDatesBefore(baseDate, basis.candleCounts)
        if (dates.isEmpty()) return emptyMap()

        val average = DSL.avg(STOCK_CANDLES_DAILY.TRADE_VALUE)
        val days = DSL.count(STOCK_CANDLES_DAILY.TRADE_VALUE)
        return dsl.select(STOCK_CANDLES_DAILY.STOCK_ID, average, days)
            .from(STOCK_CANDLES_DAILY)
            .where(
                STOCK_CANDLES_DAILY.TRADE_DATE.`in`(dates)
                    .and(STOCK_CANDLES_DAILY.VOLUME.gt(0L))
                    .and(filter),
            )
            .groupBy(STOCK_CANDLES_DAILY.STOCK_ID)
            .fetch()
            .mapNotNull { record ->
                val value = record.get(average) ?: return@mapNotNull null
                TradeValueAverage(
                    stockId = requireNotNull(record.get(STOCK_CANDLES_DAILY.STOCK_ID)),
                    average = value,
                    dayCount = record.get(days),
                )
            }
            .associateBy { it.stockId }
    }

    private fun candleCounts(dsl: DSLContext, baseDate: LocalDate): List<CandleDayCount> =
        dsl.select(STOCK_CANDLES_DAILY.TRADE_DATE, DSL.count())
            .from(STOCK_CANDLES_DAILY)
            .where(STOCK_CANDLES_DAILY.TRADE_DATE.between(baseDate.minusDays(CANDLE_WINDOW_DAYS), baseDate))
            .groupBy(STOCK_CANDLES_DAILY.TRADE_DATE)
            .orderBy(STOCK_CANDLES_DAILY.TRADE_DATE.desc())
            .fetch { CandleDayCount(requireNotNull(it.value1()), it.value2()) }
}
