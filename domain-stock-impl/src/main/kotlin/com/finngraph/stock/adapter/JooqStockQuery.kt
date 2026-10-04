package com.finngraph.stock.adapter

import com.finngraph.stock.adapter.jooq.tables.Stocks
import com.finngraph.stock.adapter.jooq.tables.references.COMPANIES
import com.finngraph.stock.adapter.jooq.tables.references.COMPANY_FINANCIALS
import com.finngraph.stock.adapter.jooq.tables.references.STOCKS
import com.finngraph.stock.adapter.jooq.tables.references.STOCK_CANDLES_DAILY
import com.finngraph.stock.adapter.jooq.tables.references.STOCK_INVESTOR_FLOWS
import com.finngraph.stock.adapter.jooq.tables.references.STOCK_VALUATIONS_DAILY
import com.finngraph.stock.model.CompanyDescription
import com.finngraph.stock.model.CompanyProfile
import com.finngraph.stock.model.StockDetailView
import com.finngraph.stock.model.StockFlags
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

    override fun exists(ticker: Ticker): Boolean =
        dsl.fetchExists(
            dsl.selectOne()
                .from(STOCKS)
                .where(STOCKS.TICKER.eq(ticker.value).and(STOCKS.IS_ACTIVE.eq(true))),
        )

    override fun findAll(): List<StockListView> =
        fetchStocks(ACTIVE, StockPricing.dates(dsl)).map { it.toListView() }

    override fun findByTicker(ticker: Ticker): StockDetailView? {
        val filter: (Stocks) -> Condition = { s -> s.TICKER.eq(ticker.value).and(s.IS_ACTIVE.eq(true)) }
        val dates = StockPricing.dates(dsl)
        val row = fetchStocks(filter, dates).firstOrNull() ?: return null
        return row.toDetailView(revenueYoY(filter), companyDescription(filter), companyProfile(filter), dates)
    }

    override fun findByTickers(tickers: List<Ticker>, asOf: LocalDate?): Map<Ticker, StockPriceView> {
        if (tickers.isEmpty()) return emptyMap()

        val values = tickers.map { it.value }.distinct()
        val filter: (Stocks) -> Condition = { s -> s.TICKER.`in`(values).and(s.IS_ACTIVE.eq(true)) }
        val dates = StockPricing.dates(dsl, asOf)
        return dsl.select(
            STOCKS.TICKER,
            STOCKS.NAME,
            STOCKS.MARKET,
            PX_PRICE,
            PX_CHANGE,
            STOCK_VALUATIONS_DAILY.MARKET_CAP,
        )
            .from(STOCKS)
            .leftJoin(priceTable(dates.price)).on(DSL.trueCondition())
            .leftJoin(STOCK_VALUATIONS_DAILY)
            .on(STOCK_VALUATIONS_DAILY.LISTING_ID.eq(STOCKS.ID).and(STOCK_VALUATIONS_DAILY.TRADE_DATE.eq(dateValue(dates.valuation))))
            .where(filter(STOCKS))
            .fetch { it.toPriceView() }
            .associateBy { Ticker(it.ticker) }
    }

    override fun findLatestTradeDate(): LocalDate? = StockPricing.priceDate(dsl)

    override fun findFlagged(): List<StockFlags> =
        dsl.select(
            STOCKS.TICKER,
            STOCKS.NAME,
            STOCKS.MARKET,
            STOCKS.UNDER_ADMINISTRATION,
            STOCKS.TRADING_SUSPENDED,
            STOCKS.DELISTING_TRADE,
        )
            .from(STOCKS)
            .where(
                STOCKS.IS_ACTIVE.eq(true).and(
                    STOCKS.UNDER_ADMINISTRATION.eq(true)
                        .or(STOCKS.TRADING_SUSPENDED.eq(true))
                        .or(STOCKS.DELISTING_TRADE.eq(true)),
                ),
            )
            .orderBy(STOCKS.TICKER.asc())
            .fetch {
                StockFlags(
                    ticker = requireNotNull(it.get(STOCKS.TICKER)),
                    name = requireNotNull(it.get(STOCKS.NAME)),
                    market = requireNotNull(it.get(STOCKS.MARKET)),
                    underAdministration = it.get(STOCKS.UNDER_ADMINISTRATION) ?: false,
                    tradingSuspended = it.get(STOCKS.TRADING_SUSPENDED) ?: false,
                    delistingTrade = it.get(STOCKS.DELISTING_TRADE) ?: false,
                )
            }

    override fun findKrx300Tickers(): List<Ticker> =
        dsl.select(STOCKS.TICKER)
            .from(STOCKS)
            .where(STOCKS.IS_ACTIVE.eq(true).and(STOCKS.KRX300.eq(true)))
            .orderBy(STOCKS.TICKER)
            .fetch { Ticker(requireNotNull(it.value1())) }

    override fun findNamesByTickers(tickers: Collection<Ticker>): Map<Ticker, String> {
        if (tickers.isEmpty()) return emptyMap()
        return dsl.select(STOCKS.TICKER, STOCKS.NAME)
            .from(STOCKS)
            .where(STOCKS.TICKER.`in`(tickers.map { it.value }.distinct()).and(STOCKS.IS_ACTIVE.eq(true)))
            .fetch { Ticker(requireNotNull(it.value1())) to requireNotNull(it.value2()) }
            .toMap()
    }

    private fun fetchStocks(filter: (Stocks) -> Condition, dates: PriceDates): List<Record> =
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
            foreignRatio(dates.price),
        )
            .from(STOCKS)
            .leftJoin(priceTable(dates.price)).on(DSL.trueCondition())
            .leftJoin(STOCK_VALUATIONS_DAILY)
            .on(STOCK_VALUATIONS_DAILY.LISTING_ID.eq(STOCKS.ID).and(STOCK_VALUATIONS_DAILY.TRADE_DATE.eq(dateValue(dates.valuation))))
            .where(filter(STOCKS))
            .orderBy(STOCKS.TICKER.asc())
            .fetch()

    private fun priceTable(priceDate: LocalDate?): Table<*> {
        val lc = STOCK_CANDLES_DAILY.`as`(LATEST_CANDLES)
        val pc = STOCK_CANDLES_DAILY.`as`(PREV_CANDLES)
        val prev = DSL.lateral(
            DSL.select(pc.CLOSE.`as`(PREV_CLOSE))
                .from(pc)
                .where(pc.STOCK_ID.eq(lc.STOCK_ID).and(pc.TRADE_DATE.lt(lc.TRADE_DATE)))
                .orderBy(pc.TRADE_DATE.desc())
                .limit(1)
                .asTable(PREV),
        )

        return DSL.lateral(
            DSL.select(
                lc.CLOSE.`as`(PRICE),
                DSL.round(
                    lc.CLOSE.minus(PREV_CLOSE_FIELD).div(DSL.nullif(PREV_CLOSE_FIELD, BigDecimal.ZERO)).times(HUNDRED),
                    DERIVED_SCALE,
                ).`as`(CHANGE),
            )
                .from(lc)
                .leftJoin(prev).on(DSL.trueCondition())
                .where(lc.STOCK_ID.eq(STOCKS.ID).and(lc.TRADE_DATE.eq(dateValue(priceDate))))
                .asTable(PX),
        )
    }

    private fun foreignRatio(priceDate: LocalDate?): Field<BigDecimal?> = DSL.field(
        DSL.select(STOCK_INVESTOR_FLOWS.FOREIGN_HOLD_RATIO)
            .from(STOCK_INVESTOR_FLOWS)
            .where(
                STOCK_INVESTOR_FLOWS.STOCK_ID.eq(STOCKS.ID)
                    .and(STOCK_INVESTOR_FLOWS.TRADE_DATE.le(dateValue(priceDate))),
            )
            .orderBy(STOCK_INVESTOR_FLOWS.TRADE_DATE.desc())
            .limit(1),
    ).`as`(FOREIGN_RATIO)

    private fun dateValue(date: LocalDate?): Field<LocalDate?> = DSL.`val`(date, SQLDataType.LOCALDATE)

    private fun companyIds(filter: (Stocks) -> Condition): Select<Record1<Long?>> {
        val s = STOCKS.`as`(COMPANY_ID_SOURCE)
        return DSL.select(s.COMPANY_ID).from(s).where(filter(s))
    }

    private fun companyDescription(filter: (Stocks) -> Condition): CompanyDescription? =
        dsl.select(COMPANIES.DESCRIPTION, COMPANIES.DESCRIPTION_SOURCE, COMPANIES.DESCRIPTION_RCEPT_NO)
            .from(COMPANIES)
            .where(COMPANIES.ID.`in`(companyIds(filter)).and(COMPANIES.DESCRIPTION.isNotNull()))
            .limit(1)
            .fetchOne()
            ?.let { CompanyDescription(requireNotNull(it.value1()), it.value2(), it.value3()) }

    private fun companyProfile(filter: (Stocks) -> Condition): CompanyProfile =
        dsl.select(
            COMPANIES.CEO_NAME,
            COMPANIES.ESTABLISHED_ON,
            STOCKS.LISTED_DATE,
            COMPANIES.FISCAL_MONTH,
            STOCKS.LISTED_SHARES,
            STOCKS.PAR_VALUE,
            COMPANIES.HOMEPAGE,
            COMPANIES.ADDRESS,
        )
            .from(STOCKS)
            .leftJoin(COMPANIES).on(COMPANIES.ID.eq(STOCKS.COMPANY_ID))
            .where(filter(STOCKS))
            .limit(1)
            .fetchOne()
            ?.let {
                CompanyProfile(
                    ceoName = it.get(COMPANIES.CEO_NAME),
                    establishedOn = it.get(COMPANIES.ESTABLISHED_ON),
                    listedOn = it.get(STOCKS.LISTED_DATE),
                    fiscalMonth = it.get(COMPANIES.FISCAL_MONTH),
                    listedShares = it.get(STOCKS.LISTED_SHARES),
                    parValue = it.get(STOCKS.PAR_VALUE),
                    homepage = it.get(COMPANIES.HOMEPAGE),
                    address = it.get(COMPANIES.ADDRESS),
                )
            } ?: EMPTY_PROFILE

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

    private fun Record.toDetailView(
        revenueYoY: BigDecimal?,
        description: CompanyDescription?,
        profile: CompanyProfile,
        dates: PriceDates,
    ) = StockDetailView(
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
        foreignRatio = get(FOREIGN_RATIO_FIELD),
        revenueYoY = revenueYoY,
        baseDate = dates.price,
        description = description,
        profile = profile,
        valuationDate = dates.valuation,
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
        private const val KIS = "KIS"
        private const val DERIVED_SCALE = 4

        private const val LATEST_CANDLES = "lc"
        private const val PREV_CANDLES = "pc"
        private const val KIS_ROWS = "kf"
        private const val PREV = "prev"
        private const val PX = "px"
        private const val PRICE = "price"
        private const val CHANGE = "change"
        private const val PREV_CLOSE = "prev_close"
        private const val COMPANY_ID_SOURCE = "cid"
        private const val FOREIGN_RATIO = "foreign_ratio"

        private val HUNDRED: BigDecimal = BigDecimal("100")

        private val EMPTY_PROFILE = CompanyProfile(null, null, null, null, null, null, null, null)

        private val ACTIVE: (Stocks) -> Condition = { s -> s.IS_ACTIVE.eq(true) }

        private val FOREIGN_RATIO_FIELD: Field<BigDecimal?> = DSL.field(DSL.name(FOREIGN_RATIO), SQLDataType.NUMERIC)

        private val PREV_CLOSE_FIELD: Field<BigDecimal?> = DSL.field(DSL.name(PREV, PREV_CLOSE), SQLDataType.NUMERIC)

        private val PX_PRICE: Field<BigDecimal?> = DSL.field(DSL.name(PX, PRICE), SQLDataType.NUMERIC)
        private val PX_CHANGE: Field<BigDecimal?> = DSL.field(DSL.name(PX, CHANGE), SQLDataType.NUMERIC)

        private val FISCAL_YEAR: Field<String?> = COMPANY_FINANCIALS.FISCAL_YYMM.substring(1, 4)

        private val ANNUAL_ORDER: List<SortField<*>> = listOf(
            FISCAL_YEAR.desc(),
            DSL.field(COMPANY_FINANCIALS.FS_DIV.eq(CONSOLIDATED)).desc().nullsLast(),
            COMPANY_FINANCIALS.DISCLOSED_AT.desc().nullsLast(),
            COMPANY_FINANCIALS.RCEPT_NO.desc().nullsLast(),
        )

        private val KIS_FINANCIALS = COMPANY_FINANCIALS.`as`(KIS_ROWS)

        private val SAME_YEAR_KIS_ROE: Field<BigDecimal?> = DSL.field(
            DSL.select(KIS_FINANCIALS.ROE)
                .from(KIS_FINANCIALS)
                .where(
                    KIS_FINANCIALS.COMPANY_ID.eq(COMPANY_FINANCIALS.COMPANY_ID)
                        .and(KIS_FINANCIALS.PERIOD_TYPE.eq(ANNUAL))
                        .and(KIS_FINANCIALS.SOURCE.eq(KIS))
                        .and(KIS_FINANCIALS.ROE.isNotNull)
                        .and(KIS_FINANCIALS.FISCAL_YYMM.substring(1, 4).eq(FISCAL_YEAR)),
                )
                .orderBy(KIS_FINANCIALS.FISCAL_YYMM.desc())
                .limit(1),
        )

        private val ROE: Field<BigDecimal?> = DSL.field(
            DSL.select(DSL.coalesce(COMPANY_FINANCIALS.ROE, SAME_YEAR_KIS_ROE))
                .from(COMPANY_FINANCIALS)
                .where(
                    COMPANY_FINANCIALS.COMPANY_ID.eq(STOCKS.COMPANY_ID)
                        .and(COMPANY_FINANCIALS.PERIOD_TYPE.eq(ANNUAL)),
                )
                .orderBy(ANNUAL_ORDER)
                .limit(1),
        ).`as`("roe")
    }
}
