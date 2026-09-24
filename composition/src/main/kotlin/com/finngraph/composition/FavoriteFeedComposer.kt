package com.finngraph.composition

import com.finngraph.favorite.model.FavoriteType
import com.finngraph.favorite.model.FavoriteView
import com.finngraph.favorite.port.FavoritePort
import com.finngraph.news.model.NewsView
import com.finngraph.news.model.PageResult
import com.finngraph.news.port.NewsQueryPort
import com.finngraph.stock.model.StockPriceView
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockQueryPort
import com.finngraph.theme.model.ThemeId
import com.finngraph.theme.model.ThemeSummary
import com.finngraph.theme.port.ThemeQueryPort
import org.springframework.stereotype.Component

data class FavoriteItem(
    val view: FavoriteView,
    val stock: StockPriceView?,
    val theme: ThemeSummary?,
) {
    val resolved: Boolean get() = stock != null || theme != null
}

data class FavoriteList(
    val count: Int,
    val limit: Int,
    val items: List<FavoriteItem>,
)

@Component
class FavoriteFeedComposer(
    private val favorites: FavoritePort,
    private val stockQuery: StockQueryPort,
    private val themeQuery: ThemeQueryPort,
    private val newsQuery: NewsQueryPort,
) {

    fun list(userId: Long): FavoriteList {
        val views = favorites.findByUser(userId)
        val stocks = stockQuery.findByTickers(
            views.filter { it.target.type == FavoriteType.STOCK }.map { Ticker(it.target.key) },
        )
        val themes = themeQuery.findByIds(
            views.filter { it.target.type == FavoriteType.THEME }.map { ThemeId(it.target.key.toLong()) },
        )
        val items = views.map { view ->
            when (view.target.type) {
                FavoriteType.STOCK -> FavoriteItem(view, stocks[Ticker(view.target.key)], null)
                FavoriteType.THEME -> FavoriteItem(view, null, themes[ThemeId(view.target.key.toLong())])
            }
        }
        return FavoriteList(items.size, FavoritePort.LIMIT, items)
    }

    fun newsPage(userId: Long, page: Int, size: Int): PageResult<NewsView> =
        newsQuery.findPageByCompanyTickers(favorites.findTickersByUser(userId), page, size)
}
