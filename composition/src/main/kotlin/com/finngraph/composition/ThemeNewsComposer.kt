package com.finngraph.composition

import com.finngraph.news.model.NewsView
import com.finngraph.news.model.PageResult
import com.finngraph.news.port.NewsQueryPort
import com.finngraph.theme.model.ThemeName
import com.finngraph.theme.port.ThemeQueryPort
import com.finngraph.theme.port.ThemeStockPort
import org.springframework.stereotype.Component

@Component
class ThemeNewsComposer(
    private val themeQuery: ThemeQueryPort,
    private val themeStock: ThemeStockPort,
    private val newsQuery: NewsQueryPort,
) {

    fun newsPage(name: ThemeName, page: Int, size: Int): PageResult<NewsView>? {
        themeQuery.findByName(name) ?: return null
        return newsQuery.findPageByCompanyTickers(themeStock.findTickers(name), page, size)
    }
}
