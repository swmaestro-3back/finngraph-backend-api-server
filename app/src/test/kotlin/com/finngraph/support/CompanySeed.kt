package com.finngraph.support

import java.sql.DriverManager

object CompanySeed {

    const val DESCRIBED_TICKER = "930001"
    const val BARE_TICKER = "930002"
    const val DESCRIPTION =
        "시드전자는 메모리 반도체를 설계·생산해 국내외 완제품 업체에 공급합니다. 파운드리 부문은 외부 고객의 위탁 생산을 맡습니다."
    const val RCEPT_NO = "20260315000123"

    fun seed() {
        cleanup()
        execute(
            """
            INSERT INTO companies (id, name, ticker, is_listed, country, description, description_source, description_rcept_no) VALUES
                (9301, '시드전자', '$DESCRIBED_TICKER', true, 'KR', '$DESCRIPTION', 'DART_LLM', '$RCEPT_NO'),
                (9302, '시드무설명', '$BARE_TICKER', true, 'KR', NULL, NULL, NULL);
            INSERT INTO stocks (id, company_id, name, ticker, market, standard_code, source, is_active) VALUES
                (9301, 9301, '시드전자', '$DESCRIBED_TICKER', 'KOSPI', 'KR7930001001', 'TEST', true),
                (9302, 9302, '시드무설명', '$BARE_TICKER', 'KOSDAQ', 'KR7930002009', 'TEST', true);
            """.trimIndent(),
        )
    }

    fun cleanup() = execute(
        """
        DELETE FROM stocks WHERE id BETWEEN 9301 AND 9302;
        DELETE FROM companies WHERE id BETWEEN 9301 AND 9302;
        """.trimIndent(),
    )

    private fun execute(sql: String) {
        val etl = TestContainers.etlPostgres
        DriverManager.getConnection(etl.jdbcUrl, etl.username, etl.password)
            .use { connection -> connection.createStatement().use { it.execute(sql) } }
    }
}
