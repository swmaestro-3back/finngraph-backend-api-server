package com.finngraph.composition.favorite

import com.finngraph.favorite.model.FavoriteTarget
import com.finngraph.favorite.model.FavoriteType
import com.finngraph.favorite.model.FolderView
import com.finngraph.favorite.port.FavoriteFolderPort
import com.finngraph.favorite.port.FavoritePort
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockCandlePort
import com.finngraph.stock.port.StockQueryPort
import com.finngraph.theme.model.PrimaryTheme
import com.finngraph.theme.port.ThemeQueryPort
import org.springframework.stereotype.Component

class FavoriteFolderNotFoundException(val folderId: Long) :
    RuntimeException("관심 폴더가 존재하지 않습니다: $folderId")

data class FolderSuggestions(
    val theme: PrimaryTheme?,
    val folders: List<FolderSuggestion>,
    val newName: String?,
)

data class FolderReturnsView(val folder: FolderView, val series: ReturnSeries)

@Component
class FavoriteFolderComposer(
    private val writer: FavoriteFolderWriter,
    private val folders: FavoriteFolderPort,
    private val favorites: FavoritePort,
    private val stockQuery: StockQueryPort,
    private val themeQuery: ThemeQueryPort,
    private val candles: StockCandlePort,
) {

    fun addItem(userId: Long, folderId: Long, target: FavoriteTarget): FolderWrite? {
        if (!exists(target)) throw FavoriteTargetNotFoundException(target)
        return writer.addItem(userId, folderId, target)
    }

    fun suggest(userId: Long, target: FavoriteTarget): FolderSuggestions {
        val owned = folders.findByUser(userId)
        val members = owned.flatMap { it.targets }.distinct()
        val folderTickers = members.filter { it.type == FavoriteType.STOCK }.map { it.key }
        val favoriteTickers = favorites.findTickersByUser(userId).filter { it != target.key }
        val memberships = themeQuery.findMemberships((folderTickers + favoriteTickers + target.key).distinct())
        val targetThemes = FolderMatcher.specific(memberships[target.key].orEmpty())
        val names = memberNames(members)
        val ranked = FolderMatcher.rank(target, targetThemes, owned, memberships) { names[it] ?: it.key }
        val nameTheme = FolderMatcher.nameTheme(targetThemes, favoriteTickers.map { memberships[it].orEmpty() })
        val newName = nameTheme
            ?.let { FolderMatcher.folderName(it.name) }
            ?.takeIf { candidate -> owned.none { it.name == candidate } }
        return FolderSuggestions(nameTheme?.let { PrimaryTheme(it.id, it.name) }, ranked, newName)
    }

    fun returns(userId: Long, folderId: Long, period: ReturnPeriod): FolderReturnsView {
        val folder = folders.find(userId, folderId) ?: throw FavoriteFolderNotFoundException(folderId)
        val tickers = folder.targets.filter { it.type == FavoriteType.STOCK }.map { Ticker(it.key) }
        return FolderReturnsView(folder, returnSeries(tickers, period))
    }

    private fun returnSeries(tickers: List<Ticker>, period: ReturnPeriod): ReturnSeries {
        val names = stockQuery.findByTickers(tickers).mapValues { (_, stock) -> stock.name }
        return FolderReturns.of(period, candles.findRecentDailyCloses(tickers, period.days), names)
    }

    private fun memberNames(members: List<FavoriteTarget>): Map<FavoriteTarget, String> {
        val stocks = stockQuery.findByTickers(
            members.filter { it.type == FavoriteType.STOCK }.map { Ticker(it.key) },
        )
        return members.associateWith { member -> stocks[Ticker(member.key)]?.name ?: member.key }
    }

    private fun exists(target: FavoriteTarget): Boolean =
        target.type == FavoriteType.STOCK && stockQuery.exists(Ticker(target.key))
}
