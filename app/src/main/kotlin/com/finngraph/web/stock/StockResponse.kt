package com.finngraph.web.stock

import com.fasterxml.jackson.annotation.JsonProperty
import com.finngraph.composition.ContractRole
import com.finngraph.composition.StockContract
import com.finngraph.stock.model.AnnualFinancials
import com.finngraph.stock.model.Candle
import com.finngraph.stock.model.CompanyProfile
import com.finngraph.stock.model.InvestorFlow
import com.finngraph.stock.model.StockDetailView
import com.finngraph.stock.model.StockListView
import com.finngraph.theme.model.PrimaryTheme
import java.math.BigDecimal
import java.time.LocalDate

data class StockSummaryResponse(
    val ticker: String,
    val name: String,
    val market: String,
    val price: BigDecimal?,
    val change: BigDecimal?,
    val changeAmount: BigDecimal?,
    val w1: BigDecimal?,
    val m1: BigDecimal?,
    val m3: BigDecimal?,
    val marketCap: Long?,
    val per: BigDecimal?,
    val pbr: BigDecimal?,
    val roe: BigDecimal?,
    val dividendYield: BigDecimal?,
    val themeId: Long?,
    val themeName: String?,
) {
    companion object {
        fun from(view: StockListView, primaryTheme: PrimaryTheme? = null) = StockSummaryResponse(
            ticker = view.ticker,
            name = view.name,
            market = view.market,
            price = view.price,
            change = view.change,
            changeAmount = view.changeAmount,
            w1 = view.w1,
            m1 = view.m1,
            m3 = view.m3,
            marketCap = view.marketCap,
            per = view.per,
            pbr = view.pbr,
            roe = view.roe,
            dividendYield = view.dividendYield,
            themeId = primaryTheme?.id,
            themeName = primaryTheme?.name,
        )
    }
}

data class CompanyProfileResponse(
    val ceoName: String?,
    val establishedOn: LocalDate?,
    val listedOn: LocalDate?,
    val fiscalMonth: String?,
    val listedShares: Long?,
    val parValue: BigDecimal?,
    val homepage: String?,
    val address: String?,
) {
    companion object {
        fun from(profile: CompanyProfile) = CompanyProfileResponse(
            ceoName = profile.ceoName,
            establishedOn = profile.establishedOn,
            listedOn = profile.listedOn,
            fiscalMonth = profile.fiscalMonth,
            listedShares = profile.listedShares,
            parValue = profile.parValue,
            homepage = profile.homepage,
            address = profile.address,
        )
    }
}

data class StockDetailResponse(
    val ticker: String,
    val name: String,
    val market: String,
    val price: BigDecimal?,
    val change: BigDecimal?,
    val changeAmount: BigDecimal?,
    val themeId: Long?,
    val themeName: String?,
    val marketCap: Long?,
    val per: BigDecimal?,
    val pbr: BigDecimal?,
    val roe: BigDecimal?,
    val eps: BigDecimal?,
    val dividendYield: BigDecimal?,
    val foreignRatio: BigDecimal?,
    val revenueGrowth: BigDecimal?,
    val description: String?,
    val descriptionSource: String?,
    val descriptionRceptNo: String?,
    val profile: CompanyProfileResponse,
    val baseDate: LocalDate?,
    val valuationDate: LocalDate?,
) {
    companion object {
        fun from(view: StockDetailView, primaryTheme: PrimaryTheme? = null) = StockDetailResponse(
            ticker = view.ticker,
            name = view.name,
            market = view.market,
            price = view.price,
            change = view.change,
            changeAmount = view.changeAmount,
            themeId = primaryTheme?.id,
            themeName = primaryTheme?.name,
            marketCap = view.marketCap,
            per = view.per,
            pbr = view.pbr,
            roe = view.roe,
            eps = view.eps,
            dividendYield = view.dividendYield,
            foreignRatio = view.foreignRatio,
            revenueGrowth = view.revenueYoY,
            description = view.description?.text,
            descriptionSource = view.description?.source,
            descriptionRceptNo = view.description?.rceptNo,
            profile = CompanyProfileResponse.from(view.profile),
            baseDate = view.baseDate,
            valuationDate = view.valuationDate,
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
    val changeRate: BigDecimal?,
) {
    companion object {
        fun from(candle: Candle) = CandleResponse(
            date = candle.date,
            open = candle.open,
            high = candle.high,
            low = candle.low,
            close = candle.close,
            volume = candle.volume,
            changeRate = candle.changeRate,
        )
    }
}

data class InvestorFlowResponse(
    val date: LocalDate,
    val foreignNet: Long?,
    val institutionNet: Long?,
    val individualNet: Long?,
    val foreignRatio: BigDecimal?,
) {
    companion object {
        fun from(flow: InvestorFlow) = InvestorFlowResponse(
            date = flow.tradeDate,
            foreignNet = flow.foreignNet,
            institutionNet = flow.institutionNet,
            individualNet = flow.individualNet,
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

data class StockContractResponse(
    val rceptNo: String,
    val rceptDate: LocalDate,
    val reportName: String,
    val role: ContractRole,
    val contractType: String?,
    val contractName: String?,
    val counterpartyName: String?,
    val counterpartyTicker: String?,
    val contractAmount: Long?,
    val salesRatio: BigDecimal?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val link: String,
    @get:JsonProperty("isCorrection") val isCorrection: Boolean,
) {
    companion object {
        fun from(stockContract: StockContract): StockContractResponse {
            val contract = stockContract.item.contract
            return StockContractResponse(
                rceptNo = contract.rceptNo,
                rceptDate = contract.rceptDate,
                reportName = contract.reportName,
                role = stockContract.role,
                contractType = contract.contractType,
                contractName = contract.contractName,
                counterpartyName = stockContract.counterpartyName,
                counterpartyTicker = stockContract.counterpartyTicker,
                contractAmount = stockContract.item.contractAmount,
                salesRatio = stockContract.item.salesRatio,
                startDate = contract.startDate,
                endDate = contract.endDate,
                link = contract.link,
                isCorrection = contract.isCorrection,
            )
        }
    }
}
