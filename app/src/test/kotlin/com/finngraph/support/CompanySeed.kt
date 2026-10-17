package com.finngraph.support

import java.sql.DriverManager

object CompanySeed {

    const val DESCRIBED_TICKER = "930001"
    const val BARE_TICKER = "930002"
    const val DESCRIPTION =
        "시드전자는 메모리 반도체를 설계·생산해 국내외 완제품 업체에 공급합니다. 파운드리 부문은 외부 고객의 위탁 생산을 맡습니다."
    const val RCEPT_NO = "20260315000123"
    const val CEO_NAME = "김시드, 이시드"
    const val ESTABLISHED_ON = "1969-01-13"
    const val LISTED_ON = "1975-06-11"
    const val FISCAL_MONTH = "12"
    const val LISTED_SHARES = 5_846_278_000L
    const val PAR_VALUE = "100"
    const val HOMEPAGE = "www.seed.example/kr"
    const val ADDRESS = "경기도 수원시 영통구  시드로 129"

    fun seed() {
        cleanup()
        execute(
            """
            INSERT INTO companies (id, name, ticker, is_listed, country, description, description_source, description_rcept_no,
                                   ceo_name, established_on, fiscal_month, homepage, address) VALUES
                (9301, '시드전자', '$DESCRIBED_TICKER', true, 'KR', '$DESCRIPTION', 'DART_LLM', '$RCEPT_NO',
                 '$CEO_NAME', '$ESTABLISHED_ON', '$FISCAL_MONTH', '$HOMEPAGE', '$ADDRESS'),
                (9302, '시드무설명', '$BARE_TICKER', true, 'KR', NULL, NULL, NULL,
                 NULL, NULL, NULL, NULL, NULL);
            INSERT INTO stocks (id, company_id, name, ticker, market, standard_code, source, is_active,
                                listed_date, listed_shares, par_value) VALUES
                (9301, 9301, '시드전자', '$DESCRIBED_TICKER', 'KOSPI', 'KR7930001001', 'TEST', true,
                 '$LISTED_ON', $LISTED_SHARES, $PAR_VALUE),
                (9302, 9302, '시드무설명', '$BARE_TICKER', 'KOSDAQ', 'KR7930002009', 'TEST', true,
                 NULL, NULL, NULL);
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
