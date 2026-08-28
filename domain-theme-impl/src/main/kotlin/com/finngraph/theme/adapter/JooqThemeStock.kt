package com.finngraph.theme.adapter

import com.finngraph.theme.adapter.ThemeQuerySupport.BASE_DATE
import com.finngraph.theme.adapter.ThemeQuerySupport.PX_CHANGE
import com.finngraph.theme.adapter.ThemeQuerySupport.PX_PRICE
import com.finngraph.theme.adapter.ThemeQuerySupport.PX_STOCK_ID
import com.finngraph.theme.adapter.ThemeQuerySupport.PX_TRADE_VALUE
import com.finngraph.theme.adapter.jooq.tables.references.STOCKS
import com.finngraph.theme.adapter.jooq.tables.references.THEMES
import com.finngraph.theme.adapter.jooq.tables.references.THEME_STOCKS
import com.finngraph.theme.adapter.jooq.tables.references.VALUATION_DAILY
import com.finngraph.theme.model.ThemeName
import com.finngraph.theme.model.ThemeStockView
import com.finngraph.theme.port.ThemeStockPort
import org.jooq.DSLContext
import org.springframework.stereotype.Component

@Component
class JooqThemeStock(private val dsl: DSLContext) : ThemeStockPort {

    override fun findStocks(name: ThemeName): List<ThemeStockView> =
        dsl.select(
            STOCKS.TICKER,
            STOCKS.NAME,
            STOCKS.MARKET,
            PX_PRICE,
            PX_CHANGE,
            PX_TRADE_VALUE,
            VALUATION_DAILY.MARKET_CAP,
            THEME_STOCKS.REASON,
        )
            .from(THEMES)
            .join(THEME_STOCKS).on(THEME_STOCKS.THEME_ID.eq(THEMES.ID))
            .join(STOCKS).on(STOCKS.ID.eq(THEME_STOCKS.STOCK_ID))
            .leftJoin(VALUATION_DAILY)
            .on(VALUATION_DAILY.LISTING_ID.eq(STOCKS.ID).and(VALUATION_DAILY.TRADE_DATE.eq(BASE_DATE)))
            .leftJoin(ThemeQuerySupport.priceTable()).on(PX_STOCK_ID.eq(STOCKS.ID))
            .where(THEMES.NAME.eq(name.value))
            .orderBy(VALUATION_DAILY.MARKET_CAP.desc().nullsLast(), STOCKS.TICKER.asc())
            .fetch {
                ThemeStockView(
                    ticker = requireNotNull(it.get(STOCKS.TICKER)),
                    name = requireNotNull(it.get(STOCKS.NAME)),
                    market = requireNotNull(it.get(STOCKS.MARKET)),
                    price = it.get(PX_PRICE),
                    change = it.get(PX_CHANGE),
                    tradingValue = it.get(PX_TRADE_VALUE),
                    marketCap = it.get(VALUATION_DAILY.MARKET_CAP),
                    reason = it.get(THEME_STOCKS.REASON),
                )
            }
}
