package com.finngraph.support

import java.sql.DriverManager
import java.time.LocalDate

object IpoSeed {

    const val LINKED_TICKER = "962101"
    const val BEYOND_TICKER = "962102"
    const val KSD_ONLY_TICKER = "962103"
    const val LISTED_TICKER = "962104"

    const val LINKED_CORP = "96210001"
    const val FILED_CORP = "96210002"
    const val SPAC_CORP = "96210003"
    const val WITHDRAWN_CORP = "96210004"
    const val TOO_FAR_CORP = "96210005"
    const val EDGE_CORP = "96210006"
    const val STARTED_CORP = "96210007"
    const val BEYOND_CORP = "96210008"
    const val LISTED_CORP = "96210009"
    const val MISMATCH_CORP = "96210010"

    fun owns(ticker: String?, corpCode: String?): Boolean =
        ticker?.startsWith("9621") == true || corpCode?.startsWith("962100") == true

    fun seed(today: LocalDate) {
        fun d(offset: Long) = "'${today.plusDays(offset)}'"
        execute(
            """
            INSERT INTO ipo_offerings
                (ticker, name, subscr_start, subscr_end, offer_price, pay_date, refund_date, listing_date, lead_managers, basis_date) VALUES
                ('$LINKED_TICKER', '공모연결', ${d(3)}, ${d(4)}, 15000, ${d(6)}, ${d(6)}, NULL, '연결주관', ${d(3)}),
                ('$BEYOND_TICKER', '공모먼예정', ${d(45)}, ${d(46)}, NULL, NULL, NULL, NULL, '먼주관', ${d(45)}),
                ('$KSD_ONLY_TICKER', '공모예탁원만', ${d(-12)}, ${d(-11)}, 10000, ${d(-9)}, ${d(-9)}, ${d(-5)}, '예탁원주관', ${d(-12)}),
                ('$LISTED_TICKER', '상장후성과', '2029-03-05', '2029-03-06', 10000, '2029-03-08', '2029-03-08', '2029-03-14', '성과주관', '2029-03-05');
            INSERT INTO ipo_filings
                (corp_code, corp_name, corp_cls, status, spac, first_rcept_no, first_filed_on, latest_rcept_no, latest_report_nm,
                 subscr_start, subscr_end, pay_date, offer_price, offer_shares, offer_amount, offer_method,
                 underwriters, fund_uses, sellers, putback, description_ready, ticker) VALUES
                ('$LINKED_CORP', '공모연결', 'E', 'PRICED', false, '20260901000001', ${d(-30)}, '20260925000001', '[발행조건확정]증권신고서(지분증권)',
                 ${d(3)}, ${d(4)}, ${d(6)}, 14000, 1000000, 14000000000, '일반공모',
                 '[{"name": "연결증권", "role": "대표", "shares": 1000000, "amount": 14000000000, "method": "총액인수"}]',
                 '[]', '[]', NULL, false, '$LINKED_TICKER'),
                ('$FILED_CORP', '신고서기업', 'E', 'FILED', false, '20260915000123', ${d(-17)}, '20261001000579', '[기재정정]증권신고서(지분증권)',
                 ${d(20)}, ${d(21)}, ${d(23)}, 23000, 2030000, 46690000000, '일반공모',
                 '[{"name": "삼성증권", "role": "대표", "shares": 1500000, "amount": 34500000000, "method": "총액인수"}, {"name": "테스트증권", "role": "인수", "shares": 530000, "amount": 12190000000, "method": "총액인수"}]',
                 '[{"purpose": "시설자금", "amount": 31320000000}, {"purpose": "운영자금", "amount": 35540000}]',
                 '[{"holder": "최대주주", "relation": "최대주주", "before": 1000000, "sold": 300000, "after": 700000}, {"holder": "벤처캐피탈", "relation": "기타", "before": 500000, "sold": 106000, "after": 394000}]',
                 '{"reason": "공모가 하락", "investors": "일반청약자", "shares": 2030000, "period": "상장일부터 1개월", "price": "공모가격의 90%"}',
                 true, NULL),
                ('$SPAC_CORP', '테스트기업인수목적1호', 'E', 'PRICED', true, '20260910000003', ${d(-20)}, '20260930000003', '[발행조건확정]증권신고서(지분증권)',
                 ${d(10)}, ${d(11)}, ${d(13)}, 2000, 5000000, 10000000000, '일반공모', '[]', '[]', '[]', NULL, false, NULL),
                ('$WITHDRAWN_CORP', '철회기업', 'E', 'WITHDRAWN', false, '20260905000004', ${d(-25)}, '20260928000004', '철회신고서',
                 ${d(15)}, ${d(16)}, ${d(18)}, 9000, 1000000, 9000000000, '일반공모', '[]', '[]', '[]', NULL, false, NULL),
                ('$TOO_FAR_CORP', '먼신고서', 'E', 'FILED', false, '20260920000005', ${d(-10)}, '20260920000005', '증권신고서(지분증권)',
                 ${d(61)}, ${d(62)}, ${d(64)}, 5000, 1000000, 5000000000, '일반공모', '[]', '[]', '[]', NULL, false, NULL),
                ('$EDGE_CORP', '경계신고서', 'N', 'FILED', false, '20260920000006', ${d(-10)}, '20260920000006', '증권신고서(지분증권)',
                 ${d(60)}, ${d(61)}, ${d(63)}, 5000, 1000000, 5000000000, '일반공모', '[]', '[]', '[]', NULL, false, NULL),
                ('$STARTED_CORP', '청약시작신고서', 'E', 'FILED', false, '20260901000007', ${d(-30)}, '20260901000007', '증권신고서(지분증권)',
                 ${d(-1)}, ${d(0)}, ${d(2)}, 5000, 1000000, 5000000000, '일반공모', '[]', '[]', '[]', NULL, false, NULL),
                ('$BEYOND_CORP', '공모먼예정', 'E', 'FILED', false, '20260925000008', ${d(-5)}, '20260925000008', '증권신고서(지분증권)',
                 ${d(45)}, ${d(46)}, ${d(48)}, 7000, 1000000, 7000000000, '일반공모', '[]', '[]', '[]', NULL, false, '$BEYOND_TICKER'),
                ('$LISTED_CORP', '상장후성과', 'E', 'PRICED', false, '20290201000009', '2029-02-01', '20290226000009', '[발행조건확정]증권신고서(지분증권)',
                 '2029-03-05', '2029-03-06', '2029-03-08', 10000, 1000000, 10000000000, '일반공모', '[]', '[]', '[]', NULL, false, '$LISTED_TICKER'),
                ('$MISMATCH_CORP', '자금불일치', 'E', 'FILED', false, '20260920000010', ${d(-10)}, '20260920000010', '증권신고서(지분증권)',
                 ${d(90)}, ${d(91)}, ${d(93)}, 12300, 1000000, 12300000000, '일반공모', '[]',
                 '[{"purpose": "운영자금", "amount": 7498415000}, {"purpose": "시설투자", "amount": 4500000000}, {"purpose": "발행제비용", "amount": 11998415000}]',
                 '[]', NULL, false, NULL);
            INSERT INTO companies
                (id, name, corp_code, is_listed, country, ceo_name, established_on, address, homepage,
                 description, description_source, description_rcept_no) VALUES
                (9621, '신고서기업', '$FILED_CORP', false, 'KR', '김대표', '2002-08-07', '서울특별시 테스트구 1', '',
                 '신고서기업은 자동차 부품을 만든다.', 'DART_LLM', '20261001000579'),
                (9622, '상장후성과', '$LISTED_CORP', true, 'KR', NULL, NULL, NULL, NULL, NULL, NULL, NULL);
            INSERT INTO stocks (id, company_id, name, ticker, market, standard_code, source, is_active) VALUES
                (9621, 9622, '상장후성과', '$LISTED_TICKER', 'KOSDAQ', 'KR7962104001', 'TEST', true);
            INSERT INTO stock_candles_daily (stock_id, trade_date, open, high, low, close, volume, trade_value, source)
            SELECT 9621, d::date, 15000, 15000, 15000, 15000, 1000, 15000000, 'TEST'
              FROM generate_series('2029-03-14'::date, '2029-03-30'::date, interval '1 day') AS d
             WHERE extract(isodow FROM d) < 6;
            UPDATE stock_candles_daily SET open = 20000, high = 21000, low = 17500, close = 18000
             WHERE stock_id = 9621 AND trade_date = '2029-03-14';
            UPDATE stock_candles_daily SET low = 12500, close = 12500
             WHERE stock_id = 9621 AND trade_date = '2029-03-30';
            """.trimIndent(),
        )
    }

    fun cleanup() = execute(
        """
        DELETE FROM stock_candles_daily WHERE stock_id = 9621;
        DELETE FROM stocks WHERE id = 9621;
        DELETE FROM companies WHERE id BETWEEN 9621 AND 9622;
        DELETE FROM ipo_filings WHERE corp_code BETWEEN '96210001' AND '96210099';
        DELETE FROM ipo_offerings WHERE ticker BETWEEN '962101' AND '962199';
        """.trimIndent(),
    )

    private fun execute(sql: String) {
        val etl = TestContainers.etlPostgres
        DriverManager.getConnection(etl.jdbcUrl, etl.username, etl.password)
            .use { connection -> connection.createStatement().use { it.execute(sql) } }
    }
}
