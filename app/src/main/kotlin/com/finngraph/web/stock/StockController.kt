package com.finngraph.web.stock

import com.finngraph.stock.model.CandlePeriod
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockCandlePort
import com.finngraph.stock.port.StockFinancialsPort
import com.finngraph.stock.port.StockFlowPort
import com.finngraph.stock.port.StockQueryPort
import com.finngraph.news.model.NewsView
import com.finngraph.news.model.PageResult
import com.finngraph.news.port.NewsQueryPort
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorCode
import com.finngraph.web.common.InvalidParameterException
import com.finngraph.web.common.PageResponse
import com.finngraph.web.common.Pagination
import com.finngraph.web.common.ResourceNotFoundException
import com.finngraph.composition.StockThemeComposer
import com.finngraph.web.news.NewsResponse
import org.springframework.web.bind.annotation.RestController

@RestController
class StockController(
    private val stockQuery: StockQueryPort,
    private val stockCandle: StockCandlePort,
    private val stockFlow: StockFlowPort,
    private val stockFinancials: StockFinancialsPort,
    private val stockThemeComposer: StockThemeComposer,
    private val newsQuery: NewsQueryPort,
) : StockApi {

    override fun list(): DataResponse<List<StockSummaryResponse>> =
        DataResponse(
            stockThemeComposer.listWithThemes().map { StockSummaryResponse.from(it.stock, it.primaryTheme) },
        )

    override fun detail(ticker: String): DataResponse<StockDetailResponse> {
        val target = toTicker(ticker)
        val found = stockThemeComposer.detailWithTheme(target) ?: throw notFound(ticker)
        return DataResponse(StockDetailResponse.from(found.stock, found.primaryTheme))
    }

    override fun candles(ticker: String, period: String, limit: Int?): DataResponse<List<CandleResponse>> {
        val target = toTicker(ticker)
        val resolved = validateCandleParams(period, limit)
        requireStock(target, ticker)
        return DataResponse(
            stockCandle.findCandles(target, resolved.period, resolved.limit).map(CandleResponse::from),
        )
    }

    override fun investorFlows(ticker: String, limit: Int): DataResponse<List<InvestorFlowResponse>> {
        val target = toTicker(ticker)
        validateFlowLimit(limit)
        requireStock(target, ticker)
        return DataResponse(stockFlow.findFlows(target, limit).map(InvestorFlowResponse::from))
    }

    override fun financials(ticker: String): DataResponse<List<AnnualFinancialsResponse>> {
        val target = toTicker(ticker)
        requireStock(target, ticker)
        return DataResponse(stockFinancials.findAnnual(target).map(AnnualFinancialsResponse::from))
    }

    override fun news(ticker: String, page: Int, size: Int): PageResponse<NewsResponse> {
        val target = toTicker(ticker)
        validatePaging(page, size)
        requireStock(target, ticker)
        return newsQuery.findPageByTicker(target.value, page, size).toResponse()
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

    private fun requireStock(target: Ticker, raw: String) {
        if (!stockQuery.exists(target)) throw notFound(raw)
    }

    private fun notFound(raw: String) =
        ResourceNotFoundException(ErrorCode.STOCK_NOT_FOUND, "종목을 찾을 수 없습니다: $raw")

    private fun validateCandleParams(period: String, limit: Int?): CandleParams {
        val parsed = CandlePeriod.entries.firstOrNull { it.name == period }
        val errors = buildMap {
            if (parsed == null) put("period", "must be one of D, W, M")
            if (limit != null && (limit < 1 || limit > MAX_CANDLE_LIMIT)) {
                put("limit", "must be between 1 and $MAX_CANDLE_LIMIT")
            }
        }
        if (errors.isNotEmpty()) {
            throw InvalidParameterException("캔들 파라미터가 올바르지 않습니다", errors)
        }
        val resolvedPeriod = checkNotNull(parsed)
        return CandleParams(resolvedPeriod, limit ?: defaultCandleLimit(resolvedPeriod))
    }

    private fun defaultCandleLimit(period: CandlePeriod) = when (period) {
        CandlePeriod.D -> DEFAULT_DAILY_LIMIT
        CandlePeriod.W -> DEFAULT_WEEKLY_LIMIT
        CandlePeriod.M -> DEFAULT_MONTHLY_LIMIT
    }

    private fun validateFlowLimit(limit: Int) {
        if (limit in 1..MAX_FLOW_LIMIT) return
        throw InvalidParameterException(
            "limit은 1 이상 $MAX_FLOW_LIMIT 이하여야 합니다",
            mapOf("limit" to "must be between 1 and $MAX_FLOW_LIMIT"),
        )
    }

    private fun validatePaging(page: Int, size: Int) {
        val errors = buildMap {
            if (page < 0) put("page", "must be >= 0")
            if (size < 1) put("size", "must be >= 1")
            if (size > PageResult.MAX_SIZE) put("size", "must be <= ${PageResult.MAX_SIZE}")
        }
        if (errors.isEmpty()) return

        val message =
            if (size > PageResult.MAX_SIZE) "size는 ${PageResult.MAX_SIZE} 이하여야 합니다"
            else "페이징 파라미터가 올바르지 않습니다"
        throw InvalidParameterException(message, errors)
    }

    private fun PageResult<NewsView>.toResponse() = PageResponse(
        data = content.map(NewsResponse::from),
        pagination = Pagination(page, size, totalElements, totalPages),
    )

    private data class CandleParams(val period: CandlePeriod, val limit: Int)

    private companion object {
        const val MAX_TICKER_LENGTH = 20
        const val MAX_CANDLE_LIMIT = 500
        const val MAX_FLOW_LIMIT = 200
        const val DEFAULT_DAILY_LIMIT = 65
        const val DEFAULT_WEEKLY_LIMIT = 52
        const val DEFAULT_MONTHLY_LIMIT = 36
    }
}
