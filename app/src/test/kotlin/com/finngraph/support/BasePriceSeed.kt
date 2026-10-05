package com.finngraph.support

import java.sql.DriverManager

object BasePriceSeed {

    const val THEME_ID = 9901L
    const val BASED_TICKER = "990101"
    const val PLAIN_TICKER = "990102"
    const val RESUMED_TICKER = "990103"
    const val FIRST_TICKER = "990104"

    const val DAY1 = "2028-06-12"
    const val DAY2 = "2028-06-13"
    const val DAY3 = "2028-06-14"

    fun seed() = execute(
        """
        INSERT INTO stocks (id, name, ticker, market, standard_code, source) VALUES
            (9901, '기준가시드', '$BASED_TICKER', 'KOSPI', 'KR7990101000', 'TEST'),
            (9902, '기준가없음시드', '$PLAIN_TICKER', 'KOSDAQ', 'KR7990102008', 'TEST'),
            (9903, '기준가재개시드', '$RESUMED_TICKER', 'KOSPI', 'KR7990103006', 'TEST'),
            (9904, '기준가첫봉시드', '$FIRST_TICKER', 'KOSDAQ', 'KR7990104004', 'TEST');
        INSERT INTO themes (id, name) VALUES ($THEME_ID, '기준가시드테마');
        INSERT INTO theme_stocks (theme_id, stock_id) VALUES ($THEME_ID, 9901), ($THEME_ID, 9902), ($THEME_ID, 9903);
        INSERT INTO stock_candles_daily (stock_id, trade_date, open, high, low, close, volume, trade_value, source, base_price) VALUES
            (9901, '$DAY1', 10000, 10000, 10000, 10000, 1000, 10000000, 'TEST', NULL),
            (9901, '$DAY2', 10500, 10500, 10500, 10500, 1000, 10500000, 'TEST', 10100),
            (9901, '$DAY3', 10600, 10600, 10600, 10600, 1000, 10600000, 'TEST', 10400),
            (9902, '$DAY1', 20000, 20000, 20000, 20000, 1000, 20000000, 'TEST', NULL),
            (9902, '$DAY2', 19000, 19000, 19000, 19000, 1000, 19000000, 'TEST', NULL),
            (9902, '$DAY3', 19380, 19380, 19380, 19380, 1000, 19380000, 'TEST', NULL),
            (9903, '$DAY1', 30000, 30000, 30000, 30000, 1000, 30000000, 'TEST', NULL),
            (9903, '$DAY3', 30300, 30300, 30300, 30300, 1000, 30300000, 'TEST', 30000),
            (9904, '$DAY3', 5000, 5000, 5000, 5000, 1000, 5000000, 'TEST', NULL);
        """.trimIndent(),
    )

    fun cleanup() = execute(
        """
        DELETE FROM themes WHERE id = $THEME_ID;
        DELETE FROM stock_candles_daily WHERE stock_id BETWEEN 9901 AND 9904;
        DELETE FROM stocks WHERE id BETWEEN 9901 AND 9904;
        """.trimIndent(),
    )

    private fun execute(sql: String) {
        val etl = TestContainers.etlPostgres
        DriverManager.getConnection(etl.jdbcUrl, etl.username, etl.password)
            .use { connection -> connection.createStatement().use { it.execute(sql) } }
    }
}
