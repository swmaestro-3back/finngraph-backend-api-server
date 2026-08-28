package com.finngraph.stock.adapter

import com.finngraph.stock.adapter.jooq.tables.references.DAILY_CANDLES
import com.finngraph.stock.adapter.jooq.tables.references.STOCKS
import com.finngraph.stock.adapter.jooq.tables.references.STOCK_PERIOD_CANDLES
import com.finngraph.stock.model.Candle
import com.finngraph.stock.model.CandlePeriod
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockCandlePort
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Select
import org.springframework.stereotype.Component

@Component
class JooqStockCandle(private val dsl: DSLContext) : StockCandlePort {

    override fun findCandles(ticker: Ticker, period: CandlePeriod, limit: Int): List<Candle> {
        val stockId = activeStockId(ticker)

        val rows = when (period) {
            CandlePeriod.D -> dsl.select(
                DAILY_CANDLES.TRADE_DATE,
                DAILY_CANDLES.OPEN,
                DAILY_CANDLES.HIGH,
                DAILY_CANDLES.LOW,
                DAILY_CANDLES.CLOSE,
                DAILY_CANDLES.VOLUME,
                DAILY_CANDLES.TRADE_VALUE,
            )
                .from(DAILY_CANDLES)
                .where(DAILY_CANDLES.STOCK_ID.eq(stockId))
                .orderBy(DAILY_CANDLES.TRADE_DATE.desc())
                .limit(limit)
                .fetch { record ->
                    Candle(
                        date = requireNotNull(record.get(DAILY_CANDLES.TRADE_DATE)),
                        open = requireNotNull(record.get(DAILY_CANDLES.OPEN)),
                        high = requireNotNull(record.get(DAILY_CANDLES.HIGH)),
                        low = requireNotNull(record.get(DAILY_CANDLES.LOW)),
                        close = requireNotNull(record.get(DAILY_CANDLES.CLOSE)),
                        volume = requireNotNull(record.get(DAILY_CANDLES.VOLUME)),
                        tradeValue = record.get(DAILY_CANDLES.TRADE_VALUE),
                    )
                }

            CandlePeriod.W, CandlePeriod.M -> dsl.select(
                STOCK_PERIOD_CANDLES.BASE_DATE,
                STOCK_PERIOD_CANDLES.OPEN,
                STOCK_PERIOD_CANDLES.HIGH,
                STOCK_PERIOD_CANDLES.LOW,
                STOCK_PERIOD_CANDLES.CLOSE,
                STOCK_PERIOD_CANDLES.VOLUME,
                STOCK_PERIOD_CANDLES.TRADE_VALUE,
            )
                .from(STOCK_PERIOD_CANDLES)
                .where(
                    STOCK_PERIOD_CANDLES.STOCK_ID.eq(stockId)
                        .and(STOCK_PERIOD_CANDLES.PERIOD.eq(period.name)),
                )
                .orderBy(STOCK_PERIOD_CANDLES.BASE_DATE.desc())
                .limit(limit)
                .fetch { record ->
                    Candle(
                        date = requireNotNull(record.get(STOCK_PERIOD_CANDLES.BASE_DATE)),
                        open = requireNotNull(record.get(STOCK_PERIOD_CANDLES.OPEN)),
                        high = requireNotNull(record.get(STOCK_PERIOD_CANDLES.HIGH)),
                        low = requireNotNull(record.get(STOCK_PERIOD_CANDLES.LOW)),
                        close = requireNotNull(record.get(STOCK_PERIOD_CANDLES.CLOSE)),
                        volume = requireNotNull(record.get(STOCK_PERIOD_CANDLES.VOLUME)),
                        tradeValue = record.get(STOCK_PERIOD_CANDLES.TRADE_VALUE),
                    )
                }
        }

        return rows.reversed()
    }

    private fun activeStockId(ticker: Ticker): Select<out Record1<Long?>> =
        dsl.select(STOCKS.ID)
            .from(STOCKS)
            .where(STOCKS.TICKER.eq(ticker.value).and(STOCKS.IS_ACTIVE.eq(true)))
}
