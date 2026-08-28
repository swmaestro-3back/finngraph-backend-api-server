package com.finngraph.theme.model

import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

data class ThemeSummary(
    val name: String,
    val description: String?,
    val baseDate: LocalDate?,
    val change: BigDecimal?,
    val tradingValue: Long?,
    val marketCap: Long?,
    val w1: BigDecimal?,
    val m1: BigDecimal?,
    val m3: BigDecimal?,
    val stockCount: Int,
    val topStocks: List<ThemeTopStock>,
)

data class ThemeTopStock(
    val ticker: String,
    val name: String,
    val marketCap: Long?,
)

data class ThemeStockView(
    val ticker: String,
    val name: String,
    val market: String,
    val price: BigDecimal?,
    val change: BigDecimal?,
    val tradingValue: Long?,
    val marketCap: Long?,
    val reason: String?,
)

data class NewsRef(
    val id: Long,
    val title: String?,
    val summary: String?,
    val url: String?,
    val publishedAt: OffsetDateTime?,
    val collectedAt: OffsetDateTime?,
)
