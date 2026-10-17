package com.finngraph.composition.calendar

import com.finngraph.stock.model.Candle
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class AfterListing(
    val listingDate: LocalDate,
    val open: BigDecimal,
    val close: BigDecimal,
    val openReturn: BigDecimal?,
    val closeReturn: BigDecimal?,
    val price: BigDecimal?,
    val currentReturn: BigDecimal?,
    val priceDate: LocalDate?,
)

object ListingReturns {

    private const val SCALE = 4
    private val HUNDRED = BigDecimal("100")

    fun of(offerPrice: BigDecimal?, listingDate: LocalDate, daily: List<Candle>, current: PricePoint): AfterListing? {
        val first = daily.firstOrNull { it.date == listingDate } ?: return null
        return AfterListing(
            listingDate = listingDate,
            open = first.open,
            close = first.close,
            openReturn = change(first.open, offerPrice),
            closeReturn = change(first.close, offerPrice),
            price = current.price,
            currentReturn = change(current.price, offerPrice),
            priceDate = current.date,
        )
    }

    private fun change(price: BigDecimal?, base: BigDecimal?): BigDecimal? {
        if (price == null || base == null || base.signum() == 0) return null
        return (price - base).multiply(HUNDRED).divide(base, SCALE, RoundingMode.HALF_UP)
    }
}
