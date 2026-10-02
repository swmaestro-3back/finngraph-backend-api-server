package com.finngraph.stock.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

data class DividendRecord(
    val recordDate: LocalDate,
    val kind: String,
    val dps: BigDecimal?,
    val payDate: LocalDate?,
)

data class DividendReaction(
    val recordDate: LocalDate,
    val kind: String,
    val dps: BigDecimal,
    val exDate: LocalDate,
    val prevClose: BigDecimal,
    val exOpen: BigDecimal,
    val theoreticalDrop: BigDecimal,
    val openGap: BigDecimal,
    val recoveryDays: Int?,
    val pending: Boolean,
)

object DividendReactions {

    const val RECOVERY_WINDOW = 60

    private const val SUSPENSION_DAYS = 7L
    private const val SCALE = 4
    private val HUNDRED = BigDecimal("100")

    fun of(records: List<DividendRecord>, candles: List<Candle>): List<DividendReaction> {
        val daily = candles.sortedBy { it.date }
        return records.sortedByDescending { it.recordDate }.mapNotNull { react(it, daily) }
    }

    private fun react(record: DividendRecord, daily: List<Candle>): DividendReaction? {
        val dps = record.dps?.takeIf { it.signum() > 0 } ?: return null
        if (daily.isEmpty() || record.recordDate > daily.last().date) return null
        val effective = daily.indexOfLast { it.date <= record.recordDate }
        if (effective < 2 || daily[effective].date < record.recordDate.minusDays(SUSPENSION_DAYS)) return null

        val ex = daily[effective - 1]
        val prevClose = daily[effective - 2].close
        if (prevClose.signum() == 0) return null

        val window = daily.subList(effective - 1, minOf(daily.size, effective - 1 + RECOVERY_WINDOW))
        val recovered = window.indexOfFirst { it.close >= prevClose }
        return DividendReaction(
            recordDate = record.recordDate,
            kind = record.kind,
            dps = dps,
            exDate = ex.date,
            prevClose = prevClose,
            exOpen = ex.open,
            theoreticalDrop = percent(dps, prevClose),
            openGap = percent(ex.open - prevClose, prevClose),
            recoveryDays = if (recovered >= 0) recovered + 1 else null,
            pending = recovered < 0 && window.size < RECOVERY_WINDOW,
        )
    }

    private fun percent(numerator: BigDecimal, denominator: BigDecimal): BigDecimal =
        numerator.multiply(HUNDRED).divide(denominator, SCALE, RoundingMode.HALF_UP)
}
