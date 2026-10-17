package com.finngraph.composition

import com.finngraph.news.model.NewsView
import com.finngraph.news.model.PageResult
import com.finngraph.news.port.NewsQueryPort
import com.finngraph.theme.model.ThemeId
import com.finngraph.theme.port.ThemeStockPort
import org.springframework.stereotype.Component

@Component
class ThemeNewsComposer(
    private val themeStock: ThemeStockPort,
    private val newsQuery: NewsQueryPort,
) {

    fun newsPage(id: ThemeId, page: Int, size: Int): PageResult<NewsView> =
        newsQuery.findPageByCompanyTickers(themeStock.findTickers(id), page, size)
}
