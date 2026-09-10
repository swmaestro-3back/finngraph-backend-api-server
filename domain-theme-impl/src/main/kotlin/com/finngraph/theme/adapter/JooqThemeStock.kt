package com.finngraph.theme.adapter

import com.finngraph.theme.adapter.ThemeQuerySupport.BASE_DATE
import com.finngraph.theme.adapter.ThemeQuerySupport.PX_CHANGE
import com.finngraph.theme.adapter.ThemeQuerySupport.PX_PRICE
import com.finngraph.theme.adapter.ThemeQuerySupport.PX_TRADE_VALUE
import com.finngraph.theme.adapter.jooq.tables.references.STOCKS
import com.finngraph.theme.adapter.jooq.tables.references.THEMES
import com.finngraph.theme.adapter.jooq.tables.references.THEME_STOCKS
import com.finngraph.theme.adapter.jooq.tables.references.STOCK_VALUATIONS_DAILY
import com.finngraph.theme.model.ThemeId
import com.finngraph.theme.model.ThemeStockView
import com.finngraph.theme.port.ThemeStockPort
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Component

@Component
class JooqThemeStock(private val dsl: DSLContext) : ThemeStockPort {

    override fun findStocks(id: ThemeId): List<ThemeStockView> =
        dsl.select(
            STOCKS.TICKER,
            STOCKS.NAME,
            STOCKS.MARKET,
            PX_PRICE,
            PX_CHANGE,
            PX_TRADE_VALUE,
            STOCK_VALUATIONS_DAILY.MARKET_CAP,
            THEME_STOCKS.REASON,
        )
            .from(THEMES)
            .join(THEME_STOCKS).on(THEME_STOCKS.THEME_ID.eq(THEMES.ID))
            .join(STOCKS).on(STOCKS.ID.eq(THEME_STOCKS.STOCK_ID))
            .leftJoin(STOCK_VALUATIONS_DAILY)
            .on(STOCK_VALUATIONS_DAILY.LISTING_ID.eq(STOCKS.ID).and(STOCK_VALUATIONS_DAILY.TRADE_DATE.eq(BASE_DATE)))
            .leftJoin(ThemeQuerySupport.priceTable()).on(DSL.trueCondition())
            .where(THEMES.ID.eq(id.value))
            .orderBy(STOCK_VALUATIONS_DAILY.MARKET_CAP.desc().nullsLast(), STOCKS.TICKER.asc())
            .fetch {
                ThemeStockView(
                    ticker = requireNotNull(it.get(STOCKS.TICKER)),
                    name = requireNotNull(it.get(STOCKS.NAME)),
                    market = requireNotNull(it.get(STOCKS.MARKET)),
                    price = it.get(PX_PRICE),
                    change = it.get(PX_CHANGE),
                    tradingValue = it.get(PX_TRADE_VALUE),
                    marketCap = it.get(STOCK_VALUATIONS_DAILY.MARKET_CAP),
                    reason = it.get(THEME_STOCKS.REASON),
                )
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
