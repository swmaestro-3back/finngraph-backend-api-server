package com.finngraph.theme.model

import java.math.BigDecimal
import java.time.LocalDate

enum class IndexPeriod { D, W, M }

data class ThemeIndexCandle(
    val date: LocalDate,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: Long,
    val tradeValue: Long?,
)

data class ThemeIndexClose(
    val date: LocalDate,
    val close: BigDecimal,
)

data class ThemeIndexSummary(
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
)
