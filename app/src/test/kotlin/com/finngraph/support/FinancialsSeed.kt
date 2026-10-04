package com.finngraph.support

import java.sql.DriverManager

object FinancialsSeed {

    const val MIXED_TICKER = "980001"
    const val STALE_TICKER = "980002"

    const val KIS_ROE_2025 = "10.85"
    const val KIS_ROE_2022 = "17.07"
    const val KIS_ROE_2024_STALE = "9.03"
    const val DART_REVENUE_2025 = 3_000_000_000_000L

    fun seed() {
        cleanup()
        execute(
            """
            INSERT INTO companies (id, name, ticker, is_listed, country) VALUES
                (9801, '시드혼합', '$MIXED_TICKER', true, 'KR'),
                (9802, '시드지연', '$STALE_TICKER', true, 'KR');
            INSERT INTO stocks (id, company_id, name, ticker, market, standard_code, source, is_active) VALUES
                (9801, 9801, '시드혼합', '$MIXED_TICKER', 'KOSPI', 'KR7980001009', 'TEST', true),
                (9802, 9802, '시드지연', '$STALE_TICKER', 'KOSDAQ', 'KR7980002007', 'TEST', true);
            INSERT INTO company_financials
                (company_id, source, fiscal_yymm, period_type, fs_div, disclosed_at, rcept_no, revenue, net_income, roe, total_equity) VALUES
                (9801, 'DART', '202512', 'A', 'CFS', '2026-03-10', '20260310000001', $DART_REVENUE_2025, 450000000000, NULL, 4300000000000),
                (9801, 'KIS',  '202512', 'A', 'CFS', NULL, NULL, 2999000000000, 450000000000, $KIS_ROE_2025, 4300000000000),
                (9801, 'KIS',  '202212', 'A', 'CFS', NULL, NULL, 2500000000000, 600000000000, $KIS_ROE_2022, 3500000000000),
                (9802, 'DART', '202512', 'A', 'CFS', '2026-03-12', '20260312000002', 800000000000, 50000000000, NULL, 600000000000),
                (9802, 'KIS',  '202412', 'A', 'CFS', NULL, NULL, 700000000000, 52000000000, $KIS_ROE_2024_STALE, 560000000000);
            """.trimIndent(),
        )
    }

    fun cleanup() = execute(
        """
        DELETE FROM company_financials WHERE company_id BETWEEN 9801 AND 9802;
        DELETE FROM stocks WHERE id BETWEEN 9801 AND 9802;
        DELETE FROM companies WHERE id BETWEEN 9801 AND 9802;
        """.trimIndent(),
    )

    private fun execute(sql: String) {
        val etl = TestContainers.etlPostgres
        DriverManager.getConnection(etl.jdbcUrl, etl.username, etl.password)
            .use { connection -> connection.createStatement().use { it.execute(sql) } }
    }
}
