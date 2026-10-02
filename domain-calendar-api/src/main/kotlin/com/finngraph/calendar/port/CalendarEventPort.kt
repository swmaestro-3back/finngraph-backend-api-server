package com.finngraph.calendar.port

import com.finngraph.calendar.model.CalendarEvent
import java.time.LocalDate
import java.time.OffsetDateTime

interface CalendarEventPort {

    fun findByTickers(tickers: Collection<String>, from: LocalDate, to: LocalDate): List<CalendarEvent>
    fun findLatestUpdatedAt(): OffsetDateTime?
}
