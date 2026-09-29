package com.finngraph.theme.adapter

import com.finngraph.theme.adapter.jooq.tables.references.STOCKS
import com.finngraph.theme.adapter.jooq.tables.references.THEMES
import com.finngraph.theme.adapter.jooq.tables.references.THEME_STOCKS
import com.finngraph.theme.model.PricingBasis
import com.finngraph.theme.model.ThemeAggregation
import com.finngraph.theme.model.ThemeId
import com.finngraph.theme.model.ThemeMember
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
        val stocks = ThemeQuerySupport.activeStocks(dsl, basis.baseDate, STOCKS.ID.`in`(memberStocks)).associateBy { it.id }

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

    override fun findTickers(id: ThemeId): List<String> =
        dsl.select(STOCKS.TICKER)
            .from(THEMES)
            .join(THEME_STOCKS).on(THEME_STOCKS.THEME_ID.eq(THEMES.ID))
            .join(STOCKS).on(STOCKS.ID.eq(THEME_STOCKS.STOCK_ID))
            .where(THEMES.ID.eq(id.value).and(STOCKS.IS_ACTIVE.eq(true)))
            .orderBy(STOCKS.TICKER.asc())
            .fetch { requireNotNull(it.get(STOCKS.TICKER)) }
}
