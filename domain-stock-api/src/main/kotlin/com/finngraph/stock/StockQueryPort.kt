package com.finngraph.stock

import java.time.LocalDate

interface StockQueryPort {

    fun findAll(): List<StockListView>

    fun findByTicker(ticker: Ticker): StockDetailView?

    fun findByTickers(tickers: List<Ticker>): Map<Ticker, StockPriceView>

    fun findLatestTradeDate(): LocalDate?
}
