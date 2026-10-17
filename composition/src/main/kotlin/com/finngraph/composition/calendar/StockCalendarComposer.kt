package com.finngraph.composition.calendar

import com.finngraph.calendar.model.CorporateAction
import com.finngraph.calendar.model.CorporateActions
import com.finngraph.calendar.model.EventFamily
import com.finngraph.calendar.model.EventKind
import com.finngraph.calendar.model.LastBuy
import com.finngraph.calendar.model.TradingDays
import com.finngraph.calendar.port.CalendarEventPort
import com.finngraph.calendar.port.MarketDayPort
import com.finngraph.stock.model.CandlePeriod
import com.finngraph.stock.model.StockDetailView
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockCandlePort
import com.finngraph.stock.port.StockDividendPort
import com.finngraph.stock.port.StockQueryPort
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.OffsetDateTime

data class StockCalendarAction(
    val action: CorporateAction,
    val lastBuy: LastBuy,
    val dividend: DividendMetrics?,
    val rights: RightsMetrics?,
    val bonus: BonusMetrics?,
)

data class StockCalendarView(
    val stock: StockDetailView,
    val from: LocalDate,
    val to: LocalDate,
    val asOf: OffsetDateTime?,
    val actions: List<StockCalendarAction>,
)

@Component
class StockCalendarComposer(
    private val events: CalendarEventPort,
    private val marketDays: MarketDayPort,
    private val stockQuery: StockQueryPort,
    private val stockCandle: StockCandlePort,
    private val stockDividend: StockDividendPort,
) {

    fun forStock(ticker: Ticker, from: LocalDate, to: LocalDate): StockCalendarView? {
        val stock = stockQuery.findByTicker(ticker) ?: return null
        val actions = CorporateActions.group(events.findByTicker(ticker.value, from, to))
        val asOf = events.findLatestUpdatedAt()
        if (actions.isEmpty()) return StockCalendarView(stock, from, to, asOf, emptyList())

        val openByDate = marketDays.findDays(
            actions.minOf { it.basisDate }.minusDays(MARKET_DAY_LOOKBACK_DAYS),
            actions.maxOf { it.basisDate },
        )
        val families = actions.map { it.family }.toSet()
        val daily = if (EventFamily.RIGHTS in families || EventFamily.BONUS in families) {
            stockCandle.findCandles(ticker, CandlePeriod.D, ACTION_CANDLES).sortedBy { it.date }
        } else {
            emptyList()
        }
        val history = if (EventFamily.DIV in families) stockDividend.findDividends(ticker) else emptyList()
        val current = PricePoint(stock.price, stock.baseDate)

        val entries = actions.map { action ->
            StockCalendarAction(
                action = action,
                lastBuy = TradingDays.lastBuy(action.basisDate, openByDate),
                dividend = if (action.family == EventFamily.DIV) ActionMetrics.dividend(action, stock.price, history) else null,
                rights = if (action.family == EventFamily.RIGHTS && action.dateOf(EventKind.RIGHTS_EX) != null) {
                    ActionMetrics.rights(action, current, daily)
                } else {
                    null
                },
                bonus = if (action.family == EventFamily.BONUS) ActionMetrics.bonus(action, current, daily) else null,
            )
        }
        return StockCalendarView(stock, from, to, asOf, entries)
    }

    private companion object {
        const val MARKET_DAY_LOOKBACK_DAYS = 14L
        const val ACTION_CANDLES = 400
    }
}
