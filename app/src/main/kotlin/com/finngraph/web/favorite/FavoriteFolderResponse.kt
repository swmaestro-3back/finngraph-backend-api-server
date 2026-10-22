package com.finngraph.web.favorite

import com.finngraph.composition.favorite.FolderReturnsView
import com.finngraph.composition.favorite.FolderSuggestions
import com.finngraph.composition.favorite.FolderSummary
import com.finngraph.composition.favorite.MemberReturn
import com.finngraph.composition.favorite.ReturnPeriod
import com.finngraph.composition.favorite.ReturnSeries
import com.finngraph.favorite.model.FolderView
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate

data class FolderNameRequest(val name: String?)

data class FolderOrderRequest(val ids: List<Long>?)

data class FavoriteFolderResponse(
    val id: Long,
    val name: String,
    val position: Int,
    val count: Int,
) {
    companion object {
        fun from(view: FolderView) = FavoriteFolderResponse(view.id, view.name, view.position, view.targets.size)
    }
}

data class FolderSummaryResponse(
    val id: Long,
    val name: String,
    val position: Int,
    val count: Int,
    @Schema(description = "담긴 종목 오늘 등락률의 단순 평균(%). 종목이 없으면 null")
    val change: BigDecimal?,
    @Schema(description = "담긴 종목을 같은 비중으로 묶은 1개월 누적 수익률(%)")
    val return1m: BigDecimal?,
    @Schema(description = "1개월 누적 수익률 시계열(%), 오래된 날부터")
    val spark: List<BigDecimal>,
) {
    companion object {
        fun from(summary: FolderSummary) = FolderSummaryResponse(
            id = summary.folder.id,
            name = summary.folder.name,
            position = summary.folder.position,
            count = summary.folder.targets.size,
            change = summary.change,
            return1m = summary.return1m,
            spark = summary.spark,
        )
    }
}

data class FolderSuggestResponse(
    val theme: SuggestThemeResponse?,
    val folders: List<FolderSuggestionResponse>,
    @Schema(description = "새 폴더 이름 후보(대상이 속한 테마 가운데 관심과 가장 많이 겹치는 좁은 테마). 같은 이름 폴더가 있으면 null")
    val newName: String?,
) {
    companion object {
        fun from(result: FolderSuggestions) = FolderSuggestResponse(
            theme = result.theme?.let { SuggestThemeResponse(it.id, it.name) },
            folders = result.folders.map {
                FolderSuggestionResponse(
                    id = it.folder.id,
                    name = it.folder.name,
                    count = it.folder.targets.size,
                    included = it.included,
                    theme = it.theme?.let { theme -> SuggestThemeResponse(theme.id, theme.name) },
                    matched = it.matched,
                    recommended = it.recommended,
                )
            },
            newName = result.newName,
        )
    }
}

data class SuggestThemeResponse(val id: Long, val name: String)

data class FolderSuggestionResponse(
    val id: Long,
    val name: String,
    val count: Int,
    @Schema(description = "이미 이 폴더에 담겨 있는지")
    val included: Boolean,
    @Schema(description = "대상과 이 폴더를 가장 많이 잇는 테마(60종목 넘는 넓은 테마 제외). 겹치는 게 없으면 null")
    val theme: SuggestThemeResponse?,
    @Schema(description = "대상과 같은 테마로 묶이는 폴더 속 종목·테마 이름. 좁은 테마로 많이 겹치는 폴더부터 정렬")
    val matched: List<String>,
    @Schema(description = "폴더 속 다른 항목의 절반 넘게 대상과 테마가 겹치면 true")
    val recommended: Boolean,
)

data class FolderReturnsResponse(
    val folderId: Long,
    @Schema(allowableValues = ["1W", "1M", "3M"])
    val period: String,
    @Schema(description = "시계열 첫 거래일. 계산할 종목이 없으면 null")
    val from: LocalDate?,
    @Schema(description = "기간 누적 수익률(%)")
    val total: BigDecimal?,
    @Schema(description = "계산에 쓴 종목 수(테마·시세 없는 종목 제외)")
    val stockCount: Int,
    val points: List<ReturnPointResponse>,
    @Schema(description = "종목별 같은 기간 누적 수익률. 기간 수익률 높은 순")
    val stocks: List<MemberReturnResponse>,
) {
    companion object {
        fun from(view: FolderReturnsView) = FolderReturnsResponse(
            folderId = view.folder.id,
            period = periodName(view.series.period),
            from = view.series.from,
            total = view.series.total,
            stockCount = view.series.stockCount,
            points = view.series.points.map { ReturnPointResponse(it.date, it.value) },
            stocks = view.series.members.map(MemberReturnResponse::from),
        )
    }
}

data class FavoriteReturnsResponse(
    @Schema(allowableValues = ["1W", "1M", "3M"])
    val period: String,
    @Schema(description = "시계열 첫 거래일. 계산할 종목이 없으면 null")
    val from: LocalDate?,
    @Schema(description = "기간 누적 수익률(%)")
    val total: BigDecimal?,
    @Schema(description = "계산에 쓴 관심 종목 수(관심 테마·시세 없는 종목 제외)")
    val stockCount: Int,
    val points: List<ReturnPointResponse>,
    @Schema(description = "종목별 같은 기간 누적 수익률. 기간 수익률 높은 순")
    val stocks: List<MemberReturnResponse>,
) {
    companion object {
        fun from(series: ReturnSeries) = FavoriteReturnsResponse(
            period = periodName(series.period),
            from = series.from,
            total = series.total,
            stockCount = series.stockCount,
            points = series.points.map { ReturnPointResponse(it.date, it.value) },
            stocks = series.members.map(MemberReturnResponse::from),
        )
    }
}

data class ReturnPointResponse(val date: LocalDate, val value: BigDecimal)

data class MemberReturnResponse(
    val ticker: String,
    val name: String,
    @Schema(description = "기간 누적 수익률(%). 종목의 기간 첫 종가 기준")
    val total: BigDecimal?,
    @Schema(description = "묶음 시계열과 같은 날짜축의 누적 수익률(%). 시세가 시작되기 전 날짜는 빠진다")
    val points: List<ReturnPointResponse>,
) {
    companion object {
        fun from(member: MemberReturn) = MemberReturnResponse(
            ticker = member.ticker.value,
            name = member.name,
            total = member.total,
            points = member.points.map { ReturnPointResponse(it.date, it.value) },
        )
    }
}

internal fun periodName(period: ReturnPeriod): String = when (period) {
    ReturnPeriod.W1 -> "1W"
    ReturnPeriod.M1 -> "1M"
    ReturnPeriod.M3 -> "3M"
}

internal fun parsePeriod(raw: String): ReturnPeriod? = ReturnPeriod.entries.firstOrNull { periodName(it) == raw }
