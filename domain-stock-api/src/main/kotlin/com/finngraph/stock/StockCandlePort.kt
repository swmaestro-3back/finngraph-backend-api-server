package com.finngraph.stock

interface StockCandlePort {

    fun findCandles(ticker: Ticker, period: CandlePeriod, limit: Int): List<Candle>
}
