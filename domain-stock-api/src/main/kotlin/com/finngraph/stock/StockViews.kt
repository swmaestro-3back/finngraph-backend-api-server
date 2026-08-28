package com.finngraph.stock

import java.math.BigDecimal
import java.time.LocalDate

data class StockListView(
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
)

data class StockDetailView(
    val ticker: String,
    val name: String,
    val market: String,
    val price: BigDecimal?,
    val change: BigDecimal?,
    val marketCap: Long?,
    val per: BigDecimal?,
    val pbr: BigDecimal?,
    val roe: BigDecimal?,
    val eps: BigDecimal?,
    val dividendYield: BigDecimal?,
    val foreignRatio: BigDecimal?,
    val revenueYoY: BigDecimal?,
    val baseDate: LocalDate?,
)

data class StockPriceView(
    val ticker: String,
    val name: String,
    val market: String,
    val price: BigDecimal?,
    val change: BigDecimal?,
    val marketCap: Long?,
)

data class Candle(
    val date: LocalDate,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: Long,
    val tradeValue: Long?,
)

data class InvestorFlow(
    val tradeDate: LocalDate,
    val foreignNet: Long?,
    val institutionNet: Long?,
    val pensionNet: Long?,
    val personalNet: Long?,
    val foreignRatio: BigDecimal?,
)

data class AnnualFinancials(
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
)
