package com.finngraph.support

import java.sql.DriverManager

object ThemeIndexSeed {

    const val INDEXED_THEME = HotThemeSeed.SURGE_THEME
    const val LAGGING_THEME = HotThemeSeed.FALL_THEME
    const val BARE_THEME = HotThemeSeed.MILD_THEME

    fun seed() = execute(
        """
        INSERT INTO theme_candles_daily (theme_id, trade_date, open, high, low, close, volume, trade_value, source) VALUES
            ($INDEXED_THEME, '2025-07-30', 800, 800, 800, 800, 10, 100, 'TEST'),
            ($INDEXED_THEME, '2025-09-15', 1300, 1300, 1300, 1300, 10, 100, 'TEST'),
            ($INDEXED_THEME, '2025-12-30', 900, 900, 900, 900, 10, 100, 'TEST'),
            ($INDEXED_THEME, '2026-04-30', 950, 950, 950, 950, 10, 100, 'TEST'),
            ($INDEXED_THEME, '2026-06-30', 1000, 1000, 1000, 1000, 50, 500, 'TEST'),
            ($INDEXED_THEME, '2026-07-24', 1090, 1105, 1085, 1100, 100, 1000, 'TEST'),
            ($INDEXED_THEME, '2026-07-28', 1105, 1160, 1100, 1150, 110, 1100, 'TEST'),
            ($INDEXED_THEME, '2026-07-29', 1150, 1155, 1130, 1140, 120, 1200, 'TEST'),
            ($INDEXED_THEME, '2026-07-30', 1145, 1210, 1140, 1200, 130, 1300, 'TEST'),
            ($INDEXED_THEME, '2026-07-31', 1205, 1270, 1195, 1260, 140, 1400, 'TEST'),
            ($INDEXED_THEME, '2026-08-03', 9000, 9999, 8000, 9999, 999, 9999, 'TEST'),
            ($LAGGING_THEME, '2026-07-29', 1000, 1000, 1000, 1000, 10, 100, 'TEST'),
            ($LAGGING_THEME, '2026-07-30', 1010, 1010, 1010, 1010, 10, 100, 'TEST');
        """.trimIndent(),
    )

    fun cleanup() = execute("DELETE FROM theme_candles_daily WHERE theme_id BETWEEN 9201 AND 9299;")

    private fun execute(sql: String) {
        val etl = TestContainers.etlPostgres
        DriverManager.getConnection(etl.jdbcUrl, etl.username, etl.password)
            .use { connection -> connection.createStatement().use { it.execute(sql) } }
    }
}
