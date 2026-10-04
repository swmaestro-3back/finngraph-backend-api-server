package com.finngraph.calendar.port

import com.finngraph.calendar.model.CompanyProfile
import com.finngraph.calendar.model.IpoFiling
import java.time.LocalDate
import java.time.OffsetDateTime

interface IpoFilingPort {

    fun findBySubscrStartBetween(from: LocalDate, to: LocalDate): List<IpoFiling>
    fun findByTickers(tickers: Collection<String>): Map<String, IpoFiling>
    fun findByCorpCode(corpCode: String): IpoFiling?
    fun findCompany(corpCode: String): CompanyProfile?
    fun findLatestUpdatedAt(): OffsetDateTime?
}
