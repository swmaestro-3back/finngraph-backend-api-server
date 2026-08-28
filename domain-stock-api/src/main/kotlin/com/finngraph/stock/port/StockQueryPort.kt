package com.finngraph.stock.port

import com.finngraph.stock.model.StockDetailView
import com.finngraph.stock.model.StockListView
import com.finngraph.stock.model.StockPriceView
import com.finngraph.stock.model.Ticker
import java.time.LocalDate

interface StockQueryPort {

    fun findAll(): List<StockListView>

    fun findByTicker(ticker: Ticker): StockDetailView?

    fun findByTickers(tickers: List<Ticker>): Map<Ticker, StockPriceView>

    fun findLatestTradeDate(): LocalDate?
}
