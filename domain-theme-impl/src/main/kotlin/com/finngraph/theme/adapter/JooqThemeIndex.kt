package com.finngraph.theme.adapter

import com.finngraph.theme.adapter.jooq.tables.references.THEME_CANDLES_DAILY
import com.finngraph.theme.model.IndexPeriod
import com.finngraph.theme.model.ThemeId
import com.finngraph.theme.model.ThemeIndexCandle
import com.finngraph.theme.model.ThemeIndexCandles
import com.finngraph.theme.model.ThemeIndexClose
import com.finngraph.theme.model.ThemeIndexStats
import com.finngraph.theme.model.ThemeIndexSummary
import com.finngraph.theme.port.ThemeIndexPort
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class JooqThemeIndex(private val dsl: DSLContext) : ThemeIndexPort {

    override fun findCandles(id: ThemeId, period: IndexPeriod, limit: Int): List<ThemeIndexCandle> {
        val baseDate = baseDate() ?: return emptyList()
        return when (period) {
            IndexPeriod.D -> latestDaily(id, baseDate, limit)
            IndexPeriod.W, IndexPeriod.M -> ThemeIndexCandles
                .aggregate(dailyBetween(id, ThemeIndexCandles.windowStart(baseDate, period, limit), baseDate), period)
                .takeLast(limit)
        }
    }

    override fun findSummary(id: ThemeId): ThemeIndexSummary? {
        val baseDate = baseDate() ?: return null
        val closes = dsl.select(THEME_CANDLES_DAILY.TRADE_DATE, THEME_CANDLES_DAILY.CLOSE)
            .from(THEME_CANDLES_DAILY)
            .where(ofTheme(id).and(THEME_CANDLES_DAILY.TRADE_DATE.between(ThemeIndexStats.windowStart(baseDate), baseDate)))
            .orderBy(THEME_CANDLES_DAILY.TRADE_DATE.asc())
            .fetch {
                ThemeIndexClose(
                    date = requireNotNull(it.get(THEME_CANDLES_DAILY.TRADE_DATE)),
                    close = requireNotNull(it.get(THEME_CANDLES_DAILY.CLOSE)),
                )
            }
        return ThemeIndexStats.summarize(baseDate, closes)
    }

    private fun latestDaily(id: ThemeId, baseDate: LocalDate, limit: Int): List<ThemeIndexCandle> =
        dsl.select(CANDLE_FIELDS)
            .from(THEME_CANDLES_DAILY)
            .where(ofTheme(id).and(THEME_CANDLES_DAILY.TRADE_DATE.le(baseDate)))
            .orderBy(THEME_CANDLES_DAILY.TRADE_DATE.desc())
            .limit(limit)
            .fetch { it.toCandle() }
            .reversed()

    private fun dailyBetween(id: ThemeId, from: LocalDate, until: LocalDate): List<ThemeIndexCandle> =
        dsl.select(CANDLE_FIELDS)
            .from(THEME_CANDLES_DAILY)
            .where(ofTheme(id).and(THEME_CANDLES_DAILY.TRADE_DATE.between(from, until)))
            .orderBy(THEME_CANDLES_DAILY.TRADE_DATE.asc())
            .fetch { it.toCandle() }

    private fun baseDate(): LocalDate? = ThemeQuerySupport.priceDate(dsl)

    private fun ofTheme(id: ThemeId): Condition = THEME_CANDLES_DAILY.THEME_ID.eq(id.value)

    private fun Record.toCandle() = ThemeIndexCandle(
        date = requireNotNull(get(THEME_CANDLES_DAILY.TRADE_DATE)),
        open = requireNotNull(get(THEME_CANDLES_DAILY.OPEN)),
        high = requireNotNull(get(THEME_CANDLES_DAILY.HIGH)),
        low = requireNotNull(get(THEME_CANDLES_DAILY.LOW)),
        close = requireNotNull(get(THEME_CANDLES_DAILY.CLOSE)),
        volume = requireNotNull(get(THEME_CANDLES_DAILY.VOLUME)),
        tradeValue = get(THEME_CANDLES_DAILY.TRADE_VALUE),
    )

    private companion object {
        val CANDLE_FIELDS = listOf(
            THEME_CANDLES_DAILY.TRADE_DATE,
            THEME_CANDLES_DAILY.OPEN,
            THEME_CANDLES_DAILY.HIGH,
            THEME_CANDLES_DAILY.LOW,
            THEME_CANDLES_DAILY.CLOSE,
            THEME_CANDLES_DAILY.VOLUME,
            THEME_CANDLES_DAILY.TRADE_VALUE,
        )
    }
}
