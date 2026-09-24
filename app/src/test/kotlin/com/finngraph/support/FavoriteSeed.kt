package com.finngraph.support

import java.sql.DriverManager

object FavoriteSeed {

    const val ACTIVE_TICKER = "930001"
    const val ACTIVE_TICKER_2 = "930002"
    const val DELISTED_TICKER = "930003"
    const val MISSING_TICKER = "939999"
    const val THEME_ID = 9301L
    const val THEME_ID_2 = 9302L
    const val MISSING_THEME_ID = 9399L
    const val THEME_ID_LAST = 9360L

    fun seed() = execute(
        """
        INSERT INTO stocks (id, name, ticker, market, standard_code, source, is_active) VALUES
            (9301, '관심시드1', '$ACTIVE_TICKER', 'KOSPI', 'KR7930001007', 'TEST', true),
            (9302, '관심시드2', '$ACTIVE_TICKER_2', 'KOSDAQ', 'KR7930002005', 'TEST', true),
            (9303, '관심상폐', '$DELISTED_TICKER', 'KOSPI', 'KR7930003003', 'TEST', true);
        INSERT INTO themes (id, name)
            SELECT g, CASE WHEN g = 9301 THEN '관심시드테마' ELSE '관심시드테마' || g END FROM generate_series(9301, 9360) AS g;
        INSERT INTO theme_stocks (theme_id, stock_id) VALUES (9301, 9301), (9302, 9302);
        INSERT INTO stock_candles_daily (stock_id, trade_date, open, high, low, close, volume, trade_value, source) VALUES
            (9301, '2026-07-30', 100, 100, 100, 100, 1000, 100000, 'TEST'),
            (9301, '2026-07-31', 110, 110, 110, 110, 1000, 110000, 'TEST'),
            (9302, '2026-07-30', 100, 100, 100, 100, 1000, 100000, 'TEST'),
            (9302, '2026-07-31', 95, 95, 95, 95, 1000, 95000, 'TEST');
        INSERT INTO stock_valuations_daily (listing_id, trade_date, market_cap) VALUES
            (9301, '2026-07-31', 3000000), (9302, '2026-07-31', 1500000);
        INSERT INTO companies (id, name, ticker, is_listed, country) VALUES
            (9301, '관심시드1', '$ACTIVE_TICKER', true, 'KR'),
            (9302, '관심시드2', '$ACTIVE_TICKER_2', true, 'KR');
        INSERT INTO news (id, title, link, published_at, triple_extracted) VALUES
            (9301, '관심뉴스 추출완료', 'https://seed.test/news/9301', '2026-07-31T01:00:00Z', true),
            (9302, '관심뉴스 삼중항없음', 'https://seed.test/news/9302', '2026-07-31T02:00:00Z', false),
            (9303, '관심뉴스 미처리', 'https://seed.test/news/9303', '2026-07-31T03:00:00Z', NULL),
            (9304, '관심뉴스2 추출완료', 'https://seed.test/news/9304', '2026-07-31T04:00:00Z', true);
        INSERT INTO news_companies (news_id, company_id) VALUES
            (9301, 9301), (9302, 9301), (9303, 9301), (9304, 9302);
        """.trimIndent(),
    )

    fun delistStock() = execute("UPDATE stocks SET is_active = false WHERE id = 9303;")

    fun cleanup() = execute(
        """
        DELETE FROM news WHERE id BETWEEN 9301 AND 9304;
        DELETE FROM companies WHERE id BETWEEN 9301 AND 9302;
        DELETE FROM themes WHERE id BETWEEN 9301 AND 9360;
        DELETE FROM stocks WHERE id BETWEEN 9301 AND 9303;
        """.trimIndent(),
    )

    private fun execute(sql: String) {
        val etl = TestContainers.etlPostgres
        DriverManager.getConnection(etl.jdbcUrl, etl.username, etl.password)
            .use { connection -> connection.createStatement().use { it.execute(sql) } }
    }
}
