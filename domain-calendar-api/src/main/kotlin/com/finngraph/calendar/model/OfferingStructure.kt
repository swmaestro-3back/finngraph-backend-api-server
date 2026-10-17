package com.finngraph.calendar.model

import java.math.BigDecimal
import java.math.RoundingMode

data class FundUseShare(val purpose: String, val amount: BigDecimal, val share: BigDecimal?)

object OfferingStructure {

    private const val SCALE = 4
    private val HUNDRED = BigDecimal("100")
    private val CONSISTENT_RANGE = BigDecimal("0.7")..BigDecimal("1.3")

    fun fundUses(uses: List<FundUse>): List<FundUseShare> {
        val total = uses.fold(BigDecimal.ZERO) { sum, use -> sum + use.amount }
        return uses.map { FundUseShare(it.purpose, it.amount, percent(it.amount, total)) }
    }

    fun oldShareRatio(sellers: List<Seller>, offerShares: Long?): BigDecimal? {
        val sold = sellers.mapNotNull { it.sold }
        if (sold.isEmpty() || offerShares == null) return null
        return percent(BigDecimal.valueOf(sold.sum()), BigDecimal.valueOf(offerShares))
    }

    fun fundUsesConsistent(filing: IpoFiling): Boolean {
        val amount = filing.offerAmount
        if (filing.fundUses.isEmpty() || amount == null || amount.signum() <= 0) return true
        val total = filing.fundUses.fold(BigDecimal.ZERO) { sum, use -> sum + use.amount }
        return total.divide(newShareAmount(amount, filing), SCALE, RoundingMode.HALF_UP) in CONSISTENT_RANGE
    }

    private fun newShareAmount(amount: BigDecimal, filing: IpoFiling): BigDecimal {
        val shares = filing.offerShares ?: return amount
        val sold = filing.sellers.mapNotNull { it.sold }.sum()
        if (shares <= 0 || sold <= 0 || sold >= shares) return amount
        return amount.multiply(BigDecimal.valueOf(shares - sold)).divide(BigDecimal.valueOf(shares), SCALE, RoundingMode.HALF_UP)
    }

    fun offerAmount(listing: IpoListing): BigDecimal? {
        val filing = listing.filing ?: return null
        if (listing.priceBasis == OfferPriceBasis.PLANNED) return filing.offerAmount
        return atPrice(filing.offerShares, listing.price)
    }

    fun underwriters(listing: IpoListing): List<Underwriter>? {
        val filing = listing.filing ?: return null
        if (listing.priceBasis == OfferPriceBasis.PLANNED) return filing.underwriters
        return filing.underwriters.map { it.copy(amount = atPrice(it.shares, listing.price)) }
    }

    private fun atPrice(shares: Long?, price: BigDecimal?): BigDecimal? =
        if (shares == null || price == null) null else price.multiply(BigDecimal.valueOf(shares))

    private fun percent(part: BigDecimal, whole: BigDecimal): BigDecimal? {
        if (whole.signum() == 0) return null
        return part.multiply(HUNDRED).divide(whole, SCALE, RoundingMode.HALF_UP)
    }
}
