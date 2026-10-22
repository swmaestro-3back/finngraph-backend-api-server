package com.finngraph.web.favorite

import com.finngraph.composition.favorite.FavoriteItem
import com.finngraph.composition.favorite.FavoriteList
import com.finngraph.favorite.model.FavoriteView
import com.finngraph.stock.model.StockPriceView
import com.finngraph.theme.model.ThemeSummary
import com.finngraph.web.theme.WEIGHTED_CHANGE_DESCRIPTION
import io.swagger.v3.oas.annotations.media.Schema
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
    @Schema(description = "나만의 테마(관심 폴더). position 순")
    val folders: List<FolderSummaryResponse>,
    val folderLimit: Int,
) {
    companion object {
        fun from(list: FavoriteList) = FavoriteListResponse(
            count = list.count,
            limit = list.limit,
            items = list.items.map(FavoriteItemResponse::from),
            folders = list.folders.map(FolderSummaryResponse::from),
            folderLimit = list.folderLimit,
        )
    }
}

data class FavoriteItemResponse(
    val type: String,
    val key: String,
    val createdAt: OffsetDateTime,
    val resolved: Boolean,
    val stock: FavoriteStockResponse?,
    val theme: FavoriteThemeResponse?,
    @Schema(description = "이 관심이 담긴 폴더 id")
    val folderIds: List<Long>,
) {
    companion object {
        fun from(item: FavoriteItem) = FavoriteItemResponse(
            type = item.view.target.type.name,
            key = item.view.target.key,
            createdAt = item.view.createdAt,
            resolved = item.resolved,
            stock = item.stock?.let(FavoriteStockResponse::from),
            theme = item.theme?.let(FavoriteThemeResponse::from),
            folderIds = item.folderIds,
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
    @Schema(description = WEIGHTED_CHANGE_DESCRIPTION)
    val weightedChange: BigDecimal?,
    val baseDate: LocalDate?,
    val stockCount: Int,
    val pricedCount: Int,
) {
    companion object {
        fun from(summary: ThemeSummary) = FavoriteThemeResponse(
            id = summary.id,
            name = summary.name,
            change = summary.change,
            weightedChange = summary.weightedChange,
            baseDate = summary.baseDate,
            stockCount = summary.stockCount,
            pricedCount = summary.pricedCount,
        )
    }
}
