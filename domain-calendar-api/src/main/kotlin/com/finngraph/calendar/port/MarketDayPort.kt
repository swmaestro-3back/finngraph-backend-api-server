package com.finngraph.calendar.port

import java.time.LocalDate

interface MarketDayPort {

    fun findClosedDates(from: LocalDate, to: LocalDate): List<LocalDate>
}
