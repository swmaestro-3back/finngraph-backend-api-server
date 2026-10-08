package com.finngraph.composition.hottheme

import com.finngraph.theme.HotThemeSelector
import com.finngraph.theme.model.ChangeStatus
import com.finngraph.theme.model.ThemeBoard
import com.finngraph.theme.model.ThemeId
import com.finngraph.theme.model.ThemeSummary
import com.finngraph.theme.port.ThemeQueryPort
import com.finngraph.theme.port.ThemeStockPort
import org.springframework.stereotype.Component

@Component
class HotThemeComposer(
    private val themeQuery: ThemeQueryPort,
    private val themeStock: ThemeStockPort,
) {

    fun all(): List<ThemeSummary> =
        themeQuery.board().let { HotThemeSelector.annotate(it.themes, it.market) }

    fun detail(id: ThemeId): ThemeSummary? =
        themeQuery.board(listOf(id)).let { board ->
            board.themes.singleOrNull { it.id == id.value }
                ?.let { HotThemeSelector.annotate(listOf(it), board.market).single() }
        }

    fun hot(count: Int): List<ThemeSummary> = select(themeQuery.board(), count)

    fun settledBoard(): ThemeBoard = themeQuery.board(themeQuery.pricingBasis().settled())

    fun hotForEtl(count: Int): HotThemeSnapshot {
        val board = themeQuery.board()
        val selected = select(board, count)
        val stocks = themeStock.findStocks(selected.map { ThemeId(it.id) }, board.basis)
        return HotThemeSnapshot(
            tradeDate = selected.firstNotNullOfOrNull { it.baseDate },
            themes = selected.map { summary ->
                HotTheme(
                    id = summary.id,
                    name = summary.name,
                    change = requireNotNull(summary.change),
                    stocks = stocks[ThemeId(summary.id)].orEmpty()
                        .filter { it.changeStatus != ChangeStatus.DELISTING }
                        .map { HotThemeStock(it.ticker, it.name) },
                )
            },
        )
    }

    fun select(board: ThemeBoard, count: Int): List<ThemeSummary> {
        val candidates = HotThemeSelector.candidates(board.themes, board.market)
        if (candidates.isEmpty()) return emptyList()
        val members = themeStock.findTickers(candidates.map { ThemeId(it.id) })
            .mapKeys { (id, _) -> id.value }
        return HotThemeSelector.select(board.themes, board.market, count, members)
    }
}
