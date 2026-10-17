package com.finngraph.calendar.model

import java.math.BigDecimal
import java.time.LocalDate

enum class OfferPriceBasis { CONFIRMED, PLANNED }

data class IpoSchedule(
    val subscrStart: LocalDate?,
    val subscrEnd: LocalDate?,
    val payDate: LocalDate?,
    val refundDate: LocalDate?,
    val listingDate: LocalDate?,
)

data class IpoListing(
    val name: String,
    val ticker: String?,
    val corpCode: String?,
    val spac: Boolean,
    val status: IpoStatus,
    val schedule: IpoSchedule,
    val price: BigDecimal?,
    val priceBasis: OfferPriceBasis,
    val leadManagers: String?,
    val filing: IpoFiling?,
)

object IpoListings {

    const val FILED_LOOKAHEAD_DAYS = 60L

    private const val LEAD_ROLE = "대표"

    private val SPAC_MARKERS = listOf("기업인수목적", "스팩")

    private val BOARD_ORDER: Comparator<IpoListing> =
        compareByDescending<IpoListing> { it.schedule.subscrStart }.thenBy { it.ticker ?: it.corpCode }

    fun linked(offering: IpoOffering, filing: IpoFiling?, today: LocalDate): IpoListing = IpoListing(
        name = offering.name,
        ticker = offering.ticker,
        corpCode = filing?.corpCode,
        spac = filing?.spac ?: SPAC_MARKERS.any { it in offering.name },
        status = IpoStatus.of(offering, today),
        schedule = IpoSchedule(
            offering.subscrStart,
            offering.subscrEnd,
            offering.payDate,
            offering.refundDate,
            offering.listingDate,
        ),
        price = offering.offerPrice,
        priceBasis = OfferPriceBasis.CONFIRMED,
        leadManagers = offering.leadManagers,
        filing = filing,
    )

    fun filed(filing: IpoFiling): IpoListing = IpoListing(
        name = filing.corpName,
        ticker = filing.ticker,
        corpCode = filing.corpCode,
        spac = filing.spac,
        status = IpoStatus.FILED,
        schedule = IpoSchedule(filing.subscrStart, filing.subscrEnd, filing.payDate, null, null),
        price = filing.offerPrice,
        priceBasis = OfferPriceBasis.PLANNED,
        leadManagers = filing.underwriters.filter { it.role == LEAD_ROLE }.joinToString(", ") { it.name }.ifEmpty { null },
        filing = filing,
    )

    fun board(
        today: LocalDate,
        offerings: List<IpoOffering>,
        filingsByTicker: Map<String, IpoFiling>,
        upcoming: List<IpoFiling>,
    ): List<IpoListing> {
        val offeringTickers = offerings.map { it.ticker }.toSet()
        val window = today..today.plusDays(FILED_LOOKAHEAD_DAYS)
        val merged = offerings.map { linked(it, filingsByTicker[it.ticker], today) }
        val standalone = upcoming
            .filter { filing -> filing.ticker.let { it == null || it !in offeringTickers } }
            .filter { filing -> filing.subscrEnd != null && filing.subscrStart?.let { it in window } == true }
            .map { filed(it) }
        return (merged + standalone).sortedWith(BOARD_ORDER)
    }
}
