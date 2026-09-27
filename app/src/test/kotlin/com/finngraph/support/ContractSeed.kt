package com.finngraph.support

import java.sql.DriverManager

object ContractSeed {

    const val FILER_TICKER = "940001"
    const val PARTNER_TICKER = "940002"
    const val OTHER_TICKER = "940003"
    const val MISSING_TICKER = "949999"

    const val CHAIN_ROOT = "99990001000001"
    const val CHAIN_MID = "99990001000002"
    const val CHAIN_LATEST = "99990001000003"
    const val STANDALONE = "99990002000001"
    const val REVERSE = "99990003000001"
    const val PARTNER_DEAL = "99990004000001"
    const val STALE = "99990005000001"

    fun seed() {
        cleanup()
        execute(
            """
            INSERT INTO disclosures (
                rcept_no, corp_code, ticker, corp_cls, report_nm, rcept_dt, flr_nm, link,
                contract_type, contract_name, start_date, end_date,
                counterparty, counterparty_corp_name, counterparty_ticker,
                is_correction, original_rcept_no, fields
            ) VALUES
                ('$CHAIN_ROOT', 'C940001', '$FILER_TICKER', 'KOSPI', '단일판매ㆍ공급계약체결', '2026-08-01', '시드공급사',
                 'https://seed.test/dart/$CHAIN_ROOT', '공사수주', '시드 원공시', '2026-08-01', '2027-08-01',
                 '시드파트너 주식회사', '시드파트너', '$PARTNER_TICKER', false, '$CHAIN_ROOT',
                 '{"contract_amount": "1000", "sales_ratio": "10.5"}'),
                ('$CHAIN_MID', 'C940001', '$FILER_TICKER', 'KOSPI', '[기재정정]단일판매ㆍ공급계약체결', '2026-08-05', '시드공급사',
                 'https://seed.test/dart/$CHAIN_MID', '공사수주', '시드 1차 정정', '2026-08-01', '2027-08-01',
                 '시드파트너 주식회사', '시드파트너', '$PARTNER_TICKER', true, '$CHAIN_ROOT',
                 '{"contract_amount": "1200", "sales_ratio": "12.5"}'),
                ('$CHAIN_LATEST', 'C940001', '$FILER_TICKER', 'KOSPI', '[기재정정]단일판매ㆍ공급계약체결', '2026-08-09', '시드공급사',
                 'https://seed.test/dart/$CHAIN_LATEST', '공사수주', '시드 2차 정정', '2026-08-01', '2027-12-31',
                 '시드파트너 주식회사', '시드파트너', '$PARTNER_TICKER', true, '$CHAIN_ROOT',
                 '{"contract_amount": "1500", "sales_ratio": "15.0"}'),
                ('$STANDALONE', 'C940001', '$FILER_TICKER', 'KOSPI', '단일판매ㆍ공급계약체결', '2026-08-10', '시드공급사',
                 'https://seed.test/dart/$STANDALONE', '용역제공', '시드 단독', NULL, NULL,
                 '비상장상대', NULL, NULL, false, NULL,
                 '{"contract_amount": "500"}'),
                ('$REVERSE', 'C940003', '$OTHER_TICKER', 'KOSDAQ', '단일판매ㆍ공급계약체결', '2026-08-08', '시드타사',
                 'https://seed.test/dart/$REVERSE', '공사수주', '시드 역방향', NULL, NULL,
                 '시드공급사 주식회사', '시드공급사', '$FILER_TICKER', false, '$REVERSE',
                 '{"contract_amount": "abc", "sales_ratio": "3.3"}'),
                ('$PARTNER_DEAL', 'C940003', '$OTHER_TICKER', 'KOSDAQ', '단일판매ㆍ공급계약체결', '2026-08-03', '시드타사',
                 'https://seed.test/dart/$PARTNER_DEAL', '기타 판매ㆍ공급계약', '시드 파트너 거래', NULL, NULL,
                 '시드파트너 주식회사', '시드파트너', '$PARTNER_TICKER', false, '$PARTNER_DEAL',
                 '{"contract_amount": "800", "sales_ratio": "99.9"}'),
                ('$STALE', 'C940003', '$OTHER_TICKER', 'KOSDAQ', '단일판매ㆍ공급계약체결', '2026-06-01', '시드타사',
                 'https://seed.test/dart/$STALE', '공사수주', '시드 오래된 공시', NULL, NULL,
                 '익명 상대', NULL, NULL, false, '$STALE',
                 '{"contract_amount": "300", "sales_ratio": "1.1"}');
            """.trimIndent(),
        )
    }

    fun cleanup() = execute("DELETE FROM disclosures WHERE rcept_no LIKE '9999%';")

    private fun execute(sql: String) {
        val etl = TestContainers.etlPostgres
        DriverManager.getConnection(etl.jdbcUrl, etl.username, etl.password)
            .use { connection -> connection.createStatement().use { it.execute(sql) } }
    }
}
