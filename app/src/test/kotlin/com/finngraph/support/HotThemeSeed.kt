package com.finngraph.support

import java.sql.DriverManager

object HotThemeSeed {

    fun seed() = execute(
        """
        INSERT INTO stocks (id, name, ticker, market, standard_code, source, is_active) VALUES
            (9101, '시드대장', '900001', 'KOSPI', 'KR7900001004', 'TEST', true),
            (9102, '시드동료', '900002', 'KOSPI', 'KR7900002002', 'TEST', true),
            (9103, '시드완만', '900003', 'KOSPI', 'KR7900003000', 'TEST', true),
            (9104, '시드하락', '900004', 'KOSPI', 'KR7900004008', 'TEST', true),
            (9105, '시드상폐', '900005', 'KOSPI', 'KR7900005005', 'TEST', false);
        INSERT INTO themes (id, name) VALUES
            (9201, '시드급등테마'), (9202, '시드상승테마'), (9203, '시드하락테마');
        INSERT INTO theme_stocks (theme_id, stock_id) VALUES
            (9201, 9101), (9201, 9102), (9201, 9105), (9202, 9103), (9203, 9104);
        INSERT INTO stock_candles_daily (stock_id, trade_date, open, high, low, close, volume, trade_value, source) VALUES
            (9101, '2026-07-30', 100, 100, 100, 100, 1000, 100000, 'TEST'),
            (9101, '2026-07-31', 105, 105, 105, 105, 1000, 105000, 'TEST'),
            (9102, '2026-07-30', 100, 100, 100, 100, 1000, 100000, 'TEST'),
            (9102, '2026-07-31', 105, 105, 105, 105, 1000, 105000, 'TEST'),
            (9103, '2026-07-30', 100, 100, 100, 100, 1000, 100000, 'TEST'),
            (9103, '2026-07-31', 102, 102, 102, 102, 1000, 102000, 'TEST'),
            (9104, '2026-07-30', 100, 100, 100, 100, 1000, 100000, 'TEST'),
            (9104, '2026-07-31', 97, 97, 97, 97, 1000, 97000, 'TEST'),
            (9105, '2026-07-30', 100, 100, 100, 100, 1000, 100000, 'TEST'),
            (9105, '2026-07-31', 105, 105, 105, 105, 1000, 105000, 'TEST');
        INSERT INTO stock_valuations_daily (listing_id, trade_date, market_cap) VALUES
            (9101, '2026-07-31', 2000000),
            (9102, '2026-07-31', 1000000),
            (9103, '2026-07-31', 1000000),
            (9104, '2026-07-31', 1000000),
            (9105, '2026-07-31', 500000);
        """.trimIndent(),
    )

    fun seedNullChange() = execute(
        """
        INSERT INTO stocks (id, name, ticker, market, standard_code, source) VALUES
            (9106, '시드무등락', '900006', 'KOSPI', 'KR7900006003', 'TEST');
        INSERT INTO themes (id, name) VALUES (9204, '시드무등락테마');
        INSERT INTO theme_stocks (theme_id, stock_id) VALUES (9204, 9106);
        """.trimIndent(),
    )

    fun cleanup() = execute(
        """
        DELETE FROM themes WHERE id BETWEEN 9201 AND 9204;
        DELETE FROM stocks WHERE id BETWEEN 9101 AND 9106;
        """.trimIndent(),
    )

    private fun execute(sql: String) {
        val etl = TestContainers.etlPostgres
        DriverManager.getConnection(etl.jdbcUrl, etl.username, etl.password)
            .use { connection -> connection.createStatement().use { it.execute(sql) } }
    }
}
