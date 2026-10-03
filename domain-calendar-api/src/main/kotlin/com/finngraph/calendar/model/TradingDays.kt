package com.finngraph.calendar.model

import java.time.DayOfWeek
import java.time.LocalDate

data class LastBuy(val date: LocalDate, val estimated: Boolean)

object TradingDays {

    private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    fun lastBuy(basis: LocalDate, openByDate: Map<LocalDate, Boolean>): LastBuy {
        var estimated = false
        val isOpen: (LocalDate) -> Boolean = { date ->
            openByDate[date] ?: run {
                estimated = true
                date.dayOfWeek !in WEEKEND
            }
        }
        val effective = lastOpenOnOrBefore(basis, isOpen)
        val exDate = lastOpenOnOrBefore(effective.minusDays(1), isOpen)
        val date = lastOpenOnOrBefore(exDate.minusDays(1), isOpen)
        return LastBuy(date, estimated)
    }

    private fun lastOpenOnOrBefore(date: LocalDate, isOpen: (LocalDate) -> Boolean): LocalDate =
        generateSequence(date) { it.minusDays(1) }.first(isOpen)
}
