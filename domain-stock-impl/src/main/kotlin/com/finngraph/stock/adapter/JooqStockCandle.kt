package com.finngraph.stock.adapter

import com.finngraph.stock.adapter.jooq.tables.references.STOCK_CANDLES_DAILY
import com.finngraph.stock.adapter.jooq.tables.references.STOCKS
import com.finngraph.stock.adapter.jooq.tables.references.STOCK_CANDLES_PERIOD
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
                STOCK_CANDLES_DAILY.TRADE_DATE,
                STOCK_CANDLES_DAILY.OPEN,
                STOCK_CANDLES_DAILY.HIGH,
                STOCK_CANDLES_DAILY.LOW,
                STOCK_CANDLES_DAILY.CLOSE,
                STOCK_CANDLES_DAILY.VOLUME,
                STOCK_CANDLES_DAILY.TRADE_VALUE,
            )
                .from(STOCK_CANDLES_DAILY)
                .where(STOCK_CANDLES_DAILY.STOCK_ID.eq(stockId))
                .orderBy(STOCK_CANDLES_DAILY.TRADE_DATE.desc())
                .limit(limit)
                .fetch { record ->
                    Candle(
                        date = requireNotNull(record.get(STOCK_CANDLES_DAILY.TRADE_DATE)),
                        open = requireNotNull(record.get(STOCK_CANDLES_DAILY.OPEN)),
                        high = requireNotNull(record.get(STOCK_CANDLES_DAILY.HIGH)),
                        low = requireNotNull(record.get(STOCK_CANDLES_DAILY.LOW)),
                        close = requireNotNull(record.get(STOCK_CANDLES_DAILY.CLOSE)),
                        volume = requireNotNull(record.get(STOCK_CANDLES_DAILY.VOLUME)),
                        tradeValue = record.get(STOCK_CANDLES_DAILY.TRADE_VALUE),
                    )
                }

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
                        .and(STOCK_CANDLES_PERIOD.PERIOD.eq(period.name)),
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

    private fun activeStockId(ticker: Ticker): Select<out Record1<Long?>> =
        dsl.select(STOCKS.ID)
            .from(STOCKS)
            .where(STOCKS.TICKER.eq(ticker.value).and(STOCKS.IS_ACTIVE.eq(true)))
}
