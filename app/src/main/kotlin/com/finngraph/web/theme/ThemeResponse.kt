package com.finngraph.web.theme

import com.finngraph.theme.model.ThemeStockView
import com.finngraph.theme.model.ThemeSummary
import com.finngraph.theme.model.ThemeTopStock
import java.math.BigDecimal

data class ThemeSummaryResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val change: BigDecimal?,
    val tradingValue: Long?,
    val w1: BigDecimal?,
    val m1: BigDecimal?,
    val m3: BigDecimal?,
    val marketCap: Long?,
    val stockCount: Int,
    val topStocks: List<ThemeTopStockResponse>,
) {
    companion object {
        fun from(summary: ThemeSummary) = ThemeSummaryResponse(
            id = summary.id,
            name = summary.name,
            description = summary.description,
            change = summary.change,
            tradingValue = summary.tradingValue,
            w1 = summary.w1,
            m1 = summary.m1,
            m3 = summary.m3,
            marketCap = summary.marketCap,
            stockCount = summary.stockCount,
            topStocks = summary.topStocks.map(ThemeTopStockResponse::from),
        )
    }
}

data class ThemeTopStockResponse(
    val ticker: String,
    val name: String,
) {
    companion object {
        fun from(stock: ThemeTopStock) = ThemeTopStockResponse(stock.ticker, stock.name)
    }
}

data class ThemeStockResponse(
    val ticker: String,
    val name: String,
    val market: String,
    val price: BigDecimal?,
    val change: BigDecimal?,
    val tradingValue: Long?,
    val marketCap: Long?,
    val reason: String?,
) {
    companion object {
        fun from(view: ThemeStockView) = ThemeStockResponse(
            ticker = view.ticker,
            name = view.name,
            market = view.market,
            price = view.price,
            change = view.change,
            tradingValue = view.tradingValue,
            marketCap = view.marketCap,
            reason = view.reason,
        )
    }
}
