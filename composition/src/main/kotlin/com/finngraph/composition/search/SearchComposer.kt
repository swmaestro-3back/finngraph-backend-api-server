package com.finngraph.composition.search

import com.finngraph.composition.issue.IssueComposer
import com.finngraph.composition.issue.IssueSummary
import com.finngraph.stock.model.StockSearchHit
import com.finngraph.stock.port.StockSearchPort
import com.finngraph.theme.model.PrimaryTheme
import com.finngraph.theme.port.ThemeStockPort
import org.springframework.stereotype.Component

data class SearchResult(
    val stocks: List<StockSearchHit>,
    val themes: List<PrimaryTheme>,
    val issues: List<IssueSummary>,
)

@Component
class SearchComposer(
    private val stockSearch: StockSearchPort,
    private val themeStock: ThemeStockPort,
    private val issues: IssueComposer,
) {

    fun search(query: String, limit: Int): SearchResult = SearchResult(
        stocks = stockSearch.search(query, limit),
        themes = themeStock.findThemesByName(query)
            .sortedWith(
                compareBy<PrimaryTheme> {
                    when {
                        it.name.equals(query, ignoreCase = true) -> 0
                        it.name.startsWith(query, ignoreCase = true) -> 1
                        else -> 2
                    }
                }.thenBy { it.name.length }.thenBy { it.name },
            )
            .take(limit),
        issues = issues.search(query, limit),
    )
}
