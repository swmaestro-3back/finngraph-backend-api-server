package com.finngraph.web.stock

import com.fasterxml.jackson.annotation.JsonProperty
import com.finngraph.composition.ContractRole
import com.finngraph.composition.StockContract
import com.finngraph.stock.model.AnnualFinancials
import com.finngraph.stock.model.Candle
import com.finngraph.stock.model.CompanyProfile
import com.finngraph.stock.model.InvestorFlow
import com.finngraph.stock.model.QuarterlyFinancials
import com.finngraph.stock.model.StockDetailView
import com.finngraph.stock.model.StockListView
import com.finngraph.theme.model.PrimaryTheme
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.LocalDate

data class StockSummaryResponse(
    val ticker: String,
    val name: String,
    val market: String,
    val price: BigDecimal?,
    val change: BigDecimal?,
    val changeAmount: BigDecimal?,
    val tradeValue: Long?,
    val high52w: BigDecimal?,
    val high52wDate: LocalDate?,
    val low52w: BigDecimal?,
    val low52wDate: LocalDate?,
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
            tradeValue = view.tradeValue,
            high52w = view.week52?.high,
            high52wDate = view.week52?.highDate,
            low52w = view.week52?.low,
            low52wDate = view.week52?.lowDate,
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
    val tradeValue: Long?,
    val high52w: BigDecimal?,
    val high52wDate: LocalDate?,
    val low52w: BigDecimal?,
    val low52wDate: LocalDate?,
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
    @Schema(description = "기준일 직전 시장 거래일 20일(테마 거래대금 배수와 같은 달력) 중 이 종목이 거래된 날(거래량 > 0)의 평균 거래대금(원). 그런 날이 5일 미만이면 null")
    val tradeValueAverage: Long?,
    @Schema(description = "tradeValueAverage를 낸 거래일 수(5~20). 평균이 없으면 0")
    val tradeValueAverageDays: Int,
    @Schema(description = "기준일 거래대금 ÷ tradeValueAverage(소수 둘째 자리). 둘 중 하나가 없으면 null")
    val tradeValueRatio: BigDecimal?,
) {
    companion object {
        fun from(view: StockDetailView, primaryTheme: PrimaryTheme? = null) = StockDetailResponse(
            ticker = view.ticker,
            name = view.name,
            market = view.market,
            price = view.price,
            change = view.change,
            changeAmount = view.changeAmount,
            tradeValue = view.tradeValue,
            high52w = view.week52?.high,
            high52wDate = view.week52?.highDate,
            low52w = view.week52?.low,
            low52wDate = view.week52?.lowDate,
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
            tradeValueAverage = view.tradeValueAverage?.value,
            tradeValueAverageDays = view.tradeValueAverage?.days ?: 0,
            tradeValueRatio = view.tradeValueRatio,
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
    @Schema(description = "주당순자산(원). 대표 재무제표(연결 우선)의 연간 값")
    val bps: BigDecimal?,
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
            bps = financials.bps,
        )
    }
}

data class QuarterlyFinancialsResponse(
    @Schema(description = "분기 말 월의 연도")
    val year: Int,
    @Schema(description = "분기 말 월의 달력 분기(1~4). 12월 결산이면 회계 분기와 같고, 그 밖의 결산월이면 다를 수 있다")
    val quarter: Int,
    @Schema(description = "분기 말 월(YYYY-MM, company_financials.fiscal_yymm)", example = "2026-06")
    val periodEnd: String,
    @Schema(description = "재무제표 구분. CFS: 연결, OFS: 별도. 같은 분기에 둘 다 있으면 연결", allowableValues = ["CFS", "OFS"])
    val fsDiv: String?,
    @Schema(description = "분기 단독 매출액(원)")
    val revenue: Long?,
    @Schema(description = "분기 단독 영업이익(원)")
    val operatingProfit: Long?,
    @Schema(description = "분기 단독 순이익(원)")
    val netIncome: Long?,
    @Schema(description = "영업이익률(%) = 영업이익 ÷ 매출액 × 100")
    val operatingMargin: BigDecimal?,
    @Schema(description = "분기 말 주당순자산(원)")
    val bps: BigDecimal?,
    @Schema(description = "1년 전 같은 분기 대비 매출 증감률(%). 1년 전 값이 없거나 0 이하이면 null")
    val revenueYoY: BigDecimal?,
    @Schema(description = "1년 전 같은 분기 대비 영업이익 증감률(%). 1년 전 값이 없거나 0 이하이면 null")
    val operatingProfitYoY: BigDecimal?,
) {
    companion object {
        fun from(financials: QuarterlyFinancials) = QuarterlyFinancialsResponse(
            year = financials.year,
            quarter = financials.quarter,
            periodEnd = financials.periodEnd,
            fsDiv = financials.fsDiv,
            revenue = financials.revenue,
            operatingProfit = financials.operatingProfit,
            netIncome = financials.netIncome,
            operatingMargin = financials.operatingMargin,
            bps = financials.bps,
            revenueYoY = financials.revenueYoY,
            operatingProfitYoY = financials.operatingProfitYoY,
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
