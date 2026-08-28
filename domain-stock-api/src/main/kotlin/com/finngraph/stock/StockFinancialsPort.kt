package com.finngraph.stock

interface StockFinancialsPort {

    fun findAnnual(ticker: Ticker): List<AnnualFinancials>
}
