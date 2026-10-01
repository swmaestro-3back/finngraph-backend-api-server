package com.finngraph.web.stock

import com.finngraph.composition.ContractComposer
import com.finngraph.composition.StockThemeComposer
import com.finngraph.news.model.NewsView
import com.finngraph.news.model.PageResult
import com.finngraph.news.port.NewsQueryPort
import com.finngraph.stock.model.CandlePeriod
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockCandlePort
import com.finngraph.stock.port.StockFinancialsPort
import com.finngraph.stock.port.StockFlowPort
import com.finngraph.stock.port.StockQueryPort
import com.finngraph.web.common.CandleInterval
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorCode
import com.finngraph.web.common.InvalidParameterException
import com.finngraph.web.common.PageResponse
import com.finngraph.web.common.Pagination
import com.finngraph.web.common.ResourceNotFoundException
import com.finngraph.web.common.validateCandleQuery
import com.finngraph.web.common.validatePaging
import com.finngraph.web.news.NewsResponse
import org.springframework.web.bind.annotation.RestController

@RestController
class StockController(
    private val stockQuery: StockQueryPort,
    private val stockCandle: StockCandlePort,
    private val stockFlow: StockFlowPort,
    private val stockFinancials: StockFinancialsPort,
    private val stockThemeComposer: StockThemeComposer,
    private val contractComposer: ContractComposer,
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
        val query = validateCandleQuery(period, limit)
        requireStock(target, ticker)
        return DataResponse(
            stockCandle.findCandles(target, query.interval.toCandlePeriod(), query.limit).map(CandleResponse::from),
        )
    }

    override fun investorFlows(ticker: String, limit: Int): DataResponse<List<InvestorFlowResponse>> {
        val target = toTicker(ticker)
        validateLimit(limit, MAX_FLOW_LIMIT)
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

    override fun contracts(ticker: String, limit: Int): DataResponse<List<StockContractResponse>> {
        val target = toTicker(ticker)
        validateLimit(limit, MAX_CONTRACT_LIMIT)
        return DataResponse(contractComposer.forStock(target, limit).map(StockContractResponse::from))
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

    private fun CandleInterval.toCandlePeriod(): CandlePeriod = when (this) {
        CandleInterval.D -> CandlePeriod.D
        CandleInterval.W -> CandlePeriod.W
        CandleInterval.M -> CandlePeriod.M
    }

    private fun validateLimit(limit: Int, max: Int) {
        if (limit in 1..max) return
        throw InvalidParameterException(
            "limit은 1 이상 $max 이하여야 합니다",
            mapOf("limit" to "must be between 1 and $max"),
        )
    }

    private fun PageResult<NewsView>.toResponse() = PageResponse(
        data = content.map(NewsResponse::from),
        pagination = Pagination(page, size, totalElements, totalPages),
    )

    private companion object {
        const val MAX_TICKER_LENGTH = 20
        const val MAX_FLOW_LIMIT = 250
        const val MAX_CONTRACT_LIMIT = 200
    }
}
