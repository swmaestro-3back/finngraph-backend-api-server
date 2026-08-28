package com.finngraph.stock.port

import com.finngraph.stock.model.Candle
import com.finngraph.stock.model.CandlePeriod
import com.finngraph.stock.model.Ticker

interface StockCandlePort {

    fun findCandles(ticker: Ticker, period: CandlePeriod, limit: Int): List<Candle>
}
