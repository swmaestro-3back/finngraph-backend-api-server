package com.finngraph.calendar.port

import com.finngraph.calendar.model.IpoOffering
import java.time.LocalDate
import java.time.OffsetDateTime

interface IpoOfferingPort {

    fun findOverlapping(from: LocalDate, to: LocalDate): List<IpoOffering>
    fun findLatestByTickers(tickers: Collection<String>): Map<String, IpoOffering>
    fun findLatestUpdatedAt(): OffsetDateTime?
}
