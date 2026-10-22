package com.finngraph.composition.favorite

import com.finngraph.stock.model.DailyClose
import com.finngraph.stock.model.Ticker
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class ReturnPoint(val date: LocalDate, val value: BigDecimal)

data class MemberReturn(val ticker: Ticker, val name: String, val points: List<ReturnPoint>) {
    val total: BigDecimal? get() = points.lastOrNull()?.value
}

data class ReturnSeries(
    val period: ReturnPeriod,
    val points: List<ReturnPoint>,
    val stockCount: Int,
    val members: List<MemberReturn> = emptyList(),
) {
    val from: LocalDate? get() = points.firstOrNull()?.date
    val total: BigDecimal? get() = points.lastOrNull()?.value
}

enum class ReturnPeriod(val days: Long) {
    W1(7),
    M1(30),
    M3(91),
}

object FolderReturns {

    fun of(
        period: ReturnPeriod,
        closes: Map<Ticker, List<DailyClose>>,
        names: Map<Ticker, String> = emptyMap(),
    ): ReturnSeries {
        val tracks = tracks(closes)
        val dates = dates(tracks)
        val members = tracks
            .map { (ticker, track) ->
                MemberReturn(
                    ticker = ticker,
                    name = names[ticker] ?: ticker.value,
                    points = dates.mapNotNull { date -> move(track, date)?.let { ReturnPoint(date, percent(it)) } },
                )
            }
            .sortedWith(compareByDescending<MemberReturn> { it.total }.thenBy { it.name })
        return ReturnSeries(period, average(tracks, dates), closes.size, members)
    }

    fun series(closes: Map<Ticker, List<DailyClose>>): List<ReturnPoint> {
        val tracks = tracks(closes)
        return average(tracks, dates(tracks))
    }

    private fun average(tracks: Map<Ticker, List<DailyClose>>, dates: Set<LocalDate>): List<ReturnPoint> =
        dates.map { date ->
            val moves = tracks.values.mapNotNull { track -> move(track, date) }
            ReturnPoint(date, percent(moves.average()))
        }

    private fun tracks(closes: Map<Ticker, List<DailyClose>>): Map<Ticker, List<DailyClose>> =
        closes
            .mapValues { (_, rows) -> rows.filter { it.close.signum() > 0 }.sortedBy { it.date } }
            .filterValues { it.isNotEmpty() }

    private fun dates(tracks: Map<Ticker, List<DailyClose>>): Set<LocalDate> =
        tracks.values.flatMap { track -> track.map { it.date } }.toSortedSet()

    private fun move(track: List<DailyClose>, date: LocalDate): Double? {
        val base = track.first()
        if (base.date > date) return null
        val last = track.lastOrNull { it.date <= date } ?: return null
        return last.close.toDouble() / base.close.toDouble() - 1.0
    }

    private fun percent(ratio: Double): BigDecimal =
        BigDecimal.valueOf(ratio * 100).setScale(2, RoundingMode.HALF_UP)
}
