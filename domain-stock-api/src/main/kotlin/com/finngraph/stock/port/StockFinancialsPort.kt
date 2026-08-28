package com.finngraph.stock.port

import com.finngraph.stock.model.AnnualFinancials
import com.finngraph.stock.model.Ticker

interface StockFinancialsPort {

    fun findAnnual(ticker: Ticker): List<AnnualFinancials>
}
