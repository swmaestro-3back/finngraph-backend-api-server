package com.finngraph.web.calendar

import com.finngraph.composition.calendar.CalendarEntry
import com.finngraph.composition.calendar.CalendarView
import com.finngraph.composition.calendar.IpoBoardView
import com.finngraph.composition.calendar.IpoCard
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

data class CalendarResponse(
    val from: LocalDate,
    val to: LocalDate,
    val asOf: OffsetDateTime?,
    val closedDates: List<LocalDate>,
    val events: List<CalendarEventResponse>,
) {
    companion object {
        fun from(view: CalendarView) = CalendarResponse(
            from = view.from,
            to = view.to,
            asOf = view.asOf,
            closedDates = view.closedDates,
            events = view.entries.map(CalendarEventResponse::from),
        )
    }
}

data class CalendarEventResponse(
    val date: LocalDate,
    val kind: String,
    val ticker: String,
    val stockName: String,
    val endDate: LocalDate?,
    val amount: BigDecimal?,
    val ratio: BigDecimal?,
    val label: String?,
    val agenda: List<String>,
    val agendaTruncated: Boolean,
    val estimated: Boolean,
    val favorite: Boolean,
) {
    companion object {
        fun from(entry: CalendarEntry) = with(entry.event) {
            CalendarEventResponse(
                date = date,
                kind = kind.name,
                ticker = ticker,
                stockName = stockName,
                endDate = endDate,
                amount = amount,
                ratio = ratio,
                label = label,
                agenda = agenda,
                agendaTruncated = agendaTruncated,
                estimated = estimated,
                favorite = entry.favorite,
            )
        }
    }
}

data class IpoListResponse(val asOf: OffsetDateTime?, val offerings: List<IpoResponse>) {
    companion object {
        fun from(view: IpoBoardView) = IpoListResponse(view.asOf, view.offerings.map(IpoResponse::from))
    }
}

data class IpoResponse(
    val ticker: String,
    val name: String,
    val status: String,
    val subscrStart: LocalDate,
    val subscrEnd: LocalDate,
    val offerPrice: BigDecimal?,
    val leadManagers: String?,
    val payDate: LocalDate?,
    val refundDate: LocalDate?,
    val listingDate: LocalDate?,
) {
    companion object {
        fun from(card: IpoCard) = with(card.offering) {
            IpoResponse(
                ticker = ticker,
                name = name,
                status = card.status.name,
                subscrStart = subscrStart,
                subscrEnd = subscrEnd,
                offerPrice = offerPrice,
                leadManagers = leadManagers,
                payDate = payDate,
                refundDate = refundDate,
                listingDate = listingDate,
            )
        }
    }
}
