package com.finngraph.theme.port

import com.finngraph.theme.model.PrimaryTheme
import com.finngraph.theme.model.ThemeId
import com.finngraph.theme.model.ThemeSummary

interface ThemeQueryPort {

    fun exists(id: ThemeId): Boolean
    fun findAll(): List<ThemeSummary>
    fun findById(id: ThemeId): ThemeSummary?
    fun findPrimaryThemeByTickers(tickers: List<String>): Map<String, PrimaryTheme>
}
