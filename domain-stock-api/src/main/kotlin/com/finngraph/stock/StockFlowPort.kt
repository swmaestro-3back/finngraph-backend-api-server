package com.finngraph.stock

interface StockFlowPort {

    fun findFlows(ticker: Ticker, limit: Int): List<InvestorFlow>
}
