package com.finngraph.stock.adapter

import com.finngraph.stock.adapter.jooq.tables.references.STOCK_CANDLES_DAILY
import com.finngraph.stock.adapter.jooq.tables.references.STOCKS
import com.finngraph.stock.adapter.jooq.tables.references.STOCK_CANDLES_PERIOD
import com.finngraph.stock.model.Candle
import com.finngraph.stock.model.CandlePeriod
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockCandlePort
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Record1
import org.jooq.Select
import org.jooq.Table
import org.jooq.impl.DSL
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class JooqStockCandle(private val dsl: DSLContext) : StockCandlePort {

    override fun findCandles(ticker: Ticker, period: CandlePeriod, limit: Int): List<Candle> {
        val stockId = activeStockId(ticker)
        val priceDate = StockPricing.priceDate(dsl) ?: return emptyList()

        val rows = when (period) {
            CandlePeriod.D -> dailyCandles(stockId, priceDate, limit)

            CandlePeriod.W, CandlePeriod.M -> dsl.select(
                STOCK_CANDLES_PERIOD.BASE_DATE,
                STOCK_CANDLES_PERIOD.OPEN,
                STOCK_CANDLES_PERIOD.HIGH,
                STOCK_CANDLES_PERIOD.LOW,
                STOCK_CANDLES_PERIOD.CLOSE,
                STOCK_CANDLES_PERIOD.VOLUME,
                STOCK_CANDLES_PERIOD.TRADE_VALUE,
            )
                .from(STOCK_CANDLES_PERIOD)
                .where(
                    STOCK_CANDLES_PERIOD.STOCK_ID.eq(stockId)
                        .and(STOCK_CANDLES_PERIOD.PERIOD.eq(period.name))
                        .and(STOCK_CANDLES_PERIOD.BASE_DATE.le(priceDate)),
                )
                .orderBy(STOCK_CANDLES_PERIOD.BASE_DATE.desc())
                .limit(limit)
                .fetch { record ->
                    Candle(
                        date = requireNotNull(record.get(STOCK_CANDLES_PERIOD.BASE_DATE)),
                        open = requireNotNull(record.get(STOCK_CANDLES_PERIOD.OPEN)),
                        high = requireNotNull(record.get(STOCK_CANDLES_PERIOD.HIGH)),
                        low = requireNotNull(record.get(STOCK_CANDLES_PERIOD.LOW)),
                        close = requireNotNull(record.get(STOCK_CANDLES_PERIOD.CLOSE)),
                        volume = requireNotNull(record.get(STOCK_CANDLES_PERIOD.VOLUME)),
                        tradeValue = record.get(STOCK_CANDLES_PERIOD.TRADE_VALUE),
                    )
                }
        }

        return rows.reversed()
    }

    private fun dailyCandles(stockId: Select<out Record1<Long?>>, priceDate: LocalDate, limit: Int): List<Candle> {
        val recent = dsl.select(
            STOCK_CANDLES_DAILY.TRADE_DATE,
            STOCK_CANDLES_DAILY.OPEN,
            STOCK_CANDLES_DAILY.HIGH,
            STOCK_CANDLES_DAILY.LOW,
            STOCK_CANDLES_DAILY.CLOSE,
            STOCK_CANDLES_DAILY.VOLUME,
            STOCK_CANDLES_DAILY.TRADE_VALUE,
            STOCK_CANDLES_DAILY.BASE_PRICE,
        )
            .from(STOCK_CANDLES_DAILY)
            .where(STOCK_CANDLES_DAILY.STOCK_ID.eq(stockId).and(STOCK_CANDLES_DAILY.TRADE_DATE.le(priceDate)))
            .orderBy(STOCK_CANDLES_DAILY.TRADE_DATE.desc())
            .limit(limit + 1)
            .asTable(RECENT)

        val date = recent.column(STOCK_CANDLES_DAILY.TRADE_DATE)
        val open = recent.column(STOCK_CANDLES_DAILY.OPEN)
        val high = recent.column(STOCK_CANDLES_DAILY.HIGH)
        val low = recent.column(STOCK_CANDLES_DAILY.LOW)
        val close = recent.column(STOCK_CANDLES_DAILY.CLOSE)
        val volume = recent.column(STOCK_CANDLES_DAILY.VOLUME)
        val tradeValue = recent.column(STOCK_CANDLES_DAILY.TRADE_VALUE)
        val basePrice = recent.column(STOCK_CANDLES_DAILY.BASE_PRICE)
        val changeRate = StockPricing.dailyChange(close, basePrice, DSL.lag(close).over(DSL.orderBy(date))).`as`(CHANGE_RATE)

        return dsl.select(date, open, high, low, close, volume, tradeValue, changeRate)
            .from(recent)
            .orderBy(date.desc())
            .limit(limit)
            .fetch { record ->
                Candle(
                    date = requireNotNull(record.get(date)),
                    open = requireNotNull(record.get(open)),
                    high = requireNotNull(record.get(high)),
                    low = requireNotNull(record.get(low)),
                    close = requireNotNull(record.get(close)),
                    volume = requireNotNull(record.get(volume)),
                    tradeValue = record.get(tradeValue),
                    changeRate = record.get(changeRate),
                )
            }
    }

    private fun <T> Table<*>.column(source: Field<T>): Field<T> = requireNotNull(field(source))

    private fun activeStockId(ticker: Ticker): Select<out Record1<Long?>> =
        dsl.select(STOCKS.ID)
            .from(STOCKS)
            .where(STOCKS.TICKER.eq(ticker.value).and(STOCKS.IS_ACTIVE.eq(true)))

    private companion object {
        const val RECENT = "recent"
        const val CHANGE_RATE = "change_rate"
    }
}
