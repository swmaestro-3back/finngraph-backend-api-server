package com.finngraph.calendar.model

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OfferingStructureTest {

    @Test
    fun `자금 용도 비중은 합계 대비 백분율이다`() {
        val shares = OfferingStructure.fundUses(
            listOf(FundUse("시설자금", BigDecimal("31320000000")), FundUse("운영자금", BigDecimal("35540000"))),
        )

        assertEquals(
            listOf(
                FundUseShare("시설자금", BigDecimal("31320000000"), BigDecimal("99.8867")),
                FundUseShare("운영자금", BigDecimal("35540000"), BigDecimal("0.1133")),
            ),
            shares,
        )
    }

    @Test
    fun `자금 합계가 0이면 비중을 비운다`() {
        assertEquals(
            listOf(FundUseShare("기타", BigDecimal.ZERO, null)),
            OfferingStructure.fundUses(listOf(FundUse("기타", BigDecimal.ZERO))),
        )
        assertEquals(emptyList<FundUseShare>(), OfferingStructure.fundUses(emptyList()))
    }

    @Test
    fun `구주매출 비중은 매출 수량 합계를 공모 수량으로 나누고 계산할 수 없으면 비운다`() {
        val sellers = listOf(
            Seller("최대주주", "최대주주", 1000000, 300000, 700000),
            Seller("벤처캐피탈", "기타", 500000, 106000, 394000),
        )

        assertEquals(BigDecimal("20.0000"), OfferingStructure.oldShareRatio(sellers, 2030000))
        assertNull(OfferingStructure.oldShareRatio(emptyList(), 2030000))
        assertNull(OfferingStructure.oldShareRatio(sellers, null))
        assertNull(OfferingStructure.oldShareRatio(sellers, 0))
        assertNull(OfferingStructure.oldShareRatio(listOf(Seller("최대주주", null, null, null, null)), 2030000))
    }

    private val underwriters = listOf(
        Underwriter("대신증권", "대표", 2500000, BigDecimal("26750000000"), "총액인수"),
        Underwriter("나증권", "인수", null, BigDecimal("100"), null),
    )

    private fun filing(shares: Long?) = IpoFiling(
        corpCode = "C1",
        corpName = "멜콘",
        status = FilingStatus.PRICED,
        spac = false,
        firstRceptNo = "20260821000368",
        latestRceptNo = "20260929000194",
        latestReportName = "[기재정정]투자설명서",
        subscrStart = null,
        subscrEnd = null,
        payDate = null,
        offerPrice = BigDecimal("10700"),
        offerShares = shares,
        offerAmount = BigDecimal("26750000000"),
        offerMethod = null,
        underwriters = underwriters,
        fundUses = emptyList(),
        sellers = emptyList(),
        putback = null,
        ticker = "179880",
    )

    private fun listing(price: String?, basis: OfferPriceBasis, filing: IpoFiling?) = IpoListing(
        name = "멜콘",
        ticker = "179880",
        corpCode = "C1",
        spac = false,
        status = IpoStatus.SUBSCRIBING,
        schedule = IpoSchedule(null, null, null, null, null),
        price = price?.let(::BigDecimal),
        priceBasis = basis,
        leadManagers = null,
        filing = filing,
    )

    private fun structure(uses: List<FundUse>, amount: String?, shares: Long?, sellers: List<Seller> = emptyList()) =
        filing(shares).copy(fundUses = uses, offerAmount = amount?.let(::BigDecimal), sellers = sellers)

    @Test
    fun `자금 용도 합계가 신주 공모 금액의 ±30% 안이면 믿을 수 있다`() {
        val uses = listOf(FundUse("시설자금", BigDecimal("31320000000")), FundUse("기타", BigDecimal("16369994000")))

        assertEquals(true, OfferingStructure.fundUsesConsistent(structure(uses, "46690000000", 2030000)))
        assertEquals(true, OfferingStructure.fundUsesConsistent(structure(uses, null, 2030000)))
        assertEquals(true, OfferingStructure.fundUsesConsistent(structure(emptyList(), "46690000000", 2030000)))
    }

    @Test
    fun `구주매출 몫은 회사 자금이 아니므로 신주 몫과 비교한다`() {
        val uses = listOf(FundUse("시설자금", BigDecimal("31320000000")), FundUse("운영자금", BigDecimal("35540000")))
        val sellers = listOf(Seller("최대주주", null, 1000000, 406000, 594000))

        assertEquals(true, OfferingStructure.fundUsesConsistent(structure(uses, "46690000000", 2030000, sellers)))
        assertEquals(false, OfferingStructure.fundUsesConsistent(structure(uses, "46690000000", 2030000)))
    }

    @Test
    fun `합계가 신주 몫과 크게 다르면 원천 오류로 본다`() {
        val doubled = listOf(
            FundUse("운영자금", BigDecimal("7498415000")),
            FundUse("시설투자", BigDecimal("4500000000")),
            FundUse("발행제비용", BigDecimal("11998415000")),
        )
        val short = listOf(FundUse("운영자금", BigDecimal("5000000000")))

        assertEquals(false, OfferingStructure.fundUsesConsistent(structure(doubled, "12300000000", 1000000)))
        assertEquals(false, OfferingStructure.fundUsesConsistent(structure(short, "12300000000", 1000000)))
    }

    @Test
    fun `확정가면 공모 총액과 인수 금액을 확정가로 다시 계산한다`() {
        val confirmed = listing("12300", OfferPriceBasis.CONFIRMED, filing(2500000))

        assertEquals(BigDecimal("30750000000"), OfferingStructure.offerAmount(confirmed))
        assertEquals(
            listOf(BigDecimal("30750000000"), null),
            OfferingStructure.underwriters(confirmed)?.map { it.amount },
        )
    }

    @Test
    fun `예정가면 신고서 금액을 그대로 쓴다`() {
        val planned = listing("10700", OfferPriceBasis.PLANNED, filing(2500000))

        assertEquals(BigDecimal("26750000000"), OfferingStructure.offerAmount(planned))
        assertEquals(underwriters, OfferingStructure.underwriters(planned))
    }

    @Test
    fun `확정가나 수량이 없으면 총액을 비우고 신고서가 없으면 둘 다 비운다`() {
        assertNull(OfferingStructure.offerAmount(listing(null, OfferPriceBasis.CONFIRMED, filing(2500000))))
        assertNull(OfferingStructure.offerAmount(listing("12300", OfferPriceBasis.CONFIRMED, filing(null))))
        assertNull(OfferingStructure.offerAmount(listing("12300", OfferPriceBasis.CONFIRMED, null)))
        assertNull(OfferingStructure.underwriters(listing("12300", OfferPriceBasis.CONFIRMED, null)))
    }
}
