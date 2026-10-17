package com.finngraph.composition

import com.finngraph.news.model.NewsId
import com.finngraph.news.port.NewsCompanyPort
import com.finngraph.news.port.NewsQueryPort
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockQueryPort
import org.springframework.stereotype.Component
import java.math.BigDecimal

data class RelatedStock(
    val companyName: String,
    val ticker: String?,
    val market: String?,
    val price: BigDecimal?,
    val change: BigDecimal?,
)

@Component
class NewsStockComposer(
    private val newsQuery: NewsQueryPort,
    private val newsCompany: NewsCompanyPort,
    private val stockQuery: StockQueryPort,
) {

    fun relatedStocks(id: NewsId): List<RelatedStock>? {
        newsQuery.findByIds(listOf(id))[id] ?: return null

        val refs = newsCompany.findByNewsIds(listOf(id))[id] ?: emptyList()
        if (refs.isEmpty()) return emptyList()

        val tickers = refs.mapNotNull { it.ticker }.map(::Ticker)
        val prices = stockQuery.findByTickers(tickers)

        return refs.map { ref ->
            val priced = ref.ticker?.let { prices[Ticker(it)] }
            RelatedStock(
                companyName = ref.companyName,
                ticker = ref.ticker,
                market = priced?.market,
                price = priced?.price,
                change = priced?.change,
            )
        }
    }
}
