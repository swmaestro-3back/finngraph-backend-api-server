package com.finngraph.composition.calendar

import com.finngraph.calendar.model.CompanyProfile
import com.finngraph.calendar.model.FundUseShare
import com.finngraph.calendar.model.IpoListing
import com.finngraph.calendar.model.IpoListings
import com.finngraph.calendar.model.OfferingStructure
import com.finngraph.calendar.model.Underwriter
import com.finngraph.calendar.port.IpoFilingPort
import com.finngraph.calendar.port.IpoOfferingPort
import com.finngraph.stock.model.CandlePeriod
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockCandlePort
import com.finngraph.stock.port.StockQueryPort
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class IpoDetailView(
    val listing: IpoListing,
    val offerAmount: BigDecimal?,
    val underwriters: List<Underwriter>?,
    val fundUses: List<FundUseShare>?,
    val fundUsesWithheld: Boolean,
    val oldShareRatio: BigDecimal?,
    val company: CompanyProfile?,
    val afterListing: AfterListing?,
    val asOf: OffsetDateTime?,
)

@Component
class IpoDetailComposer(
    private val ipos: IpoOfferingPort,
    private val filings: IpoFilingPort,
    private val stockQuery: StockQueryPort,
    private val stockCandle: StockCandlePort,
    private val clock: Clock = Clock.system(KST),
) {

    fun byCorpCode(corpCode: String): IpoDetailView? {
        val filing = filings.findByCorpCode(corpCode) ?: return null
        val offering = filing.ticker?.let { ipos.findLatestByTickers(listOf(it))[it] }
        val listing = offering?.let { IpoListings.linked(it, filing, LocalDate.now(clock)) } ?: IpoListings.filed(filing)
        return detail(listing)
    }

    fun byTicker(ticker: Ticker): IpoDetailView? {
        val offering = ipos.findLatestByTickers(listOf(ticker.value))[ticker.value]
        val filing = filings.findByTickers(listOf(ticker.value))[ticker.value]
        val listing = when {
            offering != null -> IpoListings.linked(offering, filing, LocalDate.now(clock))
            filing != null -> IpoListings.filed(filing)
            else -> return null
        }
        return detail(listing)
    }

    private fun detail(listing: IpoListing): IpoDetailView {
        val filing = listing.filing
        val withheld = filing != null && !OfferingStructure.fundUsesConsistent(filing)
        return IpoDetailView(
            listing = listing,
            offerAmount = OfferingStructure.offerAmount(listing),
            underwriters = OfferingStructure.underwriters(listing),
            fundUses = filing?.let { if (withheld) emptyList() else OfferingStructure.fundUses(it.fundUses) },
            fundUsesWithheld = withheld,
            oldShareRatio = filing?.let { OfferingStructure.oldShareRatio(it.sellers, it.offerShares) },
            company = filing?.let { filings.findCompany(it.corpCode) },
            afterListing = afterListing(listing),
            asOf = listOfNotNull(ipos.findLatestUpdatedAt(), filings.findLatestUpdatedAt()).maxOrNull(),
        )
    }

    private fun afterListing(listing: IpoListing): AfterListing? {
        val ticker = listing.ticker?.takeIf { it.isNotBlank() }?.let { Ticker(it) } ?: return null
        val listingDate = listing.schedule.listingDate ?: return null
        val stock = stockQuery.findByTicker(ticker) ?: return null
        val priceDate = stock.baseDate ?: return null
        if (listingDate > priceDate) return null
        val calendarDays = ChronoUnit.DAYS.between(listingDate, priceDate).toInt() + 1
        val daily = stockCandle.findCandles(ticker, CandlePeriod.D, calendarDays)
        return ListingReturns.of(listing.price, listingDate, daily, PricePoint(stock.price, priceDate))
    }

    private companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
