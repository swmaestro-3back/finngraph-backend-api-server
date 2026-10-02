package com.finngraph.calendar.model

import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IpoListingsTest {

    private val today = LocalDate.parse("2026-10-02")

    private fun offering(ticker: String, start: String, price: String? = null) = IpoOffering(
        ticker = ticker,
        name = "예탁원$ticker",
        subscrStart = LocalDate.parse(start),
        subscrEnd = LocalDate.parse(start).plusDays(1),
        offerPrice = price?.let(::BigDecimal),
        payDate = LocalDate.parse(start).plusDays(3),
        refundDate = LocalDate.parse(start).plusDays(3),
        listingDate = null,
        leadManagers = "예탁원주관",
    )

    private fun filing(
        corpCode: String,
        start: String?,
        status: FilingStatus = FilingStatus.FILED,
        ticker: String? = null,
        end: String? = start,
        price: String? = "23000",
        spac: Boolean = false,
        underwriters: List<Underwriter> = emptyList(),
    ) = IpoFiling(
        corpCode = corpCode,
        corpName = "신고$corpCode",
        status = status,
        spac = spac,
        firstRceptNo = "20260901000001",
        latestRceptNo = "20260915000001",
        latestReportName = "증권신고서(지분증권)",
        subscrStart = start?.let(LocalDate::parse),
        subscrEnd = end?.let(LocalDate::parse),
        payDate = null,
        offerPrice = price?.let(::BigDecimal),
        offerShares = null,
        offerAmount = null,
        offerMethod = null,
        underwriters = underwriters,
        fundUses = emptyList(),
        sellers = emptyList(),
        putback = null,
        ticker = ticker,
    )

    @Test
    fun `예탁원 공모는 예탁원 일정·가격·상태를 쓰고 연결된 신고서에서 corpCode와 스팩만 가져온다`() {
        val listing = IpoListings.linked(
            offering("960001", "2026-10-05", price = "15000"),
            filing("C1", "2026-10-05", ticker = "960001", price = "14000", spac = true),
            today,
        )

        assertEquals("예탁원960001", listing.name)
        assertEquals("960001", listing.ticker)
        assertEquals("C1", listing.corpCode)
        assertEquals(true, listing.spac)
        assertEquals(IpoStatus.UPCOMING, listing.status)
        assertEquals(BigDecimal("15000"), listing.price)
        assertEquals(OfferPriceBasis.CONFIRMED, listing.priceBasis)
        assertEquals("예탁원주관", listing.leadManagers)
        assertEquals(
            IpoSchedule(
                LocalDate.parse("2026-10-05"),
                LocalDate.parse("2026-10-06"),
                LocalDate.parse("2026-10-08"),
                LocalDate.parse("2026-10-08"),
                null,
            ),
            listing.schedule,
        )
    }

    @Test
    fun `예탁원 공모가가 비어 있으면 신고서 가격으로 채우지 않는다`() {
        val listing = IpoListings.linked(offering("960001", "2026-10-05"), filing("C1", "2026-10-05", ticker = "960001"), today)

        assertNull(listing.price)
        assertEquals(OfferPriceBasis.CONFIRMED, listing.priceBasis)
    }

    @Test
    fun `예탁원 행만 있으면 corpCode가 비고 스팩이 아니다`() {
        val listing = IpoListings.linked(offering("960001", "2026-10-05"), null, today)

        assertNull(listing.corpCode)
        assertEquals(false, listing.spac)
        assertNull(listing.filing)
    }

    @Test
    fun `예탁원 행만 있어도 이름이 스팩이면 스팩이다`() {
        val formal = IpoListings.linked(offering("960001", "2026-10-05").copy(name = "한국제17호기업인수목적"), null, today)
        val short = IpoListings.linked(offering("960002", "2026-10-05").copy(name = "엔에이치스팩34호"), null, today)

        assertEquals(true, formal.spac)
        assertEquals(true, short.spac)
    }

    @Test
    fun `신고서 공모는 FILED이고 발행조건확정이어도 가격은 예정가다`() {
        val filed = IpoListings.filed(filing("C2", "2026-10-20"))
        val priced = IpoListings.filed(filing("C3", "2026-10-20", FilingStatus.PRICED))

        assertEquals(IpoStatus.FILED, filed.status)
        assertEquals(OfferPriceBasis.PLANNED, filed.priceBasis)
        assertEquals(BigDecimal("23000"), filed.price)
        assertNull(filed.ticker)
        assertEquals("신고C2", filed.name)
        assertEquals(
            IpoSchedule(LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-20"), null, null, null),
            filed.schedule,
        )
        assertEquals(IpoStatus.FILED, priced.status)
        assertEquals(OfferPriceBasis.PLANNED, priced.priceBasis)
    }

    @Test
    fun `신고서 카드 주관사는 대표 인수인 이름을 잇고 없으면 비운다`() {
        val underwriters = listOf(
            Underwriter("가증권", "대표", null, null, null),
            Underwriter("나증권", "인수", null, null, null),
            Underwriter("다증권", "대표", null, null, null),
        )

        assertEquals("가증권, 다증권", IpoListings.filed(filing("C2", "2026-10-20", underwriters = underwriters)).leadManagers)
        assertNull(IpoListings.filed(filing("C2", "2026-10-20")).leadManagers)
    }

    @Test
    fun `보드는 연결된 신고서를 예탁원 카드로 합치고 나머지 신고서만 청약 시작 내림차순으로 더한다`() {
        val linked = filing("C1", "2026-10-05", ticker = "960001")

        val board = IpoListings.board(
            today,
            listOf(offering("960001", "2026-10-05", price = "15000")),
            mapOf("960001" to linked),
            listOf(linked, filing("C2", "2026-10-20"), filing("C3", "2026-10-10", ticker = "999999")),
        )

        assertEquals(
            listOf(
                Triple(null, "C2", IpoStatus.FILED),
                Triple("999999", "C3", IpoStatus.FILED),
                Triple("960001", "C1", IpoStatus.UPCOMING),
            ),
            board.map { Triple(it.ticker, it.corpCode, it.status) },
        )
    }

    @Test
    fun `신고서 카드는 청약 시작이 오늘부터 60일까지이고 청약 기간이 비면 뺀다`() {
        val board = IpoListings.board(
            today,
            emptyList(),
            emptyMap(),
            listOf(
                filing("TODAY", "2026-10-02"),
                filing("D60", "2026-12-01"),
                filing("D61", "2026-12-02"),
                filing("PAST", "2026-10-01"),
                filing("NOEND", "2026-10-10", end = null),
                filing("NODATE", null),
            ),
        )

        assertEquals(listOf("D60", "TODAY"), board.map { it.corpCode })
    }
}
