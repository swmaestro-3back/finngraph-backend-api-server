package com.finngraph.stock.port

import com.finngraph.stock.model.DividendRecord
import com.finngraph.stock.model.Ticker

interface StockDividendPort {

    fun findDividends(ticker: Ticker): List<DividendRecord>
}
