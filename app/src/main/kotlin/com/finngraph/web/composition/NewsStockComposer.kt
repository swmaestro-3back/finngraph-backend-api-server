package com.finngraph.web.composition

import com.finngraph.news.model.NewsId
import com.finngraph.news.port.NewsCompanyPort
import com.finngraph.news.port.NewsQueryPort
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockQueryPort
import com.finngraph.web.news.NewsRelatedStockResponse
import org.springframework.stereotype.Component

@Component
class NewsStockComposer(
    private val newsQuery: NewsQueryPort,
    private val newsCompany: NewsCompanyPort,
    private val stockQuery: StockQueryPort,
) {

    fun relatedStocks(id: NewsId): List<NewsRelatedStockResponse>? {
        newsQuery.findByIds(listOf(id))[id] ?: return null

        val refs = newsCompany.findByNewsIds(listOf(id))[id] ?: emptyList()
        if (refs.isEmpty()) return emptyList()

        val tickers = refs.mapNotNull { it.ticker }.map(::Ticker)
        val prices = stockQuery.findByTickers(tickers)

        return refs.map { ref ->
            val priced = ref.ticker?.let { prices[Ticker(it)] }
            NewsRelatedStockResponse(
                companyName = ref.companyName,
                ticker = ref.ticker,
                market = priced?.market,
                price = priced?.price,
                change = priced?.change,
            )
        }
    }
}
