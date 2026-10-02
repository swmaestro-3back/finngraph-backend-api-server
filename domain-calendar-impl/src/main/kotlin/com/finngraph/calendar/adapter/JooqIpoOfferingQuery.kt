package com.finngraph.calendar.adapter

import com.finngraph.calendar.adapter.jooq.tables.references.IPO_OFFERINGS
import com.finngraph.calendar.model.IpoOffering
import com.finngraph.calendar.port.IpoOfferingPort
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Component
class JooqIpoOfferingQuery(private val dsl: DSLContext) : IpoOfferingPort {

    override fun findOverlapping(from: LocalDate, to: LocalDate): List<IpoOffering> {
        val o = IPO_OFFERINGS
        return dsl.select(
            o.TICKER,
            o.NAME,
            o.SUBSCR_START,
            o.SUBSCR_END,
            o.OFFER_PRICE,
            o.PAY_DATE,
            o.REFUND_DATE,
            o.LISTING_DATE,
            o.LEAD_MANAGERS,
        )
            .from(o)
            .where(o.SUBSCR_START.le(to))
            .and(DSL.coalesce(o.LISTING_DATE, o.SUBSCR_END).ge(from))
            .orderBy(o.SUBSCR_START.desc(), o.TICKER)
            .fetch {
                IpoOffering(
                    ticker = requireNotNull(it[o.TICKER]),
                    name = requireNotNull(it[o.NAME]),
                    subscrStart = requireNotNull(it[o.SUBSCR_START]),
                    subscrEnd = requireNotNull(it[o.SUBSCR_END]),
                    offerPrice = it[o.OFFER_PRICE],
                    payDate = it[o.PAY_DATE],
                    refundDate = it[o.REFUND_DATE],
                    listingDate = it[o.LISTING_DATE],
                    leadManagers = it[o.LEAD_MANAGERS],
                )
            }
    }

    override fun findLatestUpdatedAt(): OffsetDateTime? =
        dsl.select(DSL.max(IPO_OFFERINGS.UPDATED_AT))
            .from(IPO_OFFERINGS)
            .fetchOne()
            ?.value1()
            ?.withOffsetSameInstant(KST)

    private companion object {
        val KST: ZoneOffset = ZoneOffset.ofHours(9)
    }
}
