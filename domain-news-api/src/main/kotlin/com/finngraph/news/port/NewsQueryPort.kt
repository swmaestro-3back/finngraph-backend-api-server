package com.finngraph.news.port

import com.finngraph.news.model.NewsDetail
import com.finngraph.news.model.NewsId
import com.finngraph.news.model.NewsView
import com.finngraph.news.model.PageResult

interface NewsQueryPort {

    fun findPage(page: Int, size: Int): PageResult<NewsView>
    fun findByIds(ids: List<NewsId>): Map<NewsId, NewsDetail>
    fun findPageByTicker(ticker: String, page: Int, size: Int): PageResult<NewsView>
    fun findPageByCompanyTickers(tickers: List<String>, page: Int, size: Int): PageResult<NewsView>
}
