package com.finngraph.web.composition

import com.finngraph.web.stock.StockDetailResponse
import com.finngraph.web.stock.StockSummaryResponse

import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockQueryPort
import com.finngraph.theme.port.ThemeQueryPort
import org.springframework.stereotype.Component

@Component
class StockThemeComposer(
    private val stockQuery: StockQueryPort,
    private val themeQuery: ThemeQueryPort,
) {

    fun listWithThemes(): List<StockSummaryResponse> {
        val stocks = stockQuery.findAll()
        if (stocks.isEmpty()) return emptyList()

        val primaryThemes = themeQuery.findPrimaryThemeByTickers(stocks.map { it.ticker })
        return stocks.map { StockSummaryResponse.from(it, primaryThemes[it.ticker]) }
    }

    fun detailWithTheme(ticker: Ticker): StockDetailResponse? {
        val found = stockQuery.findByTicker(ticker) ?: return null
        val primaryThemes = themeQuery.findPrimaryThemeByTickers(listOf(found.ticker))
        return StockDetailResponse.from(found, primaryThemes[found.ticker])
    }
}
