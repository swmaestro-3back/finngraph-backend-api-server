package com.finngraph.theme.port

import com.finngraph.theme.model.ThemeName
import com.finngraph.theme.model.ThemeStockView

interface ThemeStockPort {

    fun findStocks(name: ThemeName): List<ThemeStockView>
}
