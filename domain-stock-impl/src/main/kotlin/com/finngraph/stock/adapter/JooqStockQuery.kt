package com.finngraph.stock.adapter

import com.finngraph.stock.adapter.jooq.tables.Stocks
import com.finngraph.stock.adapter.jooq.tables.references.COMPANY_FINANCIALS
import com.finngraph.stock.adapter.jooq.tables.references.STOCK_CANDLES_DAILY
import com.finngraph.stock.adapter.jooq.tables.references.STOCK_INVESTOR_FLOWS
import com.finngraph.stock.adapter.jooq.tables.references.STOCKS
import com.finngraph.stock.adapter.jooq.tables.references.STOCK_VALUATIONS_DAILY
import com.finngraph.stock.model.StockDetailView
import com.finngraph.stock.model.StockListView
import com.finngraph.stock.model.StockPriceView
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockQueryPort
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.Record1
import org.jooq.Select
import org.jooq.SortField
import org.jooq.Table
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.abs

@Component
class JooqStockQuery(private val dsl: DSLContext) : StockQueryPort {

    override fun findAll(): List<StockListView> =
        fetchStocks(ACTIVE).map { it.toListView() }

    override fun findByTicker(ticker: Ticker): StockDetailView? {
        val filter: (Stocks) -> Condition = { s -> s.TICKER.eq(ticker.value).and(s.IS_ACTIVE.eq(true)) }
        val row = fetchStocks(filter).firstOrNull() ?: return null
        return row.toDetailView(revenueYoY(filter))
    }

    override fun findByTickers(tickers: List<Ticker>): Map<Ticker, StockPriceView> {
        if (tickers.isEmpty()) return emptyMap()

        val values = tickers.map { it.value }.distinct()
        return fetchStocks { s -> s.TICKER.`in`(values).and(s.IS_ACTIVE.eq(true)) }
            .map { it.toPriceView() }
            .associateBy { Ticker(it.ticker) }
    }

    override fun findLatestTradeDate(): LocalDate? =
        dsl.select(BASE_DATE).fetchOne()?.value1()

    private fun fetchStocks(filter: (Stocks) -> Condition): List<Record> =
        dsl.select(
            STOCKS.TICKER,
            STOCKS.NAME,
            STOCKS.MARKET,
            PX_PRICE,
            PX_CHANGE,
            STOCK_VALUATIONS_DAILY.MARKET_CAP,
            STOCK_VALUATIONS_DAILY.PER,
            STOCK_VALUATIONS_DAILY.PBR,
            STOCK_VALUATIONS_DAILY.EPS,
            STOCK_VALUATIONS_DAILY.DIVIDEND_YIELD,
            STOCK_VALUATIONS_DAILY.R_1W,
            STOCK_VALUATIONS_DAILY.R_1M,
            STOCK_VALUATIONS_DAILY.R_3M,
            ROE,
            FOREIGN_RATIO,
            BASE_DATE_COLUMN,
        )
            .from(STOCKS)
            .leftJoin(priceTable(filter)).on(PX_STOCK_ID.eq(STOCKS.ID))
            .leftJoin(STOCK_VALUATIONS_DAILY)
            .on(STOCK_VALUATIONS_DAILY.LISTING_ID.eq(STOCKS.ID).and(STOCK_VALUATIONS_DAILY.TRADE_DATE.eq(BASE_DATE)))
            .where(filter(STOCKS))
            .orderBy(STOCKS.TICKER.asc())
            .fetch()

    private fun priceTable(filter: (Stocks) -> Condition): Table<*> {
        val dc = STOCK_CANDLES_DAILY.`as`(WINDOW_CANDLES)
        val windowed = DSL.select(
            dc.STOCK_ID,
            dc.TRADE_DATE,
            dc.CLOSE,
            DSL.lag(dc.CLOSE).over().partitionBy(dc.STOCK_ID).orderBy(dc.TRADE_DATE).`as`(PREV_CLOSE),
        )
            .from(dc)
            .where(dc.STOCK_ID.`in`(stockIds(filter)))
            .asTable(WINDOWED)

        return DSL.select(
            W_STOCK_ID,
            W_CLOSE.`as`(PRICE),
            DSL.round(
                W_CLOSE.minus(W_PREV_CLOSE).div(DSL.nullif(W_PREV_CLOSE, BigDecimal.ZERO)).times(HUNDRED),
                DERIVED_SCALE,
            ).`as`(CHANGE),
        )
            .from(windowed)
            .where(W_TRADE_DATE.eq(BASE_DATE))
            .asTable(PX)
    }

    private fun stockIds(filter: (Stocks) -> Condition): Select<Record1<Long?>> {
        val s = STOCKS.`as`(STOCK_ID_SOURCE)
        return DSL.select(s.ID).from(s).where(filter(s))
    }

    private fun companyIds(filter: (Stocks) -> Condition): Select<Record1<Long?>> {
        val s = STOCKS.`as`(COMPANY_ID_SOURCE)
        return DSL.select(s.COMPANY_ID).from(s).where(filter(s))
    }

    private fun revenueYoY(filter: (Stocks) -> Condition): BigDecimal? {
        val chosenPerYear = LinkedHashMap<String, Long?>()
        dsl.select(FISCAL_YEAR, COMPANY_FINANCIALS.REVENUE)
            .from(COMPANY_FINANCIALS)
            .where(
                COMPANY_FINANCIALS.PERIOD_TYPE.eq(ANNUAL)
                    .and(COMPANY_FINANCIALS.COMPANY_ID.`in`(companyIds(filter))),
            )
            .orderBy(ANNUAL_ORDER)
            .fetch()
            .forEach {
                val year = requireNotNull(it.get(FISCAL_YEAR))
                if (!chosenPerYear.containsKey(year)) {
                    chosenPerYear[year] = it.get(COMPANY_FINANCIALS.REVENUE)
                }
            }

        val revenues = chosenPerYear.values.toList()
        if (revenues.size < 2) return null

        val latest = revenues[0] ?: return null
        val previous = revenues[1] ?: return null
        if (previous == 0L) return null

        return BigDecimal.valueOf(latest)
            .subtract(BigDecimal.valueOf(previous))
            .multiply(HUNDRED)
            .divide(BigDecimal.valueOf(abs(previous)), DERIVED_SCALE, RoundingMode.HALF_UP)
    }

    private fun Record.toListView() = StockListView(
        ticker = requireNotNull(get(STOCKS.TICKER)),
        name = requireNotNull(get(STOCKS.NAME)),
        market = requireNotNull(get(STOCKS.MARKET)),
        price = get(PX_PRICE),
        change = get(PX_CHANGE),
        w1 = get(STOCK_VALUATIONS_DAILY.R_1W),
        m1 = get(STOCK_VALUATIONS_DAILY.R_1M),
        m3 = get(STOCK_VALUATIONS_DAILY.R_3M),
        marketCap = get(STOCK_VALUATIONS_DAILY.MARKET_CAP),
        per = get(STOCK_VALUATIONS_DAILY.PER),
        pbr = get(STOCK_VALUATIONS_DAILY.PBR),
        roe = get(ROE),
        dividendYield = get(STOCK_VALUATIONS_DAILY.DIVIDEND_YIELD),
    )

    private fun Record.toDetailView(revenueYoY: BigDecimal?) = StockDetailView(
        ticker = requireNotNull(get(STOCKS.TICKER)),
        name = requireNotNull(get(STOCKS.NAME)),
        market = requireNotNull(get(STOCKS.MARKET)),
        price = get(PX_PRICE),
        change = get(PX_CHANGE),
        marketCap = get(STOCK_VALUATIONS_DAILY.MARKET_CAP),
        per = get(STOCK_VALUATIONS_DAILY.PER),
        pbr = get(STOCK_VALUATIONS_DAILY.PBR),
        roe = get(ROE),
        eps = get(STOCK_VALUATIONS_DAILY.EPS),
        dividendYield = get(STOCK_VALUATIONS_DAILY.DIVIDEND_YIELD),
        foreignRatio = get(FOREIGN_RATIO),
        revenueYoY = revenueYoY,
        baseDate = get(BASE_DATE_COLUMN),
    )

    private fun Record.toPriceView() = StockPriceView(
        ticker = requireNotNull(get(STOCKS.TICKER)),
        name = requireNotNull(get(STOCKS.NAME)),
        market = requireNotNull(get(STOCKS.MARKET)),
        price = get(PX_PRICE),
        change = get(PX_CHANGE),
        marketCap = get(STOCK_VALUATIONS_DAILY.MARKET_CAP),
    )

    companion object {
        private const val ANNUAL = "A"
        private const val CONSOLIDATED = "CFS"
        private const val DERIVED_SCALE = 4

        private const val BASE_CANDLES = "base_candles"
        private const val WINDOW_CANDLES = "wc"
        private const val WINDOWED = "w"
        private const val PX = "px"
        private const val PRICE = "price"
        private const val CHANGE = "change"
        private const val PREV_CLOSE = "prev_close"
        private const val STOCK_ID = "stock_id"
        private const val TRADE_DATE = "trade_date"
        private const val CLOSE = "close"
        private const val STOCK_ID_SOURCE = "sid"
        private const val COMPANY_ID_SOURCE = "cid"

        private val HUNDRED: BigDecimal = BigDecimal("100")

        private val ACTIVE: (Stocks) -> Condition = { s -> s.IS_ACTIVE.eq(true) }

        private val BASE_DATE: Field<LocalDate?> = STOCK_CANDLES_DAILY.`as`(BASE_CANDLES).let { dc ->
            DSL.field(DSL.select(DSL.max(dc.TRADE_DATE)).from(dc))
        }

        private val BASE_DATE_COLUMN: Field<LocalDate?> = BASE_DATE.`as`("base_date")

        private val W_STOCK_ID: Field<Long?> = DSL.field(DSL.name(WINDOWED, STOCK_ID), SQLDataType.BIGINT)
        private val W_TRADE_DATE: Field<LocalDate?> = DSL.field(DSL.name(WINDOWED, TRADE_DATE), SQLDataType.LOCALDATE)
        private val W_CLOSE: Field<BigDecimal> = DSL.field(DSL.name(WINDOWED, CLOSE), SQLDataType.NUMERIC)
        private val W_PREV_CLOSE: Field<BigDecimal> = DSL.field(DSL.name(WINDOWED, PREV_CLOSE), SQLDataType.NUMERIC)

        private val PX_STOCK_ID: Field<Long?> = DSL.field(DSL.name(PX, STOCK_ID), SQLDataType.BIGINT)
        private val PX_PRICE: Field<BigDecimal?> = DSL.field(DSL.name(PX, PRICE), SQLDataType.NUMERIC)
        private val PX_CHANGE: Field<BigDecimal?> = DSL.field(DSL.name(PX, CHANGE), SQLDataType.NUMERIC)

        private val FISCAL_YEAR: Field<String?> = COMPANY_FINANCIALS.FISCAL_YYMM.substring(1, 4)

        private val ANNUAL_ORDER: List<SortField<*>> = listOf(
            FISCAL_YEAR.desc(),
            DSL.field(COMPANY_FINANCIALS.FS_DIV.eq(CONSOLIDATED)).desc().nullsLast(),
            COMPANY_FINANCIALS.DISCLOSED_AT.desc().nullsLast(),
            COMPANY_FINANCIALS.RCEPT_NO.desc().nullsLast(),
        )

        private val ROE: Field<BigDecimal?> = DSL.field(
            DSL.select(COMPANY_FINANCIALS.ROE)
                .from(COMPANY_FINANCIALS)
                .where(
                    COMPANY_FINANCIALS.COMPANY_ID.eq(STOCKS.COMPANY_ID)
                        .and(COMPANY_FINANCIALS.PERIOD_TYPE.eq(ANNUAL)),
                )
                .orderBy(ANNUAL_ORDER)
                .limit(1),
        ).`as`("roe")

        private val FOREIGN_RATIO: Field<BigDecimal?> = DSL.field(
            DSL.select(STOCK_INVESTOR_FLOWS.FOREIGN_HOLD_RATIO)
                .from(STOCK_INVESTOR_FLOWS)
                .where(
                    STOCK_INVESTOR_FLOWS.STOCK_ID.eq(STOCKS.ID)
                        .and(STOCK_INVESTOR_FLOWS.TRADE_DATE.le(BASE_DATE)),
                )
                .orderBy(STOCK_INVESTOR_FLOWS.TRADE_DATE.desc())
                .limit(1),
        ).`as`("foreign_ratio")
    }
}
