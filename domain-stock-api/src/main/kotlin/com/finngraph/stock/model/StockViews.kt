package com.finngraph.stock.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class StockListView(
    val ticker: String,
    val name: String,
    val market: String,
    val price: BigDecimal?,
    val change: BigDecimal?,
    val changeAmount: BigDecimal?,
    val tradeValue: Long?,
    val week52: Week52Range?,
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
    val changeAmount: BigDecimal?,
    val tradeValue: Long?,
    val week52: Week52Range?,
    val marketCap: Long?,
    val per: BigDecimal?,
    val pbr: BigDecimal?,
    val roe: BigDecimal?,
    val eps: BigDecimal?,
    val dividendYield: BigDecimal?,
    val foreignRatio: BigDecimal?,
    val revenueYoY: BigDecimal?,
    val baseDate: LocalDate?,
    val description: CompanyDescription?,
    val profile: CompanyProfile,
    val valuationDate: LocalDate? = baseDate,
    val tradeValueAverage: TradeValueAverage? = null,
) {
    val tradeValueRatio: BigDecimal?
        get() {
            val average = tradeValueAverage?.value?.takeIf { it > 0 } ?: return null
            val today = tradeValue ?: return null
            return BigDecimal(today).divide(BigDecimal(average), RATIO_SCALE, RoundingMode.HALF_UP)
        }

    companion object {
        const val RATIO_SCALE = 2
    }
}

data class TradeValueAverage(
    val value: Long,
    val days: Int,
)

data class Week52Range(
    val high: BigDecimal,
    val highDate: LocalDate,
    val low: BigDecimal,
    val lowDate: LocalDate,
) {
    companion object {
        const val MONTHS = 12L

        fun exclusiveStart(priceDate: LocalDate): LocalDate = priceDate.minusMonths(MONTHS)
    }
}

data class CompanyDescription(
    val text: String,
    val source: String?,
    val rceptNo: String?,
)

data class CompanyProfile(
    val ceoName: String?,
    val establishedOn: LocalDate?,
    val listedOn: LocalDate?,
    val fiscalMonth: String?,
    val listedShares: Long?,
    val parValue: BigDecimal?,
    val homepage: String?,
    val address: String?,
)

data class StockFlags(
    val ticker: String,
    val name: String,
    val market: String,
    val underAdministration: Boolean,
    val tradingSuspended: Boolean,
    val delistingTrade: Boolean,
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
    val changeRate: BigDecimal? = null,
)

data class InvestorFlow(
    val tradeDate: LocalDate,
    val foreignNet: Long?,
    val institutionNet: Long?,
    val individualNet: Long?,
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
    val bps: BigDecimal? = null,
)

data class QuarterlyFinancials(
    val year: Int,
    val quarter: Int,
    val periodEnd: String,
    val fsDiv: String?,
    val revenue: Long?,
    val operatingProfit: Long?,
    val netIncome: Long?,
    val operatingMargin: BigDecimal?,
    val bps: BigDecimal?,
    val revenueYoY: BigDecimal?,
    val operatingProfitYoY: BigDecimal?,
)
