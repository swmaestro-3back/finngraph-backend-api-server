package com.finngraph.composition.calendar

import com.finngraph.calendar.model.CalendarEvent
import com.finngraph.calendar.model.IpoListing
import com.finngraph.calendar.model.IpoListings
import com.finngraph.calendar.port.CalendarEventPort
import com.finngraph.calendar.port.IpoFilingPort
import com.finngraph.calendar.port.IpoOfferingPort
import com.finngraph.calendar.port.MarketDayPort
import com.finngraph.favorite.port.FavoritePort
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockQueryPort
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

data class CalendarEntry(val event: CalendarEvent, val favorite: Boolean)

data class CalendarView(
    val from: LocalDate,
    val to: LocalDate,
    val asOf: OffsetDateTime?,
    val closedDates: List<LocalDate>,
    val entries: List<CalendarEntry>,
)

data class IpoBoardView(val asOf: OffsetDateTime?, val listings: List<IpoListing>)

@Component
class CalendarComposer(
    private val events: CalendarEventPort,
    private val marketDays: MarketDayPort,
    private val ipos: IpoOfferingPort,
    private val filings: IpoFilingPort,
    private val stockQuery: StockQueryPort,
    private val favorites: FavoritePort,
    private val clock: Clock = Clock.system(KST),
) {

    fun publicCalendar(from: LocalDate, to: LocalDate): CalendarView = view(from, to, emptySet())

    fun memberCalendar(userId: Long, from: LocalDate, to: LocalDate): CalendarView =
        view(from, to, favorites.findTickersByUser(userId).toSet())

    fun ipoBoard(): IpoBoardView {
        val today = LocalDate.now(clock)
        val window = ipos.findOverlapping(today.minusDays(IPO_LOOKBACK_DAYS), today.plusDays(IPO_LOOKAHEAD_DAYS))
        val upcoming = filings.findBySubscrStartBetween(today, today.plusDays(IpoListings.FILED_LOOKAHEAD_DAYS))
        val windowTickers = window.map { it.ticker }.toSet()
        val linkedBeyond = ipos.findLatestByTickers(upcoming.mapNotNull { it.ticker }.filterNot { it in windowTickers })
        val offerings = window + linkedBeyond.values
        val listings = IpoListings.board(today, offerings, filings.findByTickers(offerings.map { it.ticker }), upcoming)
        val asOf = listOfNotNull(ipos.findLatestUpdatedAt(), filings.findLatestUpdatedAt()).maxOrNull()
        return IpoBoardView(asOf, listings)
    }

    private fun view(from: LocalDate, to: LocalDate, favoriteTickers: Set<String>): CalendarView {
        val tickers = stockQuery.findKrx300Tickers().map { it.value }.toSet() + favoriteTickers
        val found = events.findByTickers(tickers, from, to)
        val names = stockQuery.findNamesByTickers(found.map { Ticker(it.ticker) }.distinct())
        val entries = found
            .map { event ->
                val name = names[Ticker(event.ticker)] ?: event.stockName
                CalendarEntry(event.copy(stockName = name), event.ticker in favoriteTickers)
            }
            .sortedWith(ENTRY_ORDER)
        return CalendarView(from, to, events.findLatestUpdatedAt(), marketDays.findClosedDates(from, to), entries)
    }

    private companion object {
        val KST: ZoneId = ZoneId.of("Asia/Seoul")
        const val IPO_LOOKBACK_DAYS = 14L
        const val IPO_LOOKAHEAD_DAYS = 30L
        val ENTRY_ORDER: Comparator<CalendarEntry> = compareBy(
            { it.event.date },
            { !it.favorite },
            { it.event.kind.ordinal },
            { it.event.stockName },
        )
    }
}
