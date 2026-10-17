package com.finngraph.calendar.model

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class IpoStatusTest {

    private fun offering(start: String, end: String, listing: String?) = IpoOffering(
        ticker = "960101",
        name = "공모",
        subscrStart = LocalDate.parse(start),
        subscrEnd = LocalDate.parse(end),
        offerPrice = null,
        payDate = null,
        refundDate = null,
        listingDate = listing?.let(LocalDate::parse),
        leadManagers = null,
    )

    @Test
    fun `청약 시작 전이면 청약 예정`() {
        assertEquals(IpoStatus.UPCOMING, IpoStatus.of(offering("2026-10-05", "2026-10-06", null), LocalDate.parse("2026-10-04")))
    }

    @Test
    fun `청약 첫날과 마지막 날은 청약 중`() {
        val target = offering("2026-10-05", "2026-10-06", null)

        assertEquals(IpoStatus.SUBSCRIBING, IpoStatus.of(target, LocalDate.parse("2026-10-05")))
        assertEquals(IpoStatus.SUBSCRIBING, IpoStatus.of(target, LocalDate.parse("2026-10-06")))
    }

    @Test
    fun `청약이 끝나고 상장일이 없거나 아직이면 상장 예정`() {
        val today = LocalDate.parse("2026-10-07")

        assertEquals(IpoStatus.LISTING_PENDING, IpoStatus.of(offering("2026-10-05", "2026-10-06", null), today))
        assertEquals(IpoStatus.LISTING_PENDING, IpoStatus.of(offering("2026-10-05", "2026-10-06", "2026-10-14"), today))
    }

    @Test
    fun `상장일 당일부터 상장`() {
        assertEquals(IpoStatus.LISTED, IpoStatus.of(offering("2026-10-05", "2026-10-06", "2026-10-14"), LocalDate.parse("2026-10-14")))
    }
}
