package com.finngraph.theme.model

import java.math.BigDecimal
import java.time.LocalDate

enum class HotSide { UP, DOWN }

enum class ChangeStatus { PRICED, TRIMMED, SUSPENDED, DELISTING, NO_CANDLE, NO_PREV }

data class ThemeSummary(
    val id: Long,
    val name: String,
    val description: String?,
    val baseDate: LocalDate?,
    val change: BigDecimal?,
    val tradingValue: Long?,
    val avgTradingValue: Long?,
    val tradingValueRatio: BigDecimal?,
    val marketCap: Long?,
    val w1: BigDecimal?,
    val m1: BigDecimal?,
    val m3: BigDecimal?,
    val stockCount: Int,
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
    val leaders: List<ThemeLeader>,
    val sources: List<String>,
    val hotSide: HotSide?,
    val topStocks: List<ThemeTopStock>,
    val valuationDate: LocalDate? = baseDate,
)

data class ThemeBoard(
    val basis: PricingBasis,
    val market: MarketStats,
    val themes: List<ThemeSummary>,
)

data class TradeValueAverage(
    val stockId: Long,
    val average: BigDecimal,
    val dayCount: Int,
)

data class ThemeTopStock(
    val ticker: String,
    val name: String,
    val marketCap: Long?,
)

data class ThemeLeader(
    val ticker: String,
    val name: String,
    val change: BigDecimal,
)

data class PrimaryTheme(
    val id: Long,
    val name: String,
)

data class ThemeStockView(
    val ticker: String,
    val name: String,
    val market: String,
    val price: BigDecimal?,
    val change: BigDecimal?,
    val changeStatus: ChangeStatus,
    val tradingSuspended: Boolean,
    val underAdministration: Boolean,
    val delistingTrade: Boolean,
    val tradingValue: Long?,
    val marketCap: Long?,
    val reason: String?,
)
