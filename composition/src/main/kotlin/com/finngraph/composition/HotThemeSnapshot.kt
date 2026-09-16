package com.finngraph.composition

import java.math.BigDecimal
import java.time.LocalDate

data class HotThemeSnapshot(
    val tradeDate: LocalDate?,
    val themes: List<HotTheme>,
)

data class HotTheme(
    val id: Long,
    val name: String,
    val change: BigDecimal,
    val stocks: List<HotThemeStock>,
)

data class HotThemeStock(
    val ticker: String,
    val name: String,
)
