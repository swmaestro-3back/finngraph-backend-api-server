package com.finngraph.web.calendar

import com.finngraph.calendar.model.ActionStep
import com.finngraph.calendar.model.AgendaItem
import com.finngraph.composition.calendar.BonusMetrics
import com.finngraph.composition.calendar.DividendMetrics
import com.finngraph.composition.calendar.ExPrice
import com.finngraph.composition.calendar.RightsMetrics
import com.finngraph.composition.calendar.StockCalendarAction
import com.finngraph.composition.calendar.StockCalendarView
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

data class StockCalendarResponse(
    val ticker: String,
    val stockName: String,
    val market: String,
    val price: BigDecimal?,
    val change: BigDecimal?,
    val priceDate: LocalDate?,
    val from: LocalDate,
    val to: LocalDate,
    val asOf: OffsetDateTime?,
    val actions: List<CorporateActionResponse>,
) {
    companion object {
        fun from(view: StockCalendarView) = StockCalendarResponse(
            ticker = view.stock.ticker,
            stockName = view.stock.name,
            market = view.stock.market,
            price = view.stock.price,
            change = view.stock.change,
            priceDate = view.stock.baseDate,
            from = view.from,
            to = view.to,
            asOf = view.asOf,
            actions = view.actions.map(CorporateActionResponse::from),
        )
    }
}

data class CorporateActionResponse(
    val family: String,
    val label: String?,
    val basisDate: LocalDate,
    val lastBuyDate: LocalDate,
    val lastBuyEstimated: Boolean,
    val steps: List<ActionStepResponse>,
    val amount: BigDecimal?,
    val ratio: BigDecimal?,
    val agenda: List<AgendaItemResponse>,
    val agendaTruncated: Boolean,
    val dividend: DividendMetricsResponse?,
    val rights: RightsMetricsResponse?,
    val bonus: BonusMetricsResponse?,
) {
    companion object {
        fun from(entry: StockCalendarAction) = with(entry.action) {
            CorporateActionResponse(
                family = family.name,
                label = label,
                basisDate = basisDate,
                lastBuyDate = entry.lastBuy.date,
                lastBuyEstimated = entry.lastBuy.estimated,
                steps = steps.map(ActionStepResponse::from),
                amount = amount,
                ratio = ratio,
                agenda = agenda.map(AgendaItemResponse::from),
                agendaTruncated = agendaTruncated,
                dividend = entry.dividend?.let(DividendMetricsResponse::from),
                rights = entry.rights?.let(RightsMetricsResponse::from),
                bonus = entry.bonus?.let(BonusMetricsResponse::from),
            )
        }
    }
}

data class ActionStepResponse(val kind: String, val date: LocalDate, val endDate: LocalDate?, val estimated: Boolean) {
    companion object {
        fun from(step: ActionStep) = ActionStepResponse(step.kind.name, step.date, step.endDate, step.estimated)
    }
}

data class AgendaItemResponse(val text: String, val tags: List<String>) {
    companion object {
        fun from(item: AgendaItem) = AgendaItemResponse(item.text, item.tags)
    }
}

data class DividendMetricsResponse(val dps: BigDecimal?, val dpsBasis: String?, val expectedYield: BigDecimal?) {
    companion object {
        fun from(metrics: DividendMetrics) =
            DividendMetricsResponse(metrics.dps, metrics.dpsBasis?.name, metrics.expectedYield)
    }
}

data class ExPriceResponse(val theoretical: BigDecimal?, val basis: String, val actualOpen: BigDecimal?) {
    companion object {
        fun from(price: ExPrice) = ExPriceResponse(price.theoretical, price.basis.name, price.actualOpen)
    }
}

data class RightsMetricsResponse(
    val dilution: BigDecimal?,
    val issuePrice: BigDecimal?,
    val priceVsIssue: BigDecimal?,
    val exPrice: ExPriceResponse,
) {
    companion object {
        fun from(metrics: RightsMetrics) = RightsMetricsResponse(
            dilution = metrics.dilution,
            issuePrice = metrics.issuePrice,
            priceVsIssue = metrics.priceVsIssue,
            exPrice = ExPriceResponse.from(metrics.exPrice),
        )
    }
}

data class BonusMetricsResponse(
    val exPrice: ExPriceResponse,
    val returnAfter5: BigDecimal?,
    val returnAfter20: BigDecimal?,
) {
    companion object {
        fun from(metrics: BonusMetrics) = BonusMetricsResponse(
            exPrice = ExPriceResponse.from(metrics.exPrice),
            returnAfter5 = metrics.returnAfter5,
            returnAfter20 = metrics.returnAfter20,
        )
    }
}
