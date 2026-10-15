package com.finngraph.stock.model

data class StockSearchHit(
    val ticker: String,
    val name: String,
    val market: String,
    val matchedAlias: String?,
)
