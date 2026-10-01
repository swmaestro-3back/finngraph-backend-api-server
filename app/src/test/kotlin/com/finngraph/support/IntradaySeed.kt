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
        """.trimIndent(),
    )

    fun clear() = execute("DELETE FROM stock_candles_daily WHERE trade_date = '$TODAY' AND stock_id BETWEEN 9101 AND 9199;")

    private fun execute(sql: String) {
        val etl = TestContainers.etlPostgres
        DriverManager.getConnection(etl.jdbcUrl, etl.username, etl.password)
            .use { connection -> connection.createStatement().use { it.execute(sql) } }
    }
}
