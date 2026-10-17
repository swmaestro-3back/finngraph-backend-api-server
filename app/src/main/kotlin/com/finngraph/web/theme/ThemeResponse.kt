package com.finngraph.web.theme

import com.finngraph.theme.model.MarketStats
import com.finngraph.theme.model.ThemeIndexCandle
import com.finngraph.theme.model.ThemeIndexSummary
import com.finngraph.theme.model.ThemeLeader
import com.finngraph.theme.model.ThemeNameMatch
import com.finngraph.theme.model.ThemeStockView
import com.finngraph.theme.model.ThemeSummary
import com.finngraph.theme.model.ThemeTopStock
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

const val WEIGHTED_CHANGE_DESCRIPTION =
    "테마 지수(시가총액 가중, 종목당 비중 상한 25%, 4종목 이하는 동일 가중)의 기준일 등락률(%). " +
        "/themes/{id}/index의 change와 같은 값. change(10% 절사평균)와 다른 지표"

data class ThemeSummaryResponse(
    val id: Long,
    val name: String,
    val description: String?,
    val change: BigDecimal?,
    @Schema(description = WEIGHTED_CHANGE_DESCRIPTION)
    val weightedChange: BigDecimal?,
    val tradingValue: Long?,
    val avgTradingValue: Long?,
    val tradingValueRatio: BigDecimal?,
    val w1: BigDecimal?,
    val m1: BigDecimal?,
    val m3: BigDecimal?,
    val marketCap: Long?,
    val stockCount: Int,
    val baseDate: LocalDate?,
    val valuationDate: LocalDate?,
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
            weightedChange = summary.weightedChange,
            tradingValue = summary.tradingValue,
            avgTradingValue = summary.avgTradingValue,
            tradingValueRatio = summary.tradingValueRatio,
            w1 = summary.w1,
            m1 = summary.m1,
            m3 = summary.m3,
            marketCap = summary.marketCap,
            stockCount = summary.stockCount,
            baseDate = summary.baseDate,
            valuationDate = summary.valuationDate,
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
    val r1w: BigDecimal?,
    val r1m: BigDecimal?,
    val r3m: BigDecimal?,
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
            r1w = view.r1w,
            r1m = view.r1m,
            r3m = view.r3m,
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
    val valuationDate: LocalDate?,
    val updatedAt: OffsetDateTime?,
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
            valuationDate = stats.valuationDate,
            updatedAt = stats.updatedAt,
        )
    }
}

data class ThemeIndexCandleResponse(
    val date: LocalDate,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: Long,
    val tradeValue: Long?,
) {
    companion object {
        fun from(candle: ThemeIndexCandle) = ThemeIndexCandleResponse(
            date = candle.date,
            open = candle.open,
            high = candle.high,
            low = candle.low,
            close = candle.close,
            volume = candle.volume,
            tradeValue = candle.tradeValue,
        )
    }
}

data class ThemeIndexResponse(
    val date: LocalDate,
    val close: BigDecimal,
    val change: BigDecimal?,
    val r1w: BigDecimal?,
    val r1m: BigDecimal?,
    val r3m: BigDecimal?,
    val r1y: BigDecimal?,
    val ytd: BigDecimal?,
    val high52w: BigDecimal,
    val low52w: BigDecimal,
    val fromHigh52w: BigDecimal?,
    val streak: Int,
) {
    companion object {
        fun from(summary: ThemeIndexSummary) = ThemeIndexResponse(
            date = summary.date,
            close = summary.close,
            change = summary.change,
            r1w = summary.r1w,
            r1m = summary.r1m,
            r3m = summary.r3m,
            r1y = summary.r1y,
            ytd = summary.ytd,
            high52w = summary.high52w,
            low52w = summary.low52w,
            fromHigh52w = summary.fromHigh52w,
            streak = summary.streak,
        )
    }
}

data class ThemeRefResponse(
    val id: Long,
    val name: String,
)

data class ThemeTickersResponse(
    val query: String,
    val themes: List<ThemeRefResponse>,
    val tickers: List<String>,
) {
    companion object {
        fun from(query: String, match: ThemeNameMatch) = ThemeTickersResponse(
            query = query,
            themes = match.themes.map { ThemeRefResponse(it.id, it.name) },
            tickers = match.tickers,
        )
    }
}
