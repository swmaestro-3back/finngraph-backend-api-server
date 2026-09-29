package com.finngraph.support

import java.sql.DriverManager

object BriefingSeed {

    const val BASE_DATE = "2026-08-20"
    const val PREV_DATE = "2026-08-19"

    const val SUPPLIER_ID = 9301L
    const val PARTNER_ID = 9302L
    const val ADMIN_ID = 9303L
    const val SUSPENDED_ID = 9304L
    const val DELISTING_ID = 9305L
    const val OTHER_ID = 9306L

    const val SUPPLIER_TICKER = "939301"
    const val PARTNER_TICKER = "939302"
    const val ADMIN_TICKER = "939303"
    const val SUSPENDED_TICKER = "939304"
    const val DELISTING_TICKER = "939305"
    const val OTHER_TICKER = "939306"

    const val HOT_CLUSTER = 501L
    const val SMALL_CLUSTER = 502L
    const val STALE_CLUSTER = 503L

    const val NEWS_A = 9001L
    const val NEWS_B = 9002L
    const val NEWS_C = 9003L
    const val NEWS_D = 9004L
    const val NEWS_STALE = 9005L

    const val REL_SUPPLY = 7701L
    const val REL_PLANNED = 7702L
    const val REL_TERMINATED = 7703L
    const val REL_STALE = 7704L

    const val CORRECTION_RCEPT = "99980001000001"
    const val STALE_RCEPT = "99980002000001"

    fun seed() {
        cleanup()
        execute(
            """
            INSERT INTO companies (id, name, ticker, corp_code, is_listed, country) VALUES
                ($SUPPLIER_ID, '시드공급사B', '$SUPPLIER_TICKER', 'C939301', true, 'KR'),
                ($PARTNER_ID, '시드파트너B', '$PARTNER_TICKER', 'C939302', true, 'KR'),
                ($OTHER_ID, '시드무관', '$OTHER_TICKER', 'C939306', true, 'KR');

            INSERT INTO stocks (id, name, ticker, market, standard_code, source, company_id, is_active, trading_suspended, under_administration, delisting_trade) VALUES
                ($SUPPLIER_ID, '시드공급사B', '$SUPPLIER_TICKER', 'KOSPI', 'KR7939301000', 'TEST', $SUPPLIER_ID, true, false, false, false),
                ($PARTNER_ID, '시드파트너B', '$PARTNER_TICKER', 'KOSPI', 'KR7939302000', 'TEST', $PARTNER_ID, true, false, false, false),
                ($ADMIN_ID, '시드관리', '$ADMIN_TICKER', 'KOSDAQ', 'KR7939303000', 'TEST', NULL, true, false, true, false),
                ($SUSPENDED_ID, '시드정지B', '$SUSPENDED_TICKER', 'KOSDAQ', 'KR7939304000', 'TEST', NULL, true, true, false, false),
                ($DELISTING_ID, '시드정리B', '$DELISTING_TICKER', 'KOSDAQ', 'KR7939305000', 'TEST', NULL, true, false, false, true),
                ($OTHER_ID, '시드무관', '$OTHER_TICKER', 'KOSPI', 'KR7939306000', 'TEST', $OTHER_ID, true, false, false, false);

            INSERT INTO stock_candles_daily (stock_id, trade_date, open, high, low, close, volume, trade_value, source) VALUES
                ($SUPPLIER_ID, '$PREV_DATE', 100, 100, 100, 100, 1000, 100000, 'TEST'),
                ($SUPPLIER_ID, '$BASE_DATE', 103, 103, 103, 103, 1000, 103000, 'TEST'),
                ($PARTNER_ID, '$PREV_DATE', 100, 100, 100, 100, 1000, 100000, 'TEST'),
                ($PARTNER_ID, '$BASE_DATE', 98, 98, 98, 98, 1000, 98000, 'TEST'),
                ($ADMIN_ID, '$PREV_DATE', 100, 100, 100, 100, 1000, 100000, 'TEST'),
                ($ADMIN_ID, '$BASE_DATE', 100, 100, 100, 100, 1000, 100000, 'TEST'),
                ($SUSPENDED_ID, '$PREV_DATE', 100, 100, 100, 100, 1000, 100000, 'TEST'),
                ($SUSPENDED_ID, '$BASE_DATE', 100, 100, 100, 100, 0, 0, 'TEST'),
                ($DELISTING_ID, '$PREV_DATE', 100, 100, 100, 100, 1000, 100000, 'TEST'),
                ($DELISTING_ID, '$BASE_DATE', 80, 80, 80, 80, 1000, 80000, 'TEST'),
                ($OTHER_ID, '$PREV_DATE', 100, 100, 100, 100, 1000, 100000, 'TEST'),
                ($OTHER_ID, '$BASE_DATE', 105, 105, 105, 105, 1000, 105000, 'TEST');

            INSERT INTO stock_valuations_daily (listing_id, trade_date, market_cap) VALUES
                ($SUPPLIER_ID, '$BASE_DATE', 1000000),
                ($PARTNER_ID, '$BASE_DATE', 900000),
                ($ADMIN_ID, '$BASE_DATE', 100000),
                ($SUSPENDED_ID, '$BASE_DATE', 100000),
                ($DELISTING_ID, '$BASE_DATE', 100000),
                ($OTHER_ID, '$BASE_DATE', 500000);

            INSERT INTO news_clusters (id, title, keywords, member_count, original_size, first_published_at, last_published_at) VALUES
                ($HOT_CLUSTER, '시드 이슈 클러스터', '{배터리,공급}', 3, 5, '$BASE_DATE 09:00:00+09', '$BASE_DATE 15:00:00+09'),
                ($SMALL_CLUSTER, '시드 소형 클러스터', '{투자}', 2, 2, '$BASE_DATE 10:00:00+09', '$BASE_DATE 10:00:00+09'),
                ($STALE_CLUSTER, '시드 지난 클러스터', '{지난}', 4, 4, '$PREV_DATE 09:00:00+09', '$PREV_DATE 12:00:00+09');

            INSERT INTO news (id, title, text, summary, link, originallink, published_at, collected_at, triple_extracted, cluster_id) VALUES
                ($NEWS_A, '시드공급사B, 시드파트너B에 배터리 공급', '시드공급사B는 시드파트너B에 배터리를 공급한다고 밝혔다. 본문 이어짐.', '시드공급사B가 시드파트너B에 배터리를 공급한다.', 'https://seed.test/news/$NEWS_A', 'https://press.test/a', '$BASE_DATE 09:00:00+09', '$BASE_DATE 09:05:00+09', true, $HOT_CLUSTER),
                ($NEWS_B, '배터리 공급계약 후속 보도', '후속 보도 본문.', '후속 보도 요약.', 'https://seed.test/news/$NEWS_B', NULL, '$BASE_DATE 11:00:00+09', '$BASE_DATE 11:05:00+09', true, $HOT_CLUSTER),
                ($NEWS_C, '배터리 공급 관련 세 번째 기사', '세 번째 기사 본문.', NULL, 'https://seed.test/news/$NEWS_C', NULL, '$BASE_DATE 15:00:00+09', '$BASE_DATE 15:05:00+09', NULL, $HOT_CLUSTER),
                ($NEWS_D, '시드무관, 시드비상장 투자 검토', '시드무관이 시드비상장 투자를 검토한다.', '시드무관이 투자를 검토한다.', 'https://seed.test/news/$NEWS_D', NULL, '$BASE_DATE 10:00:00+09', '$BASE_DATE 10:05:00+09', true, $SMALL_CLUSTER),
                ($NEWS_STALE, '지난 기사', '지난 기사 본문.', '지난 기사 요약.', 'https://seed.test/news/$NEWS_STALE', NULL, '$PREV_DATE 10:00:00+09', '$PREV_DATE 10:05:00+09', true, $STALE_CLUSTER);

            INSERT INTO news_companies (news_id, company_id) VALUES
                ($NEWS_A, $SUPPLIER_ID),
                ($NEWS_B, $SUPPLIER_ID),
                ($NEWS_B, $PARTNER_ID),
                ($NEWS_D, $OTHER_ID),
                ($NEWS_STALE, $OTHER_ID);

            INSERT INTO disclosures (
                rcept_no, corp_code, ticker, corp_cls, report_nm, rcept_dt, flr_nm, link,
                contract_type, contract_name, start_date, end_date,
                counterparty, counterparty_corp_name, counterparty_ticker,
                is_correction, original_rcept_no, correction_reason, fields
            ) VALUES
                ('$CORRECTION_RCEPT', 'C939301', '$SUPPLIER_TICKER', 'KOSPI', '[기재정정]단일판매ㆍ공급계약체결', '$BASE_DATE', '시드공급사B',
                 'https://seed.test/dart/$CORRECTION_RCEPT', '공사수주', '시드 배터리 공급', '$BASE_DATE', '2026-08-30',
                 '시드파트너B 주식회사', '시드파트너B', '$PARTNER_TICKER', true, '$CORRECTION_RCEPT', '계약 종료일 변경',
                 '{"contract_amount": "1500", "sales_ratio": "15.0"}'),
                ('$STALE_RCEPT', 'C939306', '$OTHER_TICKER', 'KOSPI', '단일판매ㆍ공급계약체결', '$PREV_DATE', '시드무관',
                 'https://seed.test/dart/$STALE_RCEPT', '용역제공', '시드 지난 계약', NULL, '2027-12-31',
                 '익명 상대', NULL, NULL, false, '$STALE_RCEPT', NULL,
                 '{"contract_amount": "300"}');

            INSERT INTO relation_sources (
                id, source_type, news_id, rcept_no, subject_name, subject_code, relation, object_name, object_code,
                item, source_sentence, polarity, tense, subject_impact, object_impact, mentioned_at
            ) VALUES
                ($REL_SUPPLY, 'news', $NEWS_A, NULL, '시드공급사B', '$SUPPLIER_TICKER', 'SUPPLIES_TO', '시드파트너B', '$PARTNER_TICKER',
                 '배터리', '시드공급사B는 시드파트너B에 배터리를 공급한다.', 'affirmed', 'past_or_present_fact', 'positive', 'neutral', '$BASE_DATE'),
                ($REL_PLANNED, 'news', $NEWS_D, NULL, '시드무관', '$OTHER_TICKER', 'INVESTS_IN', '시드비상장', NULL,
                 NULL, '시드무관이 시드비상장 투자를 검토한다.', 'affirmed', 'future_or_planned', 'neutral', 'positive', '$BASE_DATE'),
                ($REL_TERMINATED, 'disclosure', NULL, '$CORRECTION_RCEPT', '시드공급사B', '$SUPPLIER_TICKER', 'SUPPLIES_TO', '시드파트너B', '$PARTNER_TICKER',
                 '배터리', '기존 공급계약이 종료되었다.', 'terminated', 'past_or_present_fact', 'negative', 'neutral', '$BASE_DATE'),
                ($REL_STALE, 'news', $NEWS_STALE, NULL, '시드무관', '$OTHER_TICKER', 'ACQUIRES', '시드옛회사', NULL,
                 NULL, '지난 인수 문장.', 'affirmed', 'past_or_present_fact', 'positive', 'neutral', '$PREV_DATE');
            """.trimIndent(),
        )
    }

    fun cleanup() = execute(
        """
        DELETE FROM relation_sources WHERE id BETWEEN 7700 AND 7799;
        DELETE FROM disclosures WHERE rcept_no LIKE '9998%';
        DELETE FROM news WHERE id BETWEEN 9001 AND 9099;
        DELETE FROM news_clusters WHERE id BETWEEN 500 AND 599;
        DELETE FROM stocks WHERE id BETWEEN 9301 AND 9399;
        DELETE FROM companies WHERE id BETWEEN 9301 AND 9399;
        """.trimIndent(),
    )

    private fun execute(sql: String) {
        val etl = TestContainers.etlPostgres
        DriverManager.getConnection(etl.jdbcUrl, etl.username, etl.password)
            .use { connection -> connection.createStatement().use { it.execute(sql) } }
    }
}
