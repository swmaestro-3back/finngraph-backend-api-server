package com.finngraph.web.stock

import com.finngraph.stock.AnnualFinancials
import com.finngraph.stock.Candle
import com.finngraph.stock.InvestorFlow
import com.finngraph.stock.StockDetailView
import com.finngraph.stock.StockListView
import java.math.BigDecimal
import java.time.LocalDate

data class StockSummaryResponse(
    val ticker: String,
    val name: String,
    val market: String,
    val price: BigDecimal?,
    val change: BigDecimal?,
    val w1: BigDecimal?,
    val m1: BigDecimal?,
    val m3: BigDecimal?,
    val marketCap: Long?,
    val per: BigDecimal?,
    val pbr: BigDecimal?,
    val roe: BigDecimal?,
    val dividendYield: BigDecimal?,
    val themeName: String?,
) {
    companion object {
        fun from(view: StockListView, themeName: String? = null) = StockSummaryResponse(
            ticker = view.ticker,
            name = view.name,
            market = view.market,
            price = view.price,
            change = view.change,
            w1 = view.w1,
            m1 = view.m1,
            m3 = view.m3,
            marketCap = view.marketCap,
            per = view.per,
            pbr = view.pbr,
            roe = view.roe,
            dividendYield = view.dividendYield,
            themeName = themeName,
        )
    }
}

data class StockDetailResponse(
    val ticker: String,
    val name: String,
    val market: String,
    val price: BigDecimal?,
    val change: BigDecimal?,
    val themeName: String?,
    val marketCap: Long?,
    val per: BigDecimal?,
    val pbr: BigDecimal?,
    val roe: BigDecimal?,
    val eps: BigDecimal?,
    val dividendYield: BigDecimal?,
    val foreignRatio: BigDecimal?,
    val revenueGrowth: BigDecimal?,
) {
    companion object {
        fun from(view: StockDetailView, themeName: String? = null) = StockDetailResponse(
            ticker = view.ticker,
            name = view.name,
            market = view.market,
            price = view.price,
            change = view.change,
            themeName = themeName,
            marketCap = view.marketCap,
            per = view.per,
            pbr = view.pbr,
            roe = view.roe,
            eps = view.eps,
            dividendYield = view.dividendYield,
            foreignRatio = view.foreignRatio,
            revenueGrowth = view.revenueYoY,
        )
    }
}

data class CandleResponse(
    val date: LocalDate,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: Long,
) {
    companion object {
        fun from(candle: Candle) = CandleResponse(
            date = candle.date,
            open = candle.open,
            high = candle.high,
            low = candle.low,
            close = candle.close,
            volume = candle.volume,
        )
    }
}

data class InvestorFlowResponse(
    val date: LocalDate,
    val foreignNet: Long?,
    val institutionNet: Long?,
    val pensionNet: Long?,
    val foreignRatio: BigDecimal?,
) {
    companion object {
        fun from(flow: InvestorFlow) = InvestorFlowResponse(
            date = flow.tradeDate,
            foreignNet = flow.foreignNet,
            institutionNet = flow.institutionNet,
            pensionNet = flow.pensionNet,
            foreignRatio = flow.foreignRatio,
        )
    }
}

data class AnnualFinancialsResponse(
    val year: Int,
    val revenue: Long?,
    val operatingProfit: Long?,
    val netIncome: Long?,
    val operatingMargin: BigDecimal?,
    val roe: BigDecimal?,
    val debtRatio: BigDecimal?,
    val totalAssets: Long?,
    val separateAssets: Long?,
    val totalEquity: Long?,
    val totalDebt: Long?,
    val eps: BigDecimal?,
    val per: BigDecimal?,
    val pbr: BigDecimal?,
    val dps: BigDecimal?,
    val payoutRatio: BigDecimal?,
) {
    companion object {
        fun from(financials: AnnualFinancials) = AnnualFinancialsResponse(
            year = financials.year,
            revenue = financials.revenue,
            operatingProfit = financials.operatingProfit,
            netIncome = financials.netIncome,
            operatingMargin = financials.operatingMargin,
            roe = financials.roe,
            debtRatio = financials.debtRatio,
            totalAssets = financials.totalAssets,
            separateAssets = financials.separateAssets,
            totalEquity = financials.totalEquity,
            totalDebt = financials.totalDebt,
            eps = financials.eps,
            per = financials.per,
            pbr = financials.pbr,
            dps = financials.dps,
            payoutRatio = financials.payoutRatio,
        )
    }
}
