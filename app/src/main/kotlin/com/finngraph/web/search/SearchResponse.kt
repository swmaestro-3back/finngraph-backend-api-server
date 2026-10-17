package com.finngraph.web.search

import com.finngraph.composition.search.SearchResult
import com.finngraph.stock.model.StockSearchHit
import com.finngraph.theme.model.PrimaryTheme
import com.finngraph.web.issue.IssueSummaryResponse
import io.swagger.v3.oas.annotations.media.Schema

data class SearchResponse(
    @Schema(description = "앞뒤 공백을 지운 검색어")
    val query: String,
    val stocks: List<SearchStockResponse>,
    val themes: List<SearchThemeResponse>,
    val issues: List<IssueSummaryResponse>,
) {
    companion object {
        fun from(query: String, result: SearchResult) = SearchResponse(
            query = query,
            stocks = result.stocks.map(SearchStockResponse::from),
            themes = result.themes.map(SearchThemeResponse::from),
            issues = result.issues.map(IssueSummaryResponse::from),
        )
    }
}

data class SearchStockResponse(
    val ticker: String,
    val name: String,
    val market: String,
    @Schema(description = "이름·종목코드가 아니라 별칭으로 찾았을 때 걸린 별칭. 이름·종목코드로 찾았으면 null")
    val matchedAlias: String?,
) {
    companion object {
        fun from(hit: StockSearchHit) = SearchStockResponse(hit.ticker, hit.name, hit.market, hit.matchedAlias)
    }
}

data class SearchThemeResponse(
    val id: Long,
    val name: String,
) {
    companion object {
        fun from(theme: PrimaryTheme) = SearchThemeResponse(theme.id, theme.name)
    }
}
