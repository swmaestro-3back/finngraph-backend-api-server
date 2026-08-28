package com.finngraph.stock.port

import com.finngraph.stock.model.InvestorFlow
import com.finngraph.stock.model.Ticker

interface StockFlowPort {

    fun findFlows(ticker: Ticker, limit: Int): List<InvestorFlow>
}
