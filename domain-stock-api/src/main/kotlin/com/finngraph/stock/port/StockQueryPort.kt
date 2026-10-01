package com.finngraph.stock.port

import com.finngraph.stock.model.StockDetailView
import com.finngraph.stock.model.StockFlags
import com.finngraph.stock.model.StockListView
import com.finngraph.stock.model.StockPriceView
import com.finngraph.stock.model.Ticker
import java.time.LocalDate

interface StockQueryPort {

    fun exists(ticker: Ticker): Boolean
    fun findAll(): List<StockListView>
    fun findByTicker(ticker: Ticker): StockDetailView?
    fun findByTickers(tickers: List<Ticker>, asOf: LocalDate? = null): Map<Ticker, StockPriceView>
    fun findLatestTradeDate(): LocalDate?
    fun findFlagged(): List<StockFlags>
}
