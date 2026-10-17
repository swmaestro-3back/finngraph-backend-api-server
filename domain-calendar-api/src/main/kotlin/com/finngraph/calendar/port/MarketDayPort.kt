package com.finngraph.calendar.port

import java.time.LocalDate

interface MarketDayPort {

    fun findClosedDates(from: LocalDate, to: LocalDate): List<LocalDate>
    fun findDays(from: LocalDate, to: LocalDate): Map<LocalDate, Boolean>
}
