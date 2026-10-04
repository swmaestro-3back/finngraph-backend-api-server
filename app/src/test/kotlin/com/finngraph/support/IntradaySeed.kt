package com.finngraph.support

import java.sql.DriverManager

object IntradaySeed {

    const val TODAY = "2026-08-03"

    fun load(stockIds: List<Long>? = null, risers: List<Long>? = null) = execute(
        """
        INSERT INTO stock_candles_daily (stock_id, trade_date, open, high, low, close, volume, trade_value, source)
        SELECT stock_id, '$TODAY', close * factor, close * factor, close * factor, close * factor, volume, trade_value, 'TEST'
          FROM (
              SELECT stock_id, close, volume, trade_value,
                     ${risers?.let { ids -> "CASE WHEN stock_id IN (${ids.joinToString()}) THEN 1.1 ELSE 1 END" } ?: "1.1"} AS factor
                FROM stock_candles_daily
               WHERE trade_date = '${HotThemeSeed.BASE_DATE}'
                 AND stock_id BETWEEN 9101 AND 9199
                 ${stockIds?.let { ids -> "AND stock_id IN (${ids.joinToString()})" }.orEmpty()}
          ) base;
        INSERT INTO theme_candles_daily (theme_id, trade_date, open, high, low, close, volume, trade_value, source)
        SELECT theme_id, '$TODAY', close * factor, close * factor, close * factor, close * factor, volume, trade_value, 'TEST'
          FROM (
              SELECT tc.theme_id, tc.close, tc.volume, tc.trade_value,
                     ${risers?.let { ids -> "CASE WHEN EXISTS (SELECT 1 FROM theme_stocks ts WHERE ts.theme_id = tc.theme_id AND ts.stock_id IN (${ids.joinToString()})) THEN 1.1 ELSE 1 END" } ?: "1.1"} AS factor
                FROM theme_candles_daily tc
               WHERE tc.trade_date = '${HotThemeSeed.BASE_DATE}'
                 AND tc.theme_id BETWEEN 9201 AND 9299
          ) base;
        """.trimIndent(),
    )

    fun clear() = execute(
        """
        DELETE FROM stock_candles_daily WHERE trade_date = '$TODAY' AND stock_id BETWEEN 9101 AND 9199;
        DELETE FROM theme_candles_daily WHERE trade_date = '$TODAY' AND theme_id BETWEEN 9201 AND 9299;
        """.trimIndent(),
    )

    private fun execute(sql: String) {
        val etl = TestContainers.etlPostgres
        DriverManager.getConnection(etl.jdbcUrl, etl.username, etl.password)
            .use { connection -> connection.createStatement().use { it.execute(sql) } }
    }
}
