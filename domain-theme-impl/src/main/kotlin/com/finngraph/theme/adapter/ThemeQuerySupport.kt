package com.finngraph.theme.adapter

import com.finngraph.theme.adapter.jooq.tables.references.STOCK_CANDLES_DAILY
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
    private const val WINDOW_CANDLES = "wc"
    private const val WINDOWED = "w"
    private const val PX = "px"
    private const val STOCK_ID = "stock_id"
    private const val TRADE_DATE = "trade_date"
    private const val CLOSE = "close"
    private const val PREV_CLOSE = "prev_close"
    private const val PRICE = "price"
    private const val CHANGE = "change"
    private const val TRADE_VALUE = "trade_value"

    val BASE_DATE: Field<LocalDate?> = STOCK_CANDLES_DAILY.`as`(BASE_CANDLES).let { dc ->
        DSL.field(DSL.select(DSL.max(dc.TRADE_DATE)).from(dc))
    }

    val BASE_DATE_COLUMN: Field<LocalDate?> = BASE_DATE.`as`("base_date")

    val PX_STOCK_ID: Field<Long?> = DSL.field(DSL.name(PX, STOCK_ID), SQLDataType.BIGINT)
    val PX_PRICE: Field<BigDecimal?> = DSL.field(DSL.name(PX, PRICE), SQLDataType.NUMERIC)
    val PX_CHANGE: Field<BigDecimal?> = DSL.field(DSL.name(PX, CHANGE), SQLDataType.NUMERIC)
    val PX_TRADE_VALUE: Field<Long?> = DSL.field(DSL.name(PX, TRADE_VALUE), SQLDataType.BIGINT)

    private val W_STOCK_ID: Field<Long?> = DSL.field(DSL.name(WINDOWED, STOCK_ID), SQLDataType.BIGINT)
    private val W_TRADE_DATE: Field<LocalDate?> = DSL.field(DSL.name(WINDOWED, TRADE_DATE), SQLDataType.LOCALDATE)
    private val W_CLOSE: Field<BigDecimal> = DSL.field(DSL.name(WINDOWED, CLOSE), SQLDataType.NUMERIC)
    private val W_PREV_CLOSE: Field<BigDecimal> = DSL.field(DSL.name(WINDOWED, PREV_CLOSE), SQLDataType.NUMERIC)
    private val W_TRADE_VALUE: Field<Long?> = DSL.field(DSL.name(WINDOWED, TRADE_VALUE), SQLDataType.BIGINT)

    fun priceTable(): Table<*> {
        val dc = STOCK_CANDLES_DAILY.`as`(WINDOW_CANDLES)
        val windowed = DSL.select(
            dc.STOCK_ID,
            dc.TRADE_DATE,
            dc.CLOSE,
            dc.TRADE_VALUE,
            DSL.lag(dc.CLOSE).over().partitionBy(dc.STOCK_ID).orderBy(dc.TRADE_DATE).`as`(PREV_CLOSE),
        )
            .from(dc)
            .asTable(WINDOWED)

        return DSL.select(
            W_STOCK_ID,
            W_CLOSE.`as`(PRICE),
            DSL.round(
                W_CLOSE.minus(W_PREV_CLOSE).div(DSL.nullif(W_PREV_CLOSE, BigDecimal.ZERO)).times(HUNDRED),
                DERIVED_SCALE,
            ).`as`(CHANGE),
            W_TRADE_VALUE.`as`(TRADE_VALUE),
        )
            .from(windowed)
            .where(W_TRADE_DATE.eq(BASE_DATE))
            .asTable(PX)
    }
}
