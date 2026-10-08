package com.finngraph.theme.port

import com.finngraph.theme.model.PricingBasis
import com.finngraph.theme.model.ThemeId
import com.finngraph.theme.model.ThemeNameMatch
import com.finngraph.theme.model.ThemeStockView

interface ThemeStockPort {

    fun findStocks(id: ThemeId): List<ThemeStockView>

    fun findStocks(ids: List<ThemeId>, basis: PricingBasis): Map<ThemeId, List<ThemeStockView>>

    fun findTickers(id: ThemeId): List<String>

    fun findTickers(ids: List<ThemeId>): Map<ThemeId, List<String>>

    fun findTickersByName(query: String): ThemeNameMatch
}
