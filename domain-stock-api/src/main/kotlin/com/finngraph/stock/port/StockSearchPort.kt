package com.finngraph.stock.port

import com.finngraph.stock.model.StockSearchHit

interface StockSearchPort {

    fun search(query: String, limit: Int): List<StockSearchHit>
}
