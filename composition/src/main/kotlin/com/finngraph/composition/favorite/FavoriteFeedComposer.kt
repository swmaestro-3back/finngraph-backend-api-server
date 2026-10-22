package com.finngraph.composition.favorite

import com.finngraph.favorite.model.FavoriteTarget
import com.finngraph.favorite.model.FavoriteType
import com.finngraph.favorite.model.FavoriteView
import com.finngraph.favorite.model.FolderView
import com.finngraph.favorite.port.FavoriteFolderPort
import com.finngraph.favorite.port.FavoritePort
import com.finngraph.news.model.NewsView
import com.finngraph.news.model.PageResult
import com.finngraph.news.port.NewsQueryPort
import com.finngraph.stock.model.StockPriceView
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockCandlePort
import com.finngraph.stock.port.StockQueryPort
import com.finngraph.theme.model.ThemeId
import com.finngraph.theme.model.ThemeSummary
import com.finngraph.theme.port.ThemeQueryPort
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

data class FavoriteItem(
    val view: FavoriteView,
    val stock: StockPriceView?,
    val theme: ThemeSummary?,
    val folderIds: List<Long> = emptyList(),
) {
    val resolved: Boolean get() = stock != null || theme != null
}

data class FolderSummary(
    val folder: FolderView,
    val change: BigDecimal?,
    val return1m: BigDecimal?,
    val spark: List<BigDecimal>,
)

data class FavoriteList(
    val count: Int,
    val limit: Int,
    val items: List<FavoriteItem>,
    val folders: List<FolderSummary> = emptyList(),
    val folderLimit: Int = FavoriteFolderPort.LIMIT,
)

@Component
class FavoriteFeedComposer(
    private val favorites: FavoritePort,
    private val folders: FavoriteFolderPort,
    private val stockQuery: StockQueryPort,
    private val themeQuery: ThemeQueryPort,
    private val newsQuery: NewsQueryPort,
    private val candles: StockCandlePort,
) {

    fun list(userId: Long): FavoriteList {
        val views = favorites.findByUser(userId)
        val owned = folders.findByUser(userId)
        val folderIds = owned
            .flatMap { folder -> folder.targets.map { it to folder.id } }
            .groupBy({ it.first }, { it.second })
        val stocks = stockQuery.findByTickers(
            views.filter { it.target.type == FavoriteType.STOCK }.map { Ticker(it.target.key) },
        )
        val themes = themeQuery.findByIds(
            views.filter { it.target.type == FavoriteType.THEME }.map { ThemeId(it.target.key.toLong()) },
        )
        val items = views.map { view ->
            when (view.target.type) {
                FavoriteType.STOCK -> FavoriteItem(view, stocks[Ticker(view.target.key)], null, folderIds.of(view.target))
                FavoriteType.THEME ->
                    FavoriteItem(view, null, themes[ThemeId(view.target.key.toLong())], folderIds.of(view.target))
            }
        }
        return FavoriteList(items.size, FavoritePort.LIMIT, items, summarize(owned, stocks))
    }

    private fun summarize(owned: List<FolderView>, stocks: Map<Ticker, StockPriceView>): List<FolderSummary> {
        if (owned.isEmpty()) return emptyList()
        val tickers = owned.flatMap { it.stockTickers() }.distinct()
        val closes = candles.findRecentDailyCloses(tickers, ReturnPeriod.M1.days)
        return owned.map { folder ->
            val members = folder.stockTickers().toSet()
            val series = FolderReturns.series(closes.filterKeys { it in members })
            FolderSummary(
                folder = folder,
                change = average(members.mapNotNull { stocks[it]?.change }),
                return1m = series.lastOrNull()?.value,
                spark = series.map { it.value },
            )
        }
    }

    private fun FolderView.stockTickers(): List<Ticker> =
        targets.filter { it.type == FavoriteType.STOCK }.map { Ticker(it.key) }

    private fun Map<FavoriteTarget, List<Long>>.of(target: FavoriteTarget): List<Long> = this[target].orEmpty()

    private fun average(values: List<BigDecimal>): BigDecimal? {
        if (values.isEmpty()) return null
        return values.fold(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal(values.size), 2, RoundingMode.HALF_UP)
    }

    fun returns(userId: Long, period: ReturnPeriod): ReturnSeries {
        val tickers = favorites.findTickersByUser(userId).map { Ticker(it) }
        val names = stockQuery.findByTickers(tickers).mapValues { (_, stock) -> stock.name }
        return FolderReturns.of(period, candles.findRecentDailyCloses(tickers, period.days), names)
    }

    fun newsPage(userId: Long, page: Int, size: Int): PageResult<NewsView> =
        newsQuery.findPageByCompanyTickers(favorites.findTickersByUser(userId), page, size)
}
