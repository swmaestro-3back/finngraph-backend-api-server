package com.finngraph.web.theme

import com.finngraph.theme.model.MarketStats
import com.finngraph.theme.model.ThemeLeader
import com.finngraph.theme.model.ThemeStockView
import com.finngraph.theme.model.ThemeSummary
import com.finngraph.theme.model.ThemeTopStock
import java.math.BigDecimal
import java.time.LocalDate

data class ThemeSummaryResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val change: BigDecimal?,
    val tradingValue: Long?,
    val avgTradingValue: Long?,
    val tradingValueRatio: BigDecimal?,
    val w1: BigDecimal?,
    val m1: BigDecimal?,
    val m3: BigDecimal?,
    val marketCap: Long?,
    val stockCount: Int,
    val baseDate: LocalDate?,
    val pricedCount: Int,
    val upCount: Int,
    val downCount: Int,
    val flatCount: Int,
    val suspendedCount: Int,
    val trimCount: Int,
    val meanChange: BigDecimal?,
    val changeLower: BigDecimal?,
    val changeUpper: BigDecimal?,
    val sensitivity: BigDecimal?,
    val w1Count: Int,
    val m1Count: Int,
    val m3Count: Int,
    val leaders: List<ThemeLeaderResponse>,
    val sources: List<String>,
    val hotSide: String?,
    val topStocks: List<ThemeTopStockResponse>,
) {
    companion object {
        fun from(summary: ThemeSummary) = ThemeSummaryResponse(
            id = summary.id,
            name = summary.name,
            description = summary.description,
            change = summary.change,
            tradingValue = summary.tradingValue,
            avgTradingValue = summary.avgTradingValue,
            tradingValueRatio = summary.tradingValueRatio,
            w1 = summary.w1,
            m1 = summary.m1,
            m3 = summary.m3,
            marketCap = summary.marketCap,
            stockCount = summary.stockCount,
            baseDate = summary.baseDate,
            pricedCount = summary.pricedCount,
            upCount = summary.upCount,
            downCount = summary.downCount,
            flatCount = summary.flatCount,
            suspendedCount = summary.suspendedCount,
            trimCount = summary.trimCount,
            meanChange = summary.meanChange,
            changeLower = summary.changeLower,
            changeUpper = summary.changeUpper,
            sensitivity = summary.sensitivity,
            w1Count = summary.w1Count,
            m1Count = summary.m1Count,
            m3Count = summary.m3Count,
            leaders = summary.leaders.map(ThemeLeaderResponse::from),
            sources = summary.sources,
            hotSide = summary.hotSide?.name,
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

data class ThemeLeaderResponse(
    val ticker: String,
    val name: String,
    val change: BigDecimal,
) {
    companion object {
        fun from(leader: ThemeLeader) = ThemeLeaderResponse(leader.ticker, leader.name, leader.change)
    }
}

data class ThemeStockResponse(
    val ticker: String,
    val name: String,
    val market: String,
    val price: BigDecimal?,
    val change: BigDecimal?,
    val changeStatus: String,
    val tradingSuspended: Boolean,
    val underAdministration: Boolean,
    val delistingTrade: Boolean,
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
            changeStatus = view.changeStatus.name,
            tradingSuspended = view.tradingSuspended,
            underAdministration = view.underAdministration,
            delistingTrade = view.delistingTrade,
            tradingValue = view.tradingValue,
            marketCap = view.marketCap,
            reason = view.reason,
        )
    }
}

data class ThemeMarketResponse(
    val baseDate: LocalDate?,
    val pricedCount: Int,
    val upCount: Int,
    val downCount: Int,
    val flatCount: Int,
    val medianChange: BigDecimal?,
    val upRatio: BigDecimal?,
    val downRatio: BigDecimal?,
    val coverage: BigDecimal?,
) {
    companion object {
        fun from(stats: MarketStats) = ThemeMarketResponse(
            baseDate = stats.baseDate,
            pricedCount = stats.pricedCount,
            upCount = stats.upCount,
            downCount = stats.downCount,
            flatCount = stats.flatCount,
            medianChange = stats.medianChange,
            upRatio = stats.upRatio,
            downRatio = stats.downRatio,
            coverage = stats.coverage,
        )
    }
}
