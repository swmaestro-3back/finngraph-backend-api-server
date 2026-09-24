package com.finngraph.web.favorite

import com.finngraph.composition.FavoriteItem
import com.finngraph.composition.FavoriteList
import com.finngraph.favorite.model.FavoriteView
import com.finngraph.stock.model.StockPriceView
import com.finngraph.theme.model.ThemeSummary
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

data class FavoriteResponse(
    val type: String,
    val key: String,
    val createdAt: OffsetDateTime,
) {
    companion object {
        fun from(view: FavoriteView) = FavoriteResponse(view.target.type.name, view.target.key, view.createdAt)
    }
}

data class FavoriteListResponse(
    val count: Int,
    val limit: Int,
    val items: List<FavoriteItemResponse>,
) {
    companion object {
        fun from(list: FavoriteList) =
            FavoriteListResponse(list.count, list.limit, list.items.map(FavoriteItemResponse::from))
    }
}

data class FavoriteItemResponse(
    val type: String,
    val key: String,
    val createdAt: OffsetDateTime,
    val resolved: Boolean,
    val stock: FavoriteStockResponse?,
    val theme: FavoriteThemeResponse?,
) {
    companion object {
        fun from(item: FavoriteItem) = FavoriteItemResponse(
            type = item.view.target.type.name,
            key = item.view.target.key,
            createdAt = item.view.createdAt,
            resolved = item.resolved,
            stock = item.stock?.let(FavoriteStockResponse::from),
            theme = item.theme?.let(FavoriteThemeResponse::from),
        )
    }
}

data class FavoriteStockResponse(
    val ticker: String,
    val name: String,
    val market: String,
    val price: BigDecimal?,
    val change: BigDecimal?,
    val marketCap: Long?,
) {
    companion object {
        fun from(view: StockPriceView) =
            FavoriteStockResponse(view.ticker, view.name, view.market, view.price, view.change, view.marketCap)
    }
}

data class FavoriteThemeResponse(
    val id: Long,
    val name: String,
    val change: BigDecimal?,
    val baseDate: LocalDate?,
    val stockCount: Int,
) {
    companion object {
        fun from(summary: ThemeSummary) =
            FavoriteThemeResponse(summary.id, summary.name, summary.change, summary.baseDate, summary.stockCount)
    }
}
