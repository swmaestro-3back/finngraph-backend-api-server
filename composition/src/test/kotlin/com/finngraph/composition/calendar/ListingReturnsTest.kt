package com.finngraph.composition.calendar

import com.finngraph.stock.model.Candle
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListingReturnsTest {

    private val listingDate = LocalDate.parse("2029-03-14")
    private val priceDate = LocalDate.parse("2029-03-30")

    private fun candle(date: String, open: String, close: String) =
        Candle(LocalDate.parse(date), BigDecimal(open), BigDecimal(open), BigDecimal(close), BigDecimal(close), 1000, null)

    @Test
    fun `공모가 대비 상장일 시초가·종가와 현재가 수익률을 계산한다`() {
        val result = ListingReturns.of(
            BigDecimal("10000"),
            listingDate,
            listOf(candle("2029-03-14", "20000", "18000"), candle("2029-03-15", "15000", "15000")),
            PricePoint(BigDecimal("12500"), priceDate),
        )

        assertEquals(
            AfterListing(
                listingDate = listingDate,
                open = BigDecimal("20000"),
                close = BigDecimal("18000"),
                openReturn = BigDecimal("100.0000"),
                closeReturn = BigDecimal("80.0000"),
                price = BigDecimal("12500"),
                currentReturn = BigDecimal("25.0000"),
                priceDate = priceDate,
            ),
            result,
        )
    }

    @Test
    fun `공모가 아래에서 시작하면 수익률이 음수이고 현재가가 없으면 현재 수익률만 비운다`() {
        val result = ListingReturns.of(
            BigDecimal("10000"),
            listingDate,
            listOf(candle("2029-03-14", "9000", "8500")),
            PricePoint(null, priceDate),
        )

        assertEquals(BigDecimal("-10.0000"), result?.openReturn)
        assertEquals(BigDecimal("-15.0000"), result?.closeReturn)
        assertNull(result?.price)
        assertNull(result?.currentReturn)
        assertEquals(priceDate, result?.priceDate)
    }

    @Test
    fun `상장일 일봉이 없으면 상장 후 성과를 내지 않는다`() {
        val current = PricePoint(BigDecimal("12500"), priceDate)

        assertNull(ListingReturns.of(BigDecimal("10000"), listingDate, listOf(candle("2029-03-15", "15000", "15000")), current))
        assertNull(ListingReturns.of(BigDecimal("10000"), listingDate, emptyList(), current))
    }

    @Test
    fun `공모가가 없으면 가격만 주고 수익률은 비운다`() {
        val result = ListingReturns.of(
            null,
            listingDate,
            listOf(candle("2029-03-14", "20000", "18000")),
            PricePoint(BigDecimal("12500"), priceDate),
        )

        assertEquals(BigDecimal("20000"), result?.open)
        assertNull(result?.openReturn)
        assertNull(result?.closeReturn)
        assertNull(result?.currentReturn)
    }
}
