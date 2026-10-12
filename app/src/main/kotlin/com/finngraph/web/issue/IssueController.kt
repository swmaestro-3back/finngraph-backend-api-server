package com.finngraph.web.issue

import com.finngraph.composition.issue.IssueComposer
import com.finngraph.composition.issue.IssueSort
import com.finngraph.stock.model.Ticker
import com.finngraph.theme.model.ThemeId
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorCode
import com.finngraph.web.common.InvalidParameterException
import com.finngraph.web.common.PageResponse
import com.finngraph.web.common.Pagination
import com.finngraph.web.common.ResourceNotFoundException
import com.finngraph.web.common.validatePaging
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeParseException

@RestController
class IssueController(
    private val composer: IssueComposer,
) : IssueApi {

    override fun list(date: String?, sort: String, page: Int, size: Int): IssuePageResponse {
        val day = parseDate(date)
        val order = SORT_KEYS[sort]
        val errors = buildMap {
            if (!date.isNullOrBlank() && day == null) put("date", DATE_FORMAT)
            if (order == null) put("sort", "must be one of ${SORT_KEYS.keys.joinToString(", ")}")
        }
        if (errors.isNotEmpty()) {
            throw InvalidParameterException("이슈 목록 파라미터가 올바르지 않습니다", errors)
        }
        validatePaging(page, size)
        return IssuePageResponse.from(composer.page(day, checkNotNull(order), page, size))
    }

    override fun detail(id: Long): DataResponse<IssueDetailResponse> {
        if (id <= 0) {
            throw InvalidParameterException("id는 양수여야 합니다: $id", mapOf("id" to "must be positive"))
        }
        val found = composer.detail(id)
            ?: throw ResourceNotFoundException(ErrorCode.ISSUE_NOT_FOUND, "이슈를 찾을 수 없습니다: $id")
        return DataResponse(IssueDetailResponse.from(found))
    }

    override fun timeline(id: Long, limit: Int): DataResponse<IssueTimelineResponse> {
        val errors = buildMap {
            if (id <= 0) put("id", "must be positive")
            if (limit !in 1..IssueComposer.TIMELINE_MAX_LIMIT) put("limit", "must be between 1 and ${IssueComposer.TIMELINE_MAX_LIMIT}")
        }
        if (errors.isNotEmpty()) {
            throw InvalidParameterException("이슈 타임라인 파라미터가 올바르지 않습니다", errors)
        }
        val found = composer.timeline(id, limit)
            ?: throw ResourceNotFoundException(ErrorCode.ISSUE_NOT_FOUND, "이슈를 찾을 수 없습니다: $id")
        return DataResponse(IssueTimelineResponse.from(found))
    }

    override fun stockIssues(ticker: String, from: String?, to: String?, page: Int, size: Int): PageResponse<StockIssueResponse> {
        val target = toTicker(ticker)
        val start = parseDate(from)
        val end = parseDate(to)
        val errors = buildMap {
            if (!from.isNullOrBlank() && start == null) put("from", DATE_FORMAT)
            if (!to.isNullOrBlank() && end == null) put("to", DATE_FORMAT)
            if (start != null && end != null && end < start) put("to", "must not be before from")
        }
        if (errors.isNotEmpty()) {
            throw InvalidParameterException("종목 이슈 파라미터가 올바르지 않습니다", errors)
        }
        validatePaging(page, size)
        val found = composer.stockPage(target, start, end, page, size)
            ?: throw ResourceNotFoundException(ErrorCode.STOCK_NOT_FOUND, "종목을 찾을 수 없습니다: $ticker")
        return PageResponse(
            data = found.content.map(StockIssueResponse::from),
            pagination = Pagination(found.page, found.size, found.totalElements, found.totalPages),
        )
    }

    override fun latestStockIssues(tickers: String?): DataResponse<List<LatestStockIssueResponse>> {
        val requested = splitList(tickers)
        val error = when {
            requested.isEmpty() -> "required"
            requested.size > MAX_BATCH -> "must have at most $MAX_BATCH items"
            requested.any { it.length > MAX_TICKER_LENGTH } -> "each must be <= $MAX_TICKER_LENGTH characters"
            else -> null
        }
        if (error != null) {
            throw InvalidParameterException("tickers가 올바르지 않습니다", mapOf("tickers" to error))
        }
        return DataResponse(composer.latestByStocks(requested).map(LatestStockIssueResponse::from))
    }

    override fun themeIssues(ids: String?, date: String?): ThemeIssueBoardResponse {
        val parsed = splitList(ids).map { it.toLongOrNull() }
        val themeIds = parsed.filterNotNull().filter { it > 0 }.distinct()
        val day = parseDate(date)
        val errors = buildMap {
            when {
                parsed.isEmpty() -> put("ids", "required")
                parsed.any { it == null || it <= 0 } -> put("ids", "each must be a positive integer")
                themeIds.size > MAX_BATCH -> put("ids", "must have at most $MAX_BATCH items")
            }
            if (!date.isNullOrBlank() && day == null) put("date", DATE_FORMAT)
        }
        if (errors.isNotEmpty()) {
            throw InvalidParameterException("테마 이슈 파라미터가 올바르지 않습니다", errors)
        }
        return ThemeIssueBoardResponse.from(composer.themeBoard(themeIds.map(::ThemeId), day))
    }

    private fun toTicker(raw: String): Ticker {
        val errors = buildMap {
            if (raw.isBlank()) put("ticker", "must not be blank")
            else if (raw.length > MAX_TICKER_LENGTH) put("ticker", "must be <= $MAX_TICKER_LENGTH characters")
        }
        if (errors.isNotEmpty()) {
            throw InvalidParameterException("ticker가 올바르지 않습니다", errors)
        }
        return Ticker(raw)
    }

    private fun splitList(raw: String?): List<String> =
        raw.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    private fun parseDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank() || !ISO_DATE.matches(raw)) return null
        return try {
            LocalDate.parse(raw)
        } catch (e: DateTimeParseException) {
            null
        }
    }

    private companion object {
        const val MAX_BATCH = 50
        const val MAX_TICKER_LENGTH = 20
        const val DATE_FORMAT = "must be YYYY-MM-DD"
        val ISO_DATE = Regex("\\d{4}-\\d{2}-\\d{2}")
        val SORT_KEYS = mapOf(
            "media" to IssueSort.MEDIA,
            "recent" to IssueSort.RECENT,
        )
    }
}
