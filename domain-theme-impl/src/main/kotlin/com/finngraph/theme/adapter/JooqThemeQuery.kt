package com.finngraph.theme.adapter

import com.finngraph.theme.adapter.ThemeQuerySupport.LATEST_VALUATION_DATE
import com.finngraph.theme.adapter.jooq.tables.references.STOCKS
import com.finngraph.theme.adapter.jooq.tables.references.STOCK_CANDLES_DAILY
import com.finngraph.theme.adapter.jooq.tables.references.STOCK_VALUATIONS_DAILY
import com.finngraph.theme.adapter.jooq.tables.references.THEMES
import com.finngraph.theme.adapter.jooq.tables.references.THEME_CANDLES_DAILY
import com.finngraph.theme.adapter.jooq.tables.references.THEME_STOCKS
import com.finngraph.theme.model.MarketStats
import com.finngraph.theme.model.PricingBasis
import com.finngraph.theme.model.PrimaryTheme
import com.finngraph.theme.model.StockObservation
import com.finngraph.theme.model.ThemeAggregation
import com.finngraph.theme.model.ThemeBoard
import com.finngraph.theme.model.ThemeId
import com.finngraph.theme.model.ThemeIdentity
import com.finngraph.theme.model.ThemeIndexStats
import com.finngraph.theme.model.ThemeMember
import com.finngraph.theme.model.ThemeSummary
import com.finngraph.theme.model.TradeValueAverage
import com.finngraph.theme.port.ThemeQueryPort
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

@Component
class JooqThemeQuery(private val dsl: DSLContext) : ThemeQueryPort {

    override fun exists(id: ThemeId): Boolean =
        dsl.fetchExists(dsl.selectOne().from(THEMES).where(THEMES.ID.eq(id.value)))

    override fun board(): ThemeBoard = board(DSL.noCondition(), ThemeQuerySupport.pricingBasis(dsl))

    override fun board(ids: List<ThemeId>): ThemeBoard =
        board(THEMES.ID.`in`(ids.map { it.value }.distinct()), ThemeQuerySupport.pricingBasis(dsl))

    override fun board(basis: PricingBasis): ThemeBoard = board(DSL.noCondition(), basis)

    override fun findAll(): List<ThemeSummary> = board().themes

    override fun findById(id: ThemeId): ThemeSummary? = findByIds(listOf(id))[id]

    override fun findByIds(ids: List<ThemeId>): Map<ThemeId, ThemeSummary> {
        if (ids.isEmpty()) return emptyMap()
        val values = ids.map { it.value }.distinct()
        val memberStocks = DSL.select(THEME_STOCKS.STOCK_ID).from(THEME_STOCKS).where(THEME_STOCKS.THEME_ID.`in`(values))
        val basis = ThemeQuerySupport.pricingBasis(dsl)
        val stocks = ThemeQuerySupport.activeStocks(dsl, basis, STOCKS.ID.`in`(memberStocks))
        val averages = ThemeQuerySupport.averageTradeValues(dsl, basis, STOCK_CANDLES_DAILY.STOCK_ID.`in`(memberStocks))
        return summaries(THEMES.ID.`in`(values), stocks, basis, averages)
            .associateBy { ThemeId(it.id) }
    }

    override fun pricingBasis(): PricingBasis = ThemeQuerySupport.pricingBasis(dsl)

    override fun marketStats(): MarketStats {
        val basis = ThemeQuerySupport.pricingBasis(dsl)
        return marketStats(basis, ThemeQuerySupport.activeStocks(dsl, basis))
    }

    override fun findPrimaryThemeByTickers(tickers: List<String>): Map<String, PrimaryTheme> {
        if (tickers.isEmpty()) return emptyMap()

        val values = tickers.distinct()
        return dsl.select(STOCKS.TICKER, THEMES.ID, THEMES.NAME, THEME_CAP)
            .from(STOCKS)
            .join(THEME_STOCKS).on(THEME_STOCKS.STOCK_ID.eq(STOCKS.ID))
            .join(THEMES).on(THEMES.ID.eq(THEME_STOCKS.THEME_ID))
            .leftJoin(THEME_CAPS).on(CAP_THEME_ID.eq(THEME_STOCKS.THEME_ID))
            .where(STOCKS.TICKER.`in`(values).and(STOCKS.IS_ACTIVE.eq(true)))
            .fetch {
                Membership(
                    ticker = requireNotNull(it.get(STOCKS.TICKER)),
                    themeId = requireNotNull(it.get(THEMES.ID)),
                    themeName = requireNotNull(it.get(THEMES.NAME)),
                    cap = it.get(THEME_CAP),
                )
            }
            .groupBy { it.ticker }
            .mapValues { (_, memberships) ->
                val primary = memberships.sortedWith(PRIMARY_ORDER).first()
                PrimaryTheme(primary.themeId, primary.themeName)
            }
    }

    private fun board(themeFilter: Condition, basis: PricingBasis): ThemeBoard {
        val stocks = ThemeQuerySupport.activeStocks(dsl, basis)
        val averages = ThemeQuerySupport.averageTradeValues(dsl, basis)
        return ThemeBoard(
            basis = basis,
            market = marketStats(basis, stocks),
            themes = summaries(themeFilter, stocks, basis, averages),
        )
    }

    private fun marketStats(basis: PricingBasis, stocks: List<StockObservation>): MarketStats =
        ThemeAggregation.marketStats(basis.baseDate, stocks, basis.prevTradingDate, basis.valuationDate, basis.priceUpdatedAt)

    private fun summaries(
        themeFilter: Condition,
        observations: List<StockObservation>,
        basis: PricingBasis,
        averages: Map<Long, TradeValueAverage>,
    ): List<ThemeSummary> {
        val stocks = observations.associateBy { it.id }
        val indexChanges = indexChanges(themeFilter, basis.baseDate)

        val themes = LinkedHashMap<Long, ThemeIdentity>()
        val members = LinkedHashMap<Long, MutableList<ThemeMember>>()
        dsl.select(THEMES.ID, THEMES.NAME, THEMES.DESCRIPTION, THEMES.SOURCES, THEME_STOCKS.STOCK_ID, THEME_STOCKS.REASON)
            .from(THEMES)
            .leftJoin(THEME_STOCKS).on(THEME_STOCKS.THEME_ID.eq(THEMES.ID))
            .where(themeFilter)
            .orderBy(THEMES.NAME.asc(), THEME_STOCKS.STOCK_ID.asc())
            .fetch()
            .forEach { record ->
                val themeId = requireNotNull(record.get(THEMES.ID))
                themes.getOrPut(themeId) {
                    ThemeIdentity(
                        id = themeId,
                        name = requireNotNull(record.get(THEMES.NAME)),
                        description = record.get(THEMES.DESCRIPTION),
                        sources = record.get(THEMES.SOURCES)?.filterNotNull().orEmpty(),
                    )
                }
                val list = members.getOrPut(themeId) { mutableListOf() }
                val stock = record.get(THEME_STOCKS.STOCK_ID)?.let(stocks::get) ?: return@forEach
                list += ThemeMember(stock, record.get(THEME_STOCKS.REASON))
            }

        return themes.values.map { theme ->
            ThemeAggregation.summary(
                theme,
                members[theme.id].orEmpty(),
                basis.baseDate,
                basis.prevTradingDate,
                averages,
                basis.valuationDate,
                indexChanges[theme.id],
            )
        }
    }

    private fun indexChanges(themeFilter: Condition, baseDate: LocalDate?): Map<Long, BigDecimal> {
        if (baseDate == null) return emptyMap()
        val previous = THEME_CANDLES_DAILY.`as`(PREVIOUS_INDEX_ALIAS)
        val prior = DSL.lateral(
            DSL.select(previous.CLOSE.`as`(PRIOR_CLOSE_NAME))
                .from(previous)
                .where(
                    previous.THEME_ID.eq(THEME_CANDLES_DAILY.THEME_ID)
                        .and(previous.TRADE_DATE.ge(ThemeIndexStats.windowStart(baseDate)))
                        .and(previous.TRADE_DATE.lt(baseDate)),
                )
                .orderBy(previous.TRADE_DATE.desc())
                .limit(1)
                .asTable(PRIOR_INDEX_ALIAS),
        )
        return dsl.select(THEME_CANDLES_DAILY.THEME_ID, THEME_CANDLES_DAILY.CLOSE, PRIOR_CLOSE)
            .from(THEME_CANDLES_DAILY)
            .join(THEMES).on(THEMES.ID.eq(THEME_CANDLES_DAILY.THEME_ID))
            .leftJoin(prior).on(DSL.trueCondition())
            .where(themeFilter.and(THEME_CANDLES_DAILY.TRADE_DATE.eq(baseDate)))
            .fetch()
            .mapNotNull { record ->
                val close = requireNotNull(record.get(THEME_CANDLES_DAILY.CLOSE))
                val change = ThemeIndexStats.dailyChange(close, record.get(PRIOR_CLOSE)) ?: return@mapNotNull null
                requireNotNull(record.get(THEME_CANDLES_DAILY.THEME_ID)) to change
            }
            .toMap()
    }

    private data class Membership(
        val ticker: String,
        val themeId: Long,
        val themeName: String,
        val cap: BigDecimal?,
    )

    companion object {
        private const val THEME_CAPS_ALIAS = "theme_caps"
        private const val CAP_THEME_ID_NAME = "cap_theme_id"
        private const val THEME_CAP_NAME = "theme_cap"
        private const val PREVIOUS_INDEX_ALIAS = "previous_index"
        private const val PRIOR_INDEX_ALIAS = "prior_index"
        private const val PRIOR_CLOSE_NAME = "prior_close"

        private val PRIOR_CLOSE: Field<BigDecimal?> =
            DSL.field(DSL.name(PRIOR_INDEX_ALIAS, PRIOR_CLOSE_NAME), SQLDataType.NUMERIC)

        private val THEME_CAPS = run {
            val ts = THEME_STOCKS.`as`("cap_ts")
            val vd = STOCK_VALUATIONS_DAILY.`as`("cap_vd")
            DSL.select(ts.THEME_ID.`as`(CAP_THEME_ID_NAME), DSL.sum(vd.MARKET_CAP).`as`(THEME_CAP_NAME))
                .from(ts)
                .join(vd).on(vd.LISTING_ID.eq(ts.STOCK_ID).and(vd.TRADE_DATE.eq(LATEST_VALUATION_DATE)))
                .groupBy(ts.THEME_ID)
                .asTable(THEME_CAPS_ALIAS)
        }

        private val CAP_THEME_ID: Field<Long?> =
            DSL.field(DSL.name(THEME_CAPS_ALIAS, CAP_THEME_ID_NAME), SQLDataType.BIGINT)

        private val THEME_CAP: Field<BigDecimal?> =
            DSL.field(DSL.name(THEME_CAPS_ALIAS, THEME_CAP_NAME), SQLDataType.NUMERIC)

        private val PRIMARY_ORDER: Comparator<Membership> =
            compareBy<Membership> { it.cap == null }
                .thenByDescending { it.cap ?: BigDecimal.ZERO }
                .thenBy { it.themeName }
    }
}
