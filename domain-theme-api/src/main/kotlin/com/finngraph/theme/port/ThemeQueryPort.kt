package com.finngraph.theme.port

import com.finngraph.theme.model.ThemeName
import com.finngraph.theme.model.ThemeSummary

interface ThemeQueryPort {

    fun findAll(): List<ThemeSummary>

    fun findByName(name: ThemeName): ThemeSummary?

    fun findPrimaryThemeByTickers(tickers: List<String>): Map<String, String>
}
