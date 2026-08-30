package com.finngraph.web.composition

import com.finngraph.news.model.NewsView
import com.finngraph.news.model.PageResult
import com.finngraph.news.port.NewsQueryPort
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockQueryPort
import org.springframework.stereotype.Component

@Component
class StockNewsComposer(
    private val stockQuery: StockQueryPort,
    private val newsQuery: NewsQueryPort,
) {

    fun newsPage(ticker: Ticker, page: Int, size: Int): PageResult<NewsView>? {
        stockQuery.findByTicker(ticker) ?: return null
        return newsQuery.findPageByTicker(ticker.value, page, size)
    }
}
