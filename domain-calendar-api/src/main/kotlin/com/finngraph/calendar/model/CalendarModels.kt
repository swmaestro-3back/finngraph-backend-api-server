package com.finngraph.calendar.model

import java.math.BigDecimal
import java.time.LocalDate

enum class EventKind {
    DIV_EX,
    DIV_RECORD,
    DIV_PAY,
    BONUS_EX,
    BONUS_LIST,
    RIGHTS_EX,
    RIGHTS_SUBSCRIBE,
    RIGHTS_LIST,
    AGM,
}

data class CalendarEvent(
    val date: LocalDate,
    val kind: EventKind,
    val ticker: String,
    val stockName: String,
    val endDate: LocalDate?,
    val amount: BigDecimal?,
    val ratio: BigDecimal?,
    val label: String?,
    val agenda: List<String>,
    val agendaTruncated: Boolean,
    val estimated: Boolean,
)

data class IpoOffering(
    val ticker: String,
    val name: String,
    val subscrStart: LocalDate,
    val subscrEnd: LocalDate,
    val offerPrice: BigDecimal?,
    val payDate: LocalDate?,
    val refundDate: LocalDate?,
    val listingDate: LocalDate?,
    val leadManagers: String?,
)

enum class IpoStatus {
    UPCOMING,
    SUBSCRIBING,
    LISTING_PENDING,
    LISTED,
    ;

    companion object {
        fun of(offering: IpoOffering, today: LocalDate): IpoStatus {
            val listingDate = offering.listingDate
            return when {
                today < offering.subscrStart -> UPCOMING
                today <= offering.subscrEnd -> SUBSCRIBING
                listingDate == null || today < listingDate -> LISTING_PENDING
                else -> LISTED
            }
        }
    }
}
