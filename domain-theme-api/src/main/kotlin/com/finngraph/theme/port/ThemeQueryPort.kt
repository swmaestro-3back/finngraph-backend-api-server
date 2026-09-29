package com.finngraph.theme.port

import com.finngraph.theme.model.MarketStats
import com.finngraph.theme.model.PricingBasis
import com.finngraph.theme.model.PrimaryTheme
import com.finngraph.theme.model.ThemeBoard
import com.finngraph.theme.model.ThemeId
import com.finngraph.theme.model.ThemeSummary

interface ThemeQueryPort {

    fun exists(id: ThemeId): Boolean
    fun board(): ThemeBoard
    fun board(ids: List<ThemeId>): ThemeBoard
    fun findAll(): List<ThemeSummary>
    fun findById(id: ThemeId): ThemeSummary?
    fun findByIds(ids: List<ThemeId>): Map<ThemeId, ThemeSummary>
    fun findPrimaryThemeByTickers(tickers: List<String>): Map<String, PrimaryTheme>
    fun marketStats(): MarketStats
    fun pricingBasis(): PricingBasis
}
