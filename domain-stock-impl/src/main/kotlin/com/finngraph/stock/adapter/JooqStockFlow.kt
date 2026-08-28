package com.finngraph.stock.adapter

import com.finngraph.stock.InvestorFlow
import com.finngraph.stock.StockFlowPort
import com.finngraph.stock.Ticker
import com.finngraph.stock.adapter.jooq.tables.references.INVESTOR_FLOWS
import com.finngraph.stock.adapter.jooq.tables.references.STOCKS
import org.jooq.DSLContext
import org.springframework.stereotype.Component

@Component
class JooqStockFlow(private val dsl: DSLContext) : StockFlowPort {
    override fun findFlows(ticker: Ticker, limit: Int): List<InvestorFlow> =
        dsl.select(
            INVESTOR_FLOWS.TRADE_DATE,
            INVESTOR_FLOWS.FOREIGN_NET,
            INVESTOR_FLOWS.INSTITUTION_NET,
            INVESTOR_FLOWS.PENSION_NET,
            INVESTOR_FLOWS.PERSONAL_NET,
            INVESTOR_FLOWS.FOREIGN_RATIO,
        )
            .from(INVESTOR_FLOWS)
            .join(STOCKS).on(STOCKS.ID.eq(INVESTOR_FLOWS.STOCK_ID))
            .where(STOCKS.TICKER.eq(ticker.value))
            .and(STOCKS.IS_ACTIVE.eq(true))
            .orderBy(INVESTOR_FLOWS.TRADE_DATE.desc())
            .limit(limit)
            .fetch {
                InvestorFlow(
                    tradeDate = requireNotNull(it.get(INVESTOR_FLOWS.TRADE_DATE)),
                    foreignNet = it.get(INVESTOR_FLOWS.FOREIGN_NET),
                    institutionNet = it.get(INVESTOR_FLOWS.INSTITUTION_NET),
                    pensionNet = it.get(INVESTOR_FLOWS.PENSION_NET),
                    personalNet = it.get(INVESTOR_FLOWS.PERSONAL_NET),
                    foreignRatio = it.get(INVESTOR_FLOWS.FOREIGN_RATIO),
                )
            }
            .asReversed()
}
