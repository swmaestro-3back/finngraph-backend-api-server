package com.finngraph.theme.adapter

import com.finngraph.theme.adapter.ThemeQuerySupport.BASE_DATE
import com.finngraph.theme.adapter.ThemeQuerySupport.BASE_DATE_COLUMN
import com.finngraph.theme.adapter.ThemeQuerySupport.DERIVED_SCALE
import com.finngraph.theme.adapter.ThemeQuerySupport.PX_CHANGE
import com.finngraph.theme.adapter.ThemeQuerySupport.PX_TRADE_VALUE
import com.finngraph.theme.adapter.jooq.tables.references.STOCKS
import com.finngraph.theme.adapter.jooq.tables.references.THEMES
import com.finngraph.theme.adapter.jooq.tables.references.THEME_STOCKS
import com.finngraph.theme.adapter.jooq.tables.references.STOCK_VALUATIONS_DAILY
import com.finngraph.theme.model.PrimaryTheme
import com.finngraph.theme.model.ThemeId
import com.finngraph.theme.model.ThemeSummary
import com.finngraph.theme.model.ThemeTopStock
import com.finngraph.theme.port.ThemeQueryPort
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class JooqThemeQuery(private val dsl: DSLContext) : ThemeQueryPort {

    override fun exists(id: ThemeId): Boolean =
        dsl.fetchExists(dsl.selectOne().from(THEMES).where(THEMES.ID.eq(id.value)))

    override fun findAll(): List<ThemeSummary> = summaries(DSL.noCondition())

    override fun findById(id: ThemeId): ThemeSummary? =
        summaries(THEMES.ID.eq(id.value)).firstOrNull()

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

    private fun summaries(nameFilter: Condition): List<ThemeSummary> {
        val top = topStocks(nameFilter)
        return dsl.select(
            THEMES.ID,
            THEMES.NAME,
            THEMES.DESCRIPTION,
            BASE_DATE_COLUMN,
            STOCK_COUNT,
            MARKET_CAP_SUM,
            TRADING_VALUE_SUM,
            CHANGE_WEIGHTED,
            W1_WEIGHTED,
            M1_WEIGHTED,
            M3_WEIGHTED,
        )
            .from(THEMES)
            .leftJoin(THEME_STOCKS).on(THEME_STOCKS.THEME_ID.eq(THEMES.ID))
            .leftJoin(STOCKS).on(STOCKS.ID.eq(THEME_STOCKS.STOCK_ID))
            .leftJoin(STOCK_VALUATIONS_DAILY)
            .on(STOCK_VALUATIONS_DAILY.LISTING_ID.eq(STOCKS.ID).and(STOCK_VALUATIONS_DAILY.TRADE_DATE.eq(BASE_DATE)))
            .leftJoin(ThemeQuerySupport.priceTable()).on(DSL.trueCondition())
            .where(nameFilter)
            .groupBy(THEMES.ID, THEMES.NAME, THEMES.DESCRIPTION)
            .orderBy(THEMES.NAME.asc())
            .fetch { it.toSummary(top) }
    }

    private fun topStocks(nameFilter: Condition): Map<String, List<ThemeTopStock>> =
        dsl.select(THEME_NAME, STOCK_TICKER, STOCK_NAME, STOCK_VALUATIONS_DAILY.MARKET_CAP)
            .from(THEMES)
            .join(THEME_STOCKS).on(THEME_STOCKS.THEME_ID.eq(THEMES.ID))
            .join(STOCKS).on(STOCKS.ID.eq(THEME_STOCKS.STOCK_ID))
            .leftJoin(STOCK_VALUATIONS_DAILY)
            .on(STOCK_VALUATIONS_DAILY.LISTING_ID.eq(STOCKS.ID).and(STOCK_VALUATIONS_DAILY.TRADE_DATE.eq(BASE_DATE)))
            .where(nameFilter)
            .orderBy(THEME_NAME.asc(), STOCK_VALUATIONS_DAILY.MARKET_CAP.desc().nullsLast(), STOCKS.ID.asc())
            .fetch()
            .groupBy(
                { requireNotNull(it.get(THEME_NAME)) },
                {
                    ThemeTopStock(
                        ticker = requireNotNull(it.get(STOCK_TICKER)),
                        name = requireNotNull(it.get(STOCK_NAME)),
                        marketCap = it.get(STOCK_VALUATIONS_DAILY.MARKET_CAP),
                    )
                },
            )
            .mapValues { (_, stocks) -> stocks.take(TOP_STOCK_COUNT) }

    private fun Record.toSummary(top: Map<String, List<ThemeTopStock>>): ThemeSummary {
        val name = requireNotNull(get(THEMES.NAME))
        return ThemeSummary(
            id = requireNotNull(get(THEMES.ID)),
            name = name,
            description = get(THEMES.DESCRIPTION),
            baseDate = get(BASE_DATE_COLUMN),
            change = get(CHANGE_WEIGHTED),
            tradingValue = get(TRADING_VALUE_SUM)?.toLong(),
            marketCap = get(MARKET_CAP_SUM)?.toLong(),
            w1 = get(W1_WEIGHTED),
            m1 = get(M1_WEIGHTED),
            m3 = get(M3_WEIGHTED),
            stockCount = requireNotNull(get(STOCK_COUNT)),
            topStocks = top[name] ?: emptyList(),
        )
    }

    private data class Membership(
        val ticker: String,
        val themeId: Long,
        val themeName: String,
        val cap: BigDecimal?,
    )

    companion object {
        private const val TOP_STOCK_COUNT = 3

        private val THEME_NAME = THEMES.NAME.`as`("theme_name")
        private val STOCK_TICKER = STOCKS.TICKER.`as`("stock_ticker")
        private val STOCK_NAME = STOCKS.NAME.`as`("stock_name")

        private val CAP_NUMERIC: Field<BigDecimal?> = STOCK_VALUATIONS_DAILY.MARKET_CAP.cast(SQLDataType.NUMERIC)

        private val STOCK_COUNT = DSL.count(THEME_STOCKS.STOCK_ID).`as`("stock_count")
        private val MARKET_CAP_SUM = DSL.sum(STOCK_VALUATIONS_DAILY.MARKET_CAP).`as`("market_cap_sum")
        private val TRADING_VALUE_SUM = DSL.sum(PX_TRADE_VALUE).`as`("trading_value_sum")

        private val CHANGE_WEIGHTED = weighted(PX_CHANGE).`as`("change")
        private val W1_WEIGHTED = weighted(STOCK_VALUATIONS_DAILY.R_1W).`as`("w1")
        private val M1_WEIGHTED = weighted(STOCK_VALUATIONS_DAILY.R_1M).`as`("m1")
        private val M3_WEIGHTED = weighted(STOCK_VALUATIONS_DAILY.R_3M).`as`("m3")

        private fun weighted(metric: Field<BigDecimal?>): Field<BigDecimal?> =
            DSL.round(
                DSL.sum(metric.times(CAP_NUMERIC))
                    .div(DSL.nullif(DSL.sum(CAP_NUMERIC).filterWhere(metric.isNotNull), BigDecimal.ZERO)),
                DERIVED_SCALE,
            )

        private const val THEME_CAPS_ALIAS = "theme_caps"
        private const val CAP_THEME_ID_NAME = "cap_theme_id"
        private const val THEME_CAP_NAME = "theme_cap"

        private val THEME_CAPS = run {
            val ts = THEME_STOCKS.`as`("cap_ts")
            val vd = STOCK_VALUATIONS_DAILY.`as`("cap_vd")
            DSL.select(ts.THEME_ID.`as`(CAP_THEME_ID_NAME), DSL.sum(vd.MARKET_CAP).`as`(THEME_CAP_NAME))
                .from(ts)
                .join(vd).on(vd.LISTING_ID.eq(ts.STOCK_ID).and(vd.TRADE_DATE.eq(BASE_DATE)))
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
