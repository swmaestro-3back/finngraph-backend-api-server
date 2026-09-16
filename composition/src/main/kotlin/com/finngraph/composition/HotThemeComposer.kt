package com.finngraph.composition

import com.finngraph.theme.HotThemeSelector
import com.finngraph.theme.model.ThemeId
import com.finngraph.theme.model.ThemeSummary
import com.finngraph.theme.port.ThemeQueryPort
import com.finngraph.theme.port.ThemeStockPort
import org.springframework.stereotype.Component

@Component
class HotThemeComposer(
    private val themeQuery: ThemeQueryPort,
    private val themeStock: ThemeStockPort,
) {

    fun hot(count: Int): List<ThemeSummary> =
        HotThemeSelector.select(themeQuery.findAll(), count)

    fun hotForEtl(count: Int): HotThemeSnapshot {
        val selected = hot(count)
        return HotThemeSnapshot(
            tradeDate = selected.firstNotNullOfOrNull { it.baseDate },
            themes = selected.map { summary ->
                HotTheme(
                    id = summary.id,
                    name = summary.name,
                    change = requireNotNull(summary.change),
                    stocks = themeStock.findStocks(ThemeId(summary.id))
                        .map { HotThemeStock(it.ticker, it.name) },
                )
            },
        )
    }
}
