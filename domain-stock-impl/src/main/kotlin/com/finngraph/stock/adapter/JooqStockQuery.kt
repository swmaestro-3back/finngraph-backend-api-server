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
import com.finngraph.stock.model.PeerComparison
import com.finngraph.stock.model.PeerMetric
import com.finngraph.stock.model.StockDetailView
import com.finngraph.stock.model.StockFlags
import com.finngraph.stock.model.StockListView
import com.finngraph.stock.model.StockPriceView
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.model.Week52Range
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

    override fun findAll(): List<StockListView> {
        val dates = StockPricing.dates(dsl)
        val ranges = week52Ranges(dates.price, DSL.noCondition())
        return fetchStocks(ACTIVE, dates).map { it.toListView(ranges[it.get(STOCKS.ID)]) }
    }

    override fun findByTicker(ticker: Ticker): StockDetailView? {
        val filter: (Stocks) -> Condition = { s -> s.TICKER.eq(ticker.value).and(s.IS_ACTIVE.eq(true)) }
        val dates = StockPricing.dates(dsl)
        val row = fetchStocks(filter, dates).firstOrNull() ?: return null
        val stockId = requireNotNull(row.get(STOCKS.ID))
        val week52 = week52Ranges(dates.price, STOCK_CANDLES_DAILY.STOCK_ID.eq(stockId))[stockId]
        return row.toDetailView(revenueYoY(filter), companyDescription(filter), companyProfile(filter), dates, week52)
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

    override fun compareWithin(ticker: Ticker, peers: Collection<Ticker>): PeerComparison? {
        val values = peers.map { it.value }.distinct()
        val dates = StockPricing.dates(dsl)
        val members = DSL.name(PEERS).`as`(
            dsl.select(
                STOCKS.TICKER.`as`(PEER_TICKER),
                PX_CHANGE.`as`(PEER_CHANGE),
                STOCK_VALUATIONS_DAILY.MARKET_CAP.`as`(PEER_MARKET_CAP),
                PX_TRADE_VALUE.`as`(PEER_TRADE_VALUE),
                positiveOnly(STOCK_VALUATIONS_DAILY.PER).`as`(PEER_PER),
                positiveOnly(STOCK_VALUATIONS_DAILY.PBR).`as`(PEER_PBR),
                ROE,
                STOCK_VALUATIONS_DAILY.DIVIDEND_YIELD.`as`(PEER_DIVIDEND_YIELD),
            )
                .from(STOCKS)
                .leftJoin(priceTable(dates.price)).on(DSL.trueCondition())
                .leftJoin(STOCK_VALUATIONS_DAILY)
                .on(STOCK_VALUATIONS_DAILY.LISTING_ID.eq(STOCKS.ID).and(STOCK_VALUATIONS_DAILY.TRADE_DATE.eq(dateValue(dates.valuation))))
                .where(STOCKS.TICKER.`in`(values).and(STOCKS.IS_ACTIVE.eq(true))),
        )

        val metrics = PEER_METRICS.map { PeerColumn(it, members.peerField(it.column)) }
        val memberTicker = members.peerField(PEER_TICKER, String::class.java)
        val ranked = DSL.select(
            listOf(memberTicker, DSL.count().over().`as`(PEER_MEMBERS)) +
                metrics.flatMap { (metric, field) ->
                    val order = if (metric.ascending) field.asc().nullsLast() else field.desc().nullsLast()
                    listOf(
                        field,
                        DSL.`when`(field.isNotNull, DSL.rank().over(DSL.orderBy(order))).`as`(metric.rank),
                        DSL.count(field).over().`as`(metric.count),
                    )
                },
        )
            .from(members)
            .asTable(RANKED)
        val stats = DSL.select(
            metrics.map { (metric, field) ->
                DSL.round(DSL.percentileCont(MEDIAN).withinGroupOrderBy(field).cast(SQLDataType.NUMERIC), metric.scale)
                    .`as`(metric.median)
            },
        )
            .from(members)
            .asTable(STATS)

        val row = dsl.with(members)
            .select(ranked.asterisk(), stats.asterisk())
            .from(ranked)
            .crossJoin(stats)
            .where(requireNotNull(ranked.field(PEER_TICKER, String::class.java)).eq(ticker.value))
            .fetchOne() ?: return null

        fun metric(spec: PeerMetricSpec): PeerMetric<BigDecimal> {
            val count = row.get(spec.count, Int::class.java) ?: 0
            val enough = count >= MIN_PEERS
            return PeerMetric(
                value = row.get(spec.column, BigDecimal::class.java),
                rank = if (enough) row.get(spec.rank, Int::class.java) else null,
                count = count,
                median = if (enough) row.get(spec.median, BigDecimal::class.java) else null,
            )
        }

        fun PeerMetric<BigDecimal>.whole(): PeerMetric<Long> =
            PeerMetric(value?.toLong(), rank, count, median?.setScale(0, RoundingMode.HALF_UP)?.toLong())

        return PeerComparison(
            memberCount = row.get(PEER_MEMBERS, Int::class.java) ?: 0,
            baseDate = dates.price,
            valuationDate = dates.valuation,
            change = metric(CHANGE_SPEC),
            marketCap = metric(MARKET_CAP_SPEC).whole(),
            tradeValue = metric(TRADE_VALUE_SPEC).whole(),
            per = metric(PER_SPEC),
            pbr = metric(PBR_SPEC),
            roe = metric(ROE_SPEC),
            dividendYield = metric(DIVIDEND_YIELD_SPEC),
        )
    }

    private fun positiveOnly(field: Field<BigDecimal?>): Field<BigDecimal?> =
        DSL.`when`(field.gt(BigDecimal.ZERO), field)

    private fun Table<*>.peerField(name: String): Field<BigDecimal?> = peerField(name, BigDecimal::class.java)

    private fun <T> Table<*>.peerField(name: String, type: Class<T>): Field<T?> =
        requireNotNull(field(name, type))

    private fun fetchStocks(filter: (Stocks) -> Condition, dates: PriceDates): List<Record> =
        dsl.select(
            STOCKS.ID,
            STOCKS.TICKER,
            STOCKS.NAME,
            STOCKS.MARKET,
            PX_PRICE,
            PX_CHANGE,
            PX_CHANGE_AMOUNT,
            PX_TRADE_VALUE,
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
                StockPricing.dailyChange(lc.CLOSE, lc.BASE_PRICE, PREV_CLOSE_FIELD).`as`(CHANGE),
                StockPricing.dailyChangeAmount(lc.CLOSE, lc.BASE_PRICE, PREV_CLOSE_FIELD).`as`(CHANGE_AMOUNT),
                lc.TRADE_VALUE.`as`(TRADE_VALUE),
            )
                .from(lc)
                .leftJoin(prev).on(DSL.trueCondition())
                .where(lc.STOCK_ID.eq(STOCKS.ID).and(lc.TRADE_DATE.eq(dateValue(priceDate))))
                .asTable(PX),
        )
    }

    private fun week52Ranges(priceDate: LocalDate?, candleScope: Condition): Map<Long, Week52Range> {
        if (priceDate == null) return emptyMap()
        val close = STOCK_CANDLES_DAILY.CLOSE
        val epochDay = DSL.localDateDiff(STOCK_CANDLES_DAILY.TRADE_DATE, DSL.inline(LocalDate.EPOCH)).cast(SQLDataType.NUMERIC)
        val highestThenEarliest = DSL.max(DSL.array(close, epochDay.neg()))
        val lowestThenEarliest = DSL.min(DSL.array(close, epochDay))
        return dsl.select(STOCK_CANDLES_DAILY.STOCK_ID, highestThenEarliest, lowestThenEarliest)
            .from(STOCK_CANDLES_DAILY)
            .where(
                STOCK_CANDLES_DAILY.TRADE_DATE.gt(DSL.inline(Week52Range.exclusiveStart(priceDate)))
                    .and(STOCK_CANDLES_DAILY.TRADE_DATE.le(DSL.inline(priceDate)))
                    .and(candleScope),
            )
            .groupBy(STOCK_CANDLES_DAILY.STOCK_ID)
            .fetch { record ->
                val high = requireNotNull(record.get(highestThenEarliest))
                val low = requireNotNull(record.get(lowestThenEarliest))
                requireNotNull(record.get(STOCK_CANDLES_DAILY.STOCK_ID)) to Week52Range(
                    high = requireNotNull(high[0]),
                    highDate = LocalDate.ofEpochDay(requireNotNull(high[1]).negate().longValueExact()),
                    low = requireNotNull(low[0]),
                    lowDate = LocalDate.ofEpochDay(requireNotNull(low[1]).longValueExact()),
                )
            }
            .toMap()
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

    private fun Record.toListView(week52: Week52Range?) = StockListView(
        ticker = requireNotNull(get(STOCKS.TICKER)),
        name = requireNotNull(get(STOCKS.NAME)),
        market = requireNotNull(get(STOCKS.MARKET)),
        price = get(PX_PRICE),
        change = get(PX_CHANGE),
        changeAmount = get(PX_CHANGE_AMOUNT),
        tradeValue = get(PX_TRADE_VALUE),
        week52 = week52,
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
        week52: Week52Range?,
    ) = StockDetailView(
        ticker = requireNotNull(get(STOCKS.TICKER)),
        name = requireNotNull(get(STOCKS.NAME)),
        market = requireNotNull(get(STOCKS.MARKET)),
        price = get(PX_PRICE),
        change = get(PX_CHANGE),
        changeAmount = get(PX_CHANGE_AMOUNT),
        tradeValue = get(PX_TRADE_VALUE),
        week52 = week52,
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
        private const val CHANGE_AMOUNT = "change_amount"
        private const val TRADE_VALUE = "trade_value"
        private const val PREV_CLOSE = "prev_close"
        private const val COMPANY_ID_SOURCE = "cid"
        private const val PEERS = "peers"
        private const val RANKED = "ranked"
        private const val STATS = "stats"
        private const val PEER_TICKER = "ticker"
        private const val PEER_MEMBERS = "member_count"
        private const val PEER_CHANGE = "change"
        private const val PEER_MARKET_CAP = "market_cap"
        private const val PEER_TRADE_VALUE = "trade_value"
        private const val PEER_PER = "per"
        private const val PEER_PBR = "pbr"
        private const val PEER_ROE = "roe"
        private const val PEER_DIVIDEND_YIELD = "dividend_yield"
        private const val MEDIAN = 0.5
        private const val MIN_PEERS = 5
        private const val RATIO_SCALE = 4

        private val CHANGE_SPEC = PeerMetricSpec(PEER_CHANGE, ascending = false, scale = RATIO_SCALE)
        private val MARKET_CAP_SPEC = PeerMetricSpec(PEER_MARKET_CAP, ascending = false, scale = 0)
        private val TRADE_VALUE_SPEC = PeerMetricSpec(PEER_TRADE_VALUE, ascending = false, scale = 0)
        private val PER_SPEC = PeerMetricSpec(PEER_PER, ascending = true, scale = RATIO_SCALE)
        private val PBR_SPEC = PeerMetricSpec(PEER_PBR, ascending = true, scale = RATIO_SCALE)
        private val ROE_SPEC = PeerMetricSpec(PEER_ROE, ascending = false, scale = RATIO_SCALE)
        private val DIVIDEND_YIELD_SPEC = PeerMetricSpec(PEER_DIVIDEND_YIELD, ascending = false, scale = RATIO_SCALE)

        private val PEER_METRICS = listOf(
            CHANGE_SPEC,
            MARKET_CAP_SPEC,
            TRADE_VALUE_SPEC,
            PER_SPEC,
            PBR_SPEC,
            ROE_SPEC,
            DIVIDEND_YIELD_SPEC,
        )
        private const val FOREIGN_RATIO = "foreign_ratio"

        private val HUNDRED: BigDecimal = BigDecimal("100")

        private val EMPTY_PROFILE = CompanyProfile(null, null, null, null, null, null, null, null)

        private val ACTIVE: (Stocks) -> Condition = { s -> s.IS_ACTIVE.eq(true) }

        private val FOREIGN_RATIO_FIELD: Field<BigDecimal?> = DSL.field(DSL.name(FOREIGN_RATIO), SQLDataType.NUMERIC)

        private val PREV_CLOSE_FIELD: Field<BigDecimal?> = DSL.field(DSL.name(PREV, PREV_CLOSE), SQLDataType.NUMERIC)

        private val PX_PRICE: Field<BigDecimal?> = DSL.field(DSL.name(PX, PRICE), SQLDataType.NUMERIC)
        private val PX_CHANGE: Field<BigDecimal?> = DSL.field(DSL.name(PX, CHANGE), SQLDataType.NUMERIC)
        private val PX_CHANGE_AMOUNT: Field<BigDecimal?> = DSL.field(DSL.name(PX, CHANGE_AMOUNT), SQLDataType.NUMERIC)
        private val PX_TRADE_VALUE: Field<Long?> = DSL.field(DSL.name(PX, TRADE_VALUE), SQLDataType.BIGINT)

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

private data class PeerMetricSpec(val column: String, val ascending: Boolean, val scale: Int) {
    val rank = "${column}_rank"
    val count = "${column}_count"
    val median = "${column}_median"
}

private data class PeerColumn(val spec: PeerMetricSpec, val field: Field<BigDecimal?>)
