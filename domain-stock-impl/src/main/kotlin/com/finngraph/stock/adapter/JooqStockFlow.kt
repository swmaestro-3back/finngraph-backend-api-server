package com.finngraph.stock.adapter

import com.finngraph.stock.adapter.jooq.tables.references.STOCK_INVESTOR_FLOWS
import com.finngraph.stock.adapter.jooq.tables.references.STOCKS
import com.finngraph.stock.model.InvestorFlow
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockFlowPort
import org.jooq.DSLContext
import org.springframework.stereotype.Component

@Component
class JooqStockFlow(private val dsl: DSLContext) : StockFlowPort {
    override fun findFlows(ticker: Ticker, limit: Int): List<InvestorFlow> =
        dsl.select(
            STOCK_INVESTOR_FLOWS.TRADE_DATE,
            STOCK_INVESTOR_FLOWS.FOREIGN_NET_QTY,
            STOCK_INVESTOR_FLOWS.INSTITUTION_NET_QTY,
            STOCK_INVESTOR_FLOWS.INDIVIDUAL_NET_QTY,
            STOCK_INVESTOR_FLOWS.FOREIGN_HOLD_RATIO,
        )
            .from(STOCK_INVESTOR_FLOWS)
            .join(STOCKS).on(STOCKS.ID.eq(STOCK_INVESTOR_FLOWS.STOCK_ID))
            .where(STOCKS.TICKER.eq(ticker.value))
            .and(STOCKS.IS_ACTIVE.eq(true))
            .orderBy(STOCK_INVESTOR_FLOWS.TRADE_DATE.desc())
            .limit(limit)
            .fetch {
                InvestorFlow(
                    tradeDate = requireNotNull(it.get(STOCK_INVESTOR_FLOWS.TRADE_DATE)),
                    foreignNet = it.get(STOCK_INVESTOR_FLOWS.FOREIGN_NET_QTY),
                    institutionNet = it.get(STOCK_INVESTOR_FLOWS.INSTITUTION_NET_QTY),
                    individualNet = it.get(STOCK_INVESTOR_FLOWS.INDIVIDUAL_NET_QTY),
                    foreignRatio = it.get(STOCK_INVESTOR_FLOWS.FOREIGN_HOLD_RATIO),
                )
            }
            .asReversed()
}
