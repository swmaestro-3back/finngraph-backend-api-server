package com.finngraph.theme.port

import com.finngraph.theme.model.ThemeId
import com.finngraph.theme.model.ThemeStockView

interface ThemeStockPort {

    fun findStocks(id: ThemeId): List<ThemeStockView>

    fun findTickers(id: ThemeId): List<String>
}
