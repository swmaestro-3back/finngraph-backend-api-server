package com.finngraph.web.stock

import com.finngraph.stock.model.CandlePeriod
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockCandlePort
import com.finngraph.stock.port.StockFinancialsPort
import com.finngraph.stock.port.StockFlowPort
import com.finngraph.stock.port.StockQueryPort
import com.finngraph.web.common.DataResponse
import com.finngraph.web.common.ErrorCode
import com.finngraph.web.common.InvalidParameterException
import com.finngraph.web.common.ResourceNotFoundException
import org.springframework.web.bind.annotation.RestController

@RestController
class StockController(
    private val stockQuery: StockQueryPort,
    private val stockCandle: StockCandlePort,
    private val stockFlow: StockFlowPort,
    private val stockFinancials: StockFinancialsPort,
    private val stockThemeComposer: StockThemeComposer,
) : StockApi {

    override fun list(): DataResponse<List<StockSummaryResponse>> =
        DataResponse(stockThemeComposer.listWithThemes())

    override fun detail(ticker: String): DataResponse<StockDetailResponse> {
        val target = toTicker(ticker)
        val found = stockThemeComposer.detailWithTheme(target) ?: throw notFound(ticker)
        return DataResponse(found)
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
        stockQuery.findByTicker(target) ?: throw notFound(raw)
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
