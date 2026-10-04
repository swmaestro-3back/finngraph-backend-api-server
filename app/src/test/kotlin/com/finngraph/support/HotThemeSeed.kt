package com.finngraph.support

import java.sql.DriverManager

object HotThemeSeed {

    const val BASE_DATE = "2026-07-31"
    const val PARTIAL_DATE = "2026-07-30"
    const val PREV_DATE = "2026-07-29"
    const val AVERAGE_DATE = "2026-07-28"
    const val AVERAGE_PRICE = "300"
    const val AVERAGE_ZERO_VOLUME_STOCK = 9107L

    const val SURGE_THEME = 9201L
    const val MILD_THEME = 9202L
    const val FALL_THEME = 9203L
    const val NULL_THEME = 9204L
    const val SKEWED_THEME = 9205L
    const val SPARSE_THEME = 9206L
    const val BOUNDARY_THEME = 9207L

    enum class Kind { PRICED, SUSPENDED, DELISTING, INACTIVE, NO_CANDLE, PARTIAL_PREV, NO_PREV }

    data class Stock(
        val id: Long,
        val name: String,
        val close: String,
        val cap: Long,
        val theme: Long? = null,
        val kind: Kind = Kind.PRICED,
    ) {
        val ticker: String get() = "90$id"
    }

    data class IndexClose(
        val theme: Long,
        val date: String,
        val close: String,
    )

    val INDEX_CLOSES: List<IndexClose> = listOf(
        IndexClose(SURGE_THEME, PREV_DATE, "1000"),
        IndexClose(SURGE_THEME, BASE_DATE, "1043.21"),
        IndexClose(FALL_THEME, PREV_DATE, "1000"),
        IndexClose(FALL_THEME, BASE_DATE, "954.321"),
        IndexClose(BOUNDARY_THEME, PREV_DATE, "1000"),
        IndexClose(BOUNDARY_THEME, BASE_DATE, "1036"),
        IndexClose(SPARSE_THEME, AVERAGE_DATE, "1000"),
        IndexClose(SPARSE_THEME, BASE_DATE, "1050"),
    )

    val STOCKS: List<Stock> = listOf(
        Stock(9101, "시드급등1", "103", 2_000_000, SURGE_THEME),
        Stock(9102, "시드급등2", "104", 1_000_000, SURGE_THEME),
        Stock(9103, "시드급등3", "105", 900_000, SURGE_THEME),
        Stock(9104, "시드급등4", "105", 800_000, SURGE_THEME),
        Stock(9105, "시드급등5", "106", 700_000, SURGE_THEME),
        Stock(9107, "시드급등6", "108", 600_000, SURGE_THEME),
        Stock(9108, "시드정지", "100", 50_000, SURGE_THEME, Kind.SUSPENDED),
        Stock(9109, "시드정리매매", "80", 500_000, SURGE_THEME, Kind.DELISTING),
        Stock(9110, "시드상폐", "105", 400_000, SURGE_THEME, Kind.INACTIVE),
        Stock(9111, "시드하락1", "97", 1_000_000, FALL_THEME),
        Stock(9112, "시드하락2", "96", 900_000, FALL_THEME),
        Stock(9113, "시드하락3", "95", 800_000, FALL_THEME),
        Stock(9114, "시드하락4", "95", 700_000, FALL_THEME),
        Stock(9115, "시드하락5", "93", 600_000, FALL_THEME),
        Stock(9116, "시드완만", "102", 1_000_000, MILD_THEME),
        Stock(9117, "시드폭등", "120", 1_000_000, SKEWED_THEME),
        Stock(9118, "시드쏠림2", "103", 900_000, SKEWED_THEME),
        Stock(9119, "시드쏠림3", "99.8", 800_000, SKEWED_THEME),
        Stock(9120, "시드쏠림4", "99.7", 700_000, SKEWED_THEME),
        Stock(9121, "시드쏠림5", "99.6", 600_000, SKEWED_THEME),
        Stock(9122, "시드결손1", "105", 1_000_000, SPARSE_THEME),
        Stock(9123, "시드결손2", "105", 900_000, SPARSE_THEME),
        Stock(9124, "시드결손3", "105", 800_000, SPARSE_THEME),
        Stock(9125, "시드결손4", "105", 700_000, SPARSE_THEME),
        Stock(9126, "시드결손5", "105", 600_000, SPARSE_THEME),
        Stock(9127, "시드결손무캔들", "105", 500_000, SPARSE_THEME, Kind.NO_CANDLE),
        Stock(9128, "시드결손부분적재", "100", 400_000, SPARSE_THEME, Kind.PARTIAL_PREV),
        Stock(9129, "시드결손무직전", "100", 300_000, SPARSE_THEME, Kind.NO_PREV),
        Stock(9130, "시드경계1", "102", 1_000_000, BOUNDARY_THEME),
        Stock(9131, "시드경계2", "103", 900_000, BOUNDARY_THEME),
        Stock(9132, "시드경계3", "103", 800_000, BOUNDARY_THEME),
        Stock(9133, "시드경계4", "104", 700_000, BOUNDARY_THEME),
        Stock(9134, "시드경계5", "104", 600_000, BOUNDARY_THEME),
        Stock(9135, "시드경계6", "105", 500_000, BOUNDARY_THEME),
        Stock(9136, "시드경계7", "106", 400_000, BOUNDARY_THEME),
        Stock(9137, "시드경계무캔들", "105", 300_000, BOUNDARY_THEME, Kind.NO_CANDLE),
        Stock(9138, "시드경계부분적재1", "100", 200_000, BOUNDARY_THEME, Kind.PARTIAL_PREV),
        Stock(9139, "시드경계부분적재2", "100", 100_000, BOUNDARY_THEME, Kind.PARTIAL_PREV),
        Stock(9140, "시드중립1", "100", 100_000),
        Stock(9141, "시드중립2", "100", 100_000),
        Stock(9142, "시드중립3", "100.5", 100_000),
        Stock(9143, "시드중립4", "99.5", 100_000),
        Stock(9144, "시드중립5", "101", 100_000),
        Stock(9145, "시드중립6", "99", 100_000),
        Stock(9146, "시드중립7", "100.2", 100_000),
        Stock(9147, "시드중립8", "99.8", 100_000),
        Stock(9148, "시드중립9", "100.8", 100_000),
        Stock(9149, "시드중립10", "99.2", 100_000),
        Stock(9150, "시드중립11", "100.3", 100_000),
        Stock(9151, "시드중립12", "99.7", 100_000),
    )

    fun seed() = execute(
        buildString {
            appendLine("INSERT INTO stocks (id, name, ticker, market, standard_code, source, is_active, trading_suspended, delisting_trade) VALUES")
            appendLine(STOCKS.joinToString(",\n", postfix = ";") { it.stockRow() })
            appendLine("INSERT INTO themes (id, name, sources) VALUES")
            appendLine(
                """
                ($SURGE_THEME, '시드급등테마', '{naver,judal}'),
                ($MILD_THEME, '시드완만테마', '{naver}'),
                ($FALL_THEME, '시드하락테마', '{judal}'),
                ($SKEWED_THEME, '시드쏠림테마', '{naver}'),
                ($SPARSE_THEME, '시드결손테마', '{naver}'),
                ($BOUNDARY_THEME, '시드경계테마', '{naver}');
                """.trimIndent(),
            )
            appendLine("INSERT INTO theme_stocks (theme_id, stock_id) VALUES")
            appendLine(STOCKS.filter { it.theme != null }.joinToString(",\n", postfix = ";") { "(${it.theme}, ${it.id})" })
            appendLine("INSERT INTO stock_candles_daily (stock_id, trade_date, open, high, low, close, volume, trade_value, source) VALUES")
            appendLine(STOCKS.flatMap { it.candleRows() }.joinToString(",\n", postfix = ";"))
            appendLine("INSERT INTO stock_valuations_daily (listing_id, trade_date, market_cap) VALUES")
            appendLine(STOCKS.joinToString(",\n", postfix = ";") { "(${it.id}, '$BASE_DATE', ${it.cap})" })
            appendLine("INSERT INTO theme_candles_daily (theme_id, trade_date, open, high, low, close, volume, trade_value, source) VALUES")
            appendLine(INDEX_CLOSES.joinToString(",\n", postfix = ";") { it.row() })
        },
    )

    fun moveIndex(theme: Long, close: String) = execute(
        "UPDATE theme_candles_daily SET open = $close, high = $close, low = $close, close = $close WHERE theme_id = $theme AND trade_date = '$BASE_DATE'",
    )

    fun seedAverageWindow() = execute(
        buildString {
            appendLine("INSERT INTO stock_candles_daily (stock_id, trade_date, open, high, low, close, volume, trade_value, source) VALUES")
            appendLine(
                STOCKS.filter { it.kind != Kind.NO_PREV }.joinToString(",\n", postfix = ";") {
                    it.candle(AVERAGE_DATE, AVERAGE_PRICE, if (it.id == AVERAGE_ZERO_VOLUME_STOCK) 0 else 1000)
                },
            )
        },
    )

    fun seedPeriodReturns(stockId: Long, r1w: String, r1m: String, r3m: String) = execute(
        "UPDATE stock_valuations_daily SET r_1w = $r1w, r_1m = $r1m, r_3m = $r3m WHERE listing_id = $stockId AND trade_date = '$BASE_DATE'",
    )

    fun seedNullChange() = execute(
        """
        INSERT INTO stocks (id, name, ticker, market, standard_code, source) VALUES
            (9106, '시드무등락', '909106', 'KOSPI', 'KR7909106000', 'TEST');
        INSERT INTO themes (id, name) VALUES ($NULL_THEME, '시드무등락테마');
        INSERT INTO theme_stocks (theme_id, stock_id) VALUES ($NULL_THEME, 9106);
        """.trimIndent(),
    )

    fun cleanup() = execute(
        """
        DELETE FROM themes WHERE id BETWEEN 9201 AND 9299;
        DELETE FROM stocks WHERE id BETWEEN 9101 AND 9199;
        """.trimIndent(),
    )

    private fun Stock.stockRow(): String {
        val active = kind != Kind.INACTIVE
        val suspended = kind == Kind.SUSPENDED
        val delisting = kind == Kind.DELISTING
        return "($id, '$name', '$ticker', 'KOSPI', 'KR7${ticker}000', 'TEST', $active, $suspended, $delisting)"
    }

    private fun Stock.candleRows(): List<String> {
        val rows = mutableListOf<String>()
        if (kind != Kind.NO_PREV) rows += candle(PREV_DATE, "100", 1000)
        if (kind == Kind.PARTIAL_PREV) rows += candle(PARTIAL_DATE, "100", 1000)
        if (kind != Kind.NO_CANDLE) rows += candle(BASE_DATE, close, if (kind == Kind.SUSPENDED) 0 else 1000)
        return rows
    }

    private fun IndexClose.row(): String = "($theme, '$date', $close, $close, $close, $close, 0, NULL, 'TEST')"

    private fun Stock.candle(date: String, price: String, volume: Long): String =
        "($id, '$date', $price, $price, $price, $price, $volume, ${(price.toDouble() * 1000).toLong()}, 'TEST')"

    private fun execute(sql: String) {
        val etl = TestContainers.etlPostgres
        DriverManager.getConnection(etl.jdbcUrl, etl.username, etl.password)
            .use { connection -> connection.createStatement().use { it.execute(sql) } }
    }
}
