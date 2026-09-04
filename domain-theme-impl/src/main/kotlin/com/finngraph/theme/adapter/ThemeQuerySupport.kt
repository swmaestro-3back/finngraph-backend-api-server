package com.finngraph.theme.adapter

import com.finngraph.theme.adapter.jooq.tables.references.STOCK_CANDLES_DAILY
import com.finngraph.theme.adapter.jooq.tables.references.STOCKS
import org.jooq.Field
import org.jooq.Table
import org.jooq.impl.DSL
import org.jooq.impl.SQLDataType
import java.math.BigDecimal
import java.time.LocalDate

internal object ThemeQuerySupport {
    const val DERIVED_SCALE = 4

    val HUNDRED: BigDecimal = BigDecimal("100")

    private const val BASE_CANDLES = "base_candles"
    private const val LATEST_CANDLES = "lc"
    private const val PREV_CANDLES = "pc"
    private const val PREV = "prev"
    private const val PX = "px"
    private const val PREV_CLOSE = "prev_close"
    private const val PRICE = "price"
    private const val CHANGE = "change"
    private const val TRADE_VALUE = "trade_value"

    val BASE_DATE: Field<LocalDate?> = STOCK_CANDLES_DAILY.`as`(BASE_CANDLES).let { dc ->
        DSL.field(DSL.select(DSL.max(dc.TRADE_DATE)).from(dc))
    }

    val BASE_DATE_COLUMN: Field<LocalDate?> = BASE_DATE.`as`("base_date")

    val PX_PRICE: Field<BigDecimal?> = DSL.field(DSL.name(PX, PRICE), SQLDataType.NUMERIC)
    val PX_CHANGE: Field<BigDecimal?> = DSL.field(DSL.name(PX, CHANGE), SQLDataType.NUMERIC)
    val PX_TRADE_VALUE: Field<Long?> = DSL.field(DSL.name(PX, TRADE_VALUE), SQLDataType.BIGINT)

    private val PREV_CLOSE_FIELD: Field<BigDecimal?> = DSL.field(DSL.name(PREV, PREV_CLOSE), SQLDataType.NUMERIC)

    fun priceTable(): Table<*> {
        val lc = STOCK_CANDLES_DAILY.`as`(LATEST_CANDLES)
        val pc = STOCK_CANDLES_DAILY.`as`(PREV_CANDLES)
        val prev = DSL.lateral(
            DSL.select(pc.CLOSE.`as`(PREV_CLOSE))
                .from(pc)
                .where(pc.STOCK_ID.eq(lc.STOCK_ID).and(pc.TRADE_DATE.lt(lc.TRADE_DATE)))
                .orderBy(pc.TRADE_DATE.desc())
                .limit(1)
                .asTable(PREV),
        )

        return DSL.lateral(
            DSL.select(
                lc.CLOSE.`as`(PRICE),
                DSL.round(
                    lc.CLOSE.minus(PREV_CLOSE_FIELD).div(DSL.nullif(PREV_CLOSE_FIELD, BigDecimal.ZERO)).times(HUNDRED),
                    DERIVED_SCALE,
                ).`as`(CHANGE),
                lc.TRADE_VALUE.`as`(TRADE_VALUE),
            )
                .from(lc)
                .leftJoin(prev).on(DSL.trueCondition())
                .where(lc.STOCK_ID.eq(STOCKS.ID).and(lc.TRADE_DATE.eq(BASE_DATE)))
                .asTable(PX),
        )
    }
}
