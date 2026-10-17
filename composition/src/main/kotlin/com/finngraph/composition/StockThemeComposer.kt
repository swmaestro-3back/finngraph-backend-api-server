package com.finngraph.composition

import com.finngraph.stock.model.PeerComparison
import com.finngraph.stock.model.StockDetailView
import com.finngraph.stock.model.StockListView
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockQueryPort
import com.finngraph.theme.model.PrimaryTheme
import com.finngraph.theme.model.ThemeId
import com.finngraph.theme.port.ThemeQueryPort
import com.finngraph.theme.port.ThemeStockPort
import java.math.BigDecimal
import org.springframework.stereotype.Component

data class StockWithTheme(
    val stock: StockListView,
    val primaryTheme: PrimaryTheme?,
)

data class StockDetailWithTheme(
    val stock: StockDetailView,
    val primaryTheme: PrimaryTheme?,
)

data class StockThemeEntry(
    val id: Long,
    val name: String,
    val stockCount: Int,
    val change: BigDecimal?,
    val primary: Boolean,
)

data class StockThemeComparison(
    val themeId: Long,
    val themeName: String,
    val comparison: PeerComparison,
)

@Component
class StockThemeComposer(
    private val stockQuery: StockQueryPort,
    private val themeQuery: ThemeQueryPort,
    private val themeStock: ThemeStockPort,
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

    fun themesOf(ticker: Ticker): List<StockThemeEntry> {
        val themes = themeQuery.findThemesByTicker(ticker.value)
        if (themes.isEmpty()) return emptyList()

        val summaries = themeQuery.findByIds(themes.map { ThemeId(it.id) })
        return themes.mapIndexed { index, theme ->
            val summary = summaries[ThemeId(theme.id)]
            StockThemeEntry(
                id = theme.id,
                name = theme.name,
                stockCount = summary?.stockCount ?: 0,
                change = summary?.change,
                primary = index == 0,
            )
        }
    }

    fun compareInTheme(ticker: Ticker, themeId: ThemeId): StockThemeComparison? {
        val theme = themeQuery.findThemesByTicker(ticker.value).firstOrNull { it.id == themeId.value } ?: return null
        val peers = themeStock.findTickers(themeId).map(::Ticker)
        val comparison = stockQuery.compareWithin(ticker, peers) ?: return null
        return StockThemeComparison(theme.id, theme.name, comparison)
    }
}
