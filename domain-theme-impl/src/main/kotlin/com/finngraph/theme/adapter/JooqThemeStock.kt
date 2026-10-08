package com.finngraph.theme.adapter

import com.finngraph.theme.adapter.jooq.tables.references.STOCKS
import com.finngraph.theme.adapter.jooq.tables.references.THEMES
import com.finngraph.theme.adapter.jooq.tables.references.THEME_STOCKS
import com.finngraph.theme.model.PricingBasis
import com.finngraph.theme.model.PrimaryTheme
import com.finngraph.theme.model.ThemeAggregation
import com.finngraph.theme.model.ThemeId
import com.finngraph.theme.model.ThemeMember
import com.finngraph.theme.model.ThemeNameMatch
import com.finngraph.theme.model.ThemeStockView
import com.finngraph.theme.port.ThemeStockPort
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Component

@Component
class JooqThemeStock(private val dsl: DSLContext) : ThemeStockPort {

    override fun findStocks(id: ThemeId): List<ThemeStockView> =
        findStocks(listOf(id), ThemeQuerySupport.pricingBasis(dsl))[id].orEmpty()

    override fun findStocks(ids: List<ThemeId>, basis: PricingBasis): Map<ThemeId, List<ThemeStockView>> {
        if (ids.isEmpty()) return emptyMap()
        val values = ids.map { it.value }.distinct()
        val memberStocks = DSL.select(THEME_STOCKS.STOCK_ID).from(THEME_STOCKS).where(THEME_STOCKS.THEME_ID.`in`(values))
        val stocks = ThemeQuerySupport.activeStocks(dsl, basis, STOCKS.ID.`in`(memberStocks)).associateBy { it.id }

        val members = LinkedHashMap<Long, MutableList<ThemeMember>>()
        dsl.select(THEME_STOCKS.THEME_ID, THEME_STOCKS.STOCK_ID, THEME_STOCKS.REASON)
            .from(THEME_STOCKS)
            .where(THEME_STOCKS.THEME_ID.`in`(values))
            .orderBy(THEME_STOCKS.THEME_ID.asc(), THEME_STOCKS.STOCK_ID.asc())
            .fetch()
            .forEach { record ->
                val themeId = requireNotNull(record.get(THEME_STOCKS.THEME_ID))
                val stock = record.get(THEME_STOCKS.STOCK_ID)?.let(stocks::get) ?: return@forEach
                members.getOrPut(themeId) { mutableListOf() } += ThemeMember(stock, record.get(THEME_STOCKS.REASON))
            }

        return values.associate { themeId ->
            ThemeId(themeId) to ThemeAggregation.stockViews(members[themeId].orEmpty(), basis.prevTradingDate)
        }
    }

    override fun findTickers(id: ThemeId): List<String> = findTickers(listOf(id))[id].orEmpty()

    override fun findTickers(ids: List<ThemeId>): Map<ThemeId, List<String>> {
        if (ids.isEmpty()) return emptyMap()
        val values = ids.map { it.value }.distinct()
        val found = dsl.select(THEMES.ID, STOCKS.TICKER)
            .from(THEMES)
            .join(THEME_STOCKS).on(THEME_STOCKS.THEME_ID.eq(THEMES.ID))
            .join(STOCKS).on(STOCKS.ID.eq(THEME_STOCKS.STOCK_ID))
            .where(THEMES.ID.`in`(values).and(STOCKS.IS_ACTIVE.eq(true)))
            .orderBy(THEMES.ID.asc(), STOCKS.TICKER.asc())
            .fetch()
            .groupBy({ requireNotNull(it.get(THEMES.ID)) }, { requireNotNull(it.get(STOCKS.TICKER)) })
        return values.associate { ThemeId(it) to found[it].orEmpty() }
    }

    override fun findTickersByName(query: String): ThemeNameMatch {
        val themes = dsl.select(THEMES.ID, THEMES.NAME)
            .from(THEMES)
            .where(THEMES.NAME.containsIgnoreCase(query))
            .orderBy(THEMES.NAME.asc(), THEMES.ID.asc())
            .fetch { PrimaryTheme(requireNotNull(it.value1()), requireNotNull(it.value2())) }
        if (themes.isEmpty()) return ThemeNameMatch(emptyList(), emptyList())

        val tickers = findTickers(themes.map { ThemeId(it.id) }).values.flatten().distinct().sorted()
        return ThemeNameMatch(themes, tickers)
    }
}
