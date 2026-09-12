package com.finngraph.composition

import com.finngraph.stock.model.StockDetailView
import com.finngraph.stock.model.StockListView
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockQueryPort
import com.finngraph.theme.model.PrimaryTheme
import com.finngraph.theme.port.ThemeQueryPort
import org.springframework.stereotype.Component

data class StockWithTheme(
    val stock: StockListView,
    val primaryTheme: PrimaryTheme?,
)

data class StockDetailWithTheme(
    val stock: StockDetailView,
    val primaryTheme: PrimaryTheme?,
)

@Component
class StockThemeComposer(
    private val stockQuery: StockQueryPort,
    private val themeQuery: ThemeQueryPort,
) {

    fun listWithThemes(): List<StockWithTheme> {
        val stocks = stockQuery.findAll()
        if (stocks.isEmpty()) return emptyList()

        val primaryThemes = themeQuery.findPrimaryThemeByTickers(stocks.map { it.ticker })
        return stocks.map { StockWithTheme(it, primaryThemes[it.ticker]) }
    }

    fun detailWithTheme(ticker: Ticker): StockDetailWithTheme? {
        val found = stockQuery.findByTicker(ticker) ?: return null
        val primaryThemes = themeQuery.findPrimaryThemeByTickers(listOf(found.ticker))
        return StockDetailWithTheme(found, primaryThemes[found.ticker])
    }
}
