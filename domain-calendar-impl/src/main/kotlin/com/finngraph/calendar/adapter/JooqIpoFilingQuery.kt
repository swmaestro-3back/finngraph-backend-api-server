package com.finngraph.calendar.adapter

import com.finngraph.calendar.adapter.jooq.tables.references.COMPANIES
import com.finngraph.calendar.adapter.jooq.tables.references.IPO_FILINGS
import com.finngraph.calendar.model.CompanyProfile
import com.finngraph.calendar.model.FilingStatus
import com.finngraph.calendar.model.IpoFiling
import com.finngraph.calendar.port.IpoFilingPort
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Component
class JooqIpoFilingQuery(private val dsl: DSLContext) : IpoFilingPort {

    override fun findBySubscrStartBetween(from: LocalDate, to: LocalDate): List<IpoFiling> =
        select(IPO_FILINGS.SUBSCR_START.between(from, to))

    override fun findByTickers(tickers: Collection<String>): Map<String, IpoFiling> {
        if (tickers.isEmpty()) return emptyMap()
        return select(IPO_FILINGS.TICKER.`in`(tickers.distinct()))
            .mapNotNull { filing -> filing.ticker?.let { it to filing } }
            .toMap()
    }

    override fun findByCorpCode(corpCode: String): IpoFiling? =
        select(IPO_FILINGS.CORP_CODE.eq(corpCode)).firstOrNull()

    override fun findCompany(corpCode: String): CompanyProfile? {
        val c = COMPANIES
        return dsl.select(
            c.CORP_CODE,
            c.CEO_NAME,
            c.ESTABLISHED_ON,
            c.ADDRESS,
            c.HOMEPAGE,
            c.DESCRIPTION,
            c.DESCRIPTION_SOURCE,
            c.DESCRIPTION_RCEPT_NO,
        )
            .from(c)
            .where(c.CORP_CODE.eq(corpCode))
            .fetchOne {
                CompanyProfile(
                    corpCode = requireNotNull(it[c.CORP_CODE]),
                    ceo = it[c.CEO_NAME].present(),
                    establishedOn = it[c.ESTABLISHED_ON],
                    address = it[c.ADDRESS].present(),
                    homepage = it[c.HOMEPAGE].present(),
                    description = it[c.DESCRIPTION].present(),
                    descriptionSource = it[c.DESCRIPTION_SOURCE].present(),
                    descriptionRceptNo = it[c.DESCRIPTION_RCEPT_NO].present(),
                )
            }
    }

    override fun findLatestUpdatedAt(): OffsetDateTime? =
        dsl.select(DSL.max(IPO_FILINGS.UPDATED_AT))
            .from(IPO_FILINGS)
            .fetchOne()
            ?.value1()
            ?.withOffsetSameInstant(KST)

    private fun select(condition: Condition): List<IpoFiling> =
        dsl.selectFrom(IPO_FILINGS)
            .where(condition)
            .and(IPO_FILINGS.STATUS.`in`(VISIBLE_STATUSES))
            .orderBy(IPO_FILINGS.FIRST_FILED_ON, IPO_FILINGS.CORP_CODE)
            .fetch { toFiling(it) }

    private fun toFiling(record: Record): IpoFiling {
        val f = IPO_FILINGS
        return IpoFiling(
            corpCode = requireNotNull(record[f.CORP_CODE]),
            corpName = requireNotNull(record[f.CORP_NAME]),
            status = FilingStatus.valueOf(requireNotNull(record[f.STATUS])),
            spac = record[f.SPAC] == true,
            firstRceptNo = requireNotNull(record[f.FIRST_RCEPT_NO]),
            latestRceptNo = requireNotNull(record[f.LATEST_RCEPT_NO]),
            latestReportName = requireNotNull(record[f.LATEST_REPORT_NM]),
            subscrStart = record[f.SUBSCR_START],
            subscrEnd = record[f.SUBSCR_END],
            payDate = record[f.PAY_DATE],
            offerPrice = record[f.OFFER_PRICE],
            offerShares = record[f.OFFER_SHARES],
            offerAmount = record[f.OFFER_AMOUNT],
            offerMethod = record[f.OFFER_METHOD],
            underwriters = IpoFilingJson.underwriters(record[f.UNDERWRITERS]?.data()),
            fundUses = IpoFilingJson.fundUses(record[f.FUND_USES]?.data()),
            sellers = IpoFilingJson.sellers(record[f.SELLERS]?.data()),
            putback = IpoFilingJson.putback(record[f.PUTBACK]?.data()),
            ticker = record[f.TICKER],
        )
    }

    private fun String?.present(): String? = this?.takeIf { it.isNotBlank() }

    private companion object {
        val KST: ZoneOffset = ZoneOffset.ofHours(9)

        val VISIBLE_STATUSES: List<String> = FilingStatus.entries.map { it.name }
    }
}
