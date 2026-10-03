package com.finngraph.stock.adapter

import com.finngraph.stock.adapter.jooq.tables.references.STOCKS
import com.finngraph.stock.adapter.jooq.tables.references.STOCK_DIVIDENDS
import com.finngraph.stock.model.DividendRecord
import com.finngraph.stock.model.Ticker
import com.finngraph.stock.port.StockDividendPort
import org.jooq.DSLContext
import org.springframework.stereotype.Component

@Component
class JooqStockDividend(private val dsl: DSLContext) : StockDividendPort {

    override fun findDividends(ticker: Ticker): List<DividendRecord> =
        dsl.select(STOCK_DIVIDENDS.RECORD_DATE, STOCK_DIVIDENDS.DIVI_KIND, STOCK_DIVIDENDS.DPS, STOCK_DIVIDENDS.PAY_DATE)
            .from(STOCK_DIVIDENDS)
            .join(STOCKS).on(STOCKS.ID.eq(STOCK_DIVIDENDS.LISTING_ID))
            .where(STOCKS.TICKER.eq(ticker.value).and(STOCKS.IS_ACTIVE.eq(true)))
            .orderBy(STOCK_DIVIDENDS.RECORD_DATE.desc())
            .fetch {
                DividendRecord(
                    recordDate = requireNotNull(it.value1()),
                    kind = requireNotNull(it.value2()),
                    dps = it.value3()?.takeIf { dps -> dps.signum() > 0 },
                    payDate = it.value4(),
                )
            }
}
