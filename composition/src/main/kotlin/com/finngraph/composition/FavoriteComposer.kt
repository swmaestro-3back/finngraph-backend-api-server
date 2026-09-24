package com.finngraph.composition

import com.finngraph.favorite.model.AddFavoriteResult
import com.finngraph.favorite.model.FavoriteTarget
import com.finngraph.favorite.model.FavoriteType
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockQueryPort
import com.finngraph.theme.model.ThemeId
import com.finngraph.theme.port.ThemeQueryPort
import org.springframework.stereotype.Component

class FavoriteTargetNotFoundException(val target: FavoriteTarget) :
    RuntimeException("즐겨찾기 대상이 존재하지 않습니다: ${target.type} ${target.key}")

@Component
class FavoriteComposer(
    private val writer: FavoriteWriter,
    private val stockQuery: StockQueryPort,
    private val themeQuery: ThemeQueryPort,
) {

    fun add(userId: Long, target: FavoriteTarget): AddFavoriteResult? {
        if (!exists(target)) throw FavoriteTargetNotFoundException(target)
        return writer.add(userId, target)
    }

    fun remove(userId: Long, target: FavoriteTarget) {
        writer.remove(userId, target)
    }

    private fun exists(target: FavoriteTarget): Boolean = when (target.type) {
        FavoriteType.STOCK -> stockQuery.exists(Ticker(target.key))
        FavoriteType.THEME -> themeQuery.exists(ThemeId(target.key.toLong()))
    }
}
