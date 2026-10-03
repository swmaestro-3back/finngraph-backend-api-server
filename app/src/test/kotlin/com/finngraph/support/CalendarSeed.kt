package com.finngraph.support

import java.sql.DriverManager
import java.time.LocalDate
import java.time.ZoneId

object CalendarSeed {

    const val KRX300_TICKER = "960001"
    const val FAVORITE_TICKER = "960002"
    const val OTHER_TICKER = "960003"
    const val DELISTED_KRX300_TICKER = "960004"

    fun seed() {
        val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
        fun d(offset: Long) = "'${today.plusDays(offset)}'"
        execute(
            """
            INSERT INTO stocks (id, name, ticker, market, standard_code, source, is_active, krx300) VALUES
                (9601, '캘린더대형', '$KRX300_TICKER', 'KOSPI', 'KR7960001001', 'TEST', true, true),
                (9602, '캘린더관심', '$FAVORITE_TICKER', 'KOSDAQ', 'KR7960002009', 'TEST', true, false),
                (9603, '캘린더기타', '$OTHER_TICKER', 'KOSDAQ', 'KR7960003007', 'TEST', true, false),
                (9604, '캘린더상폐', '$DELISTED_KRX300_TICKER', 'KOSPI', 'KR7960004005', 'TEST', false, true);
            INSERT INTO stock_calendar_events
                (event_date, kind, ticker, stock_name, source, source_key, basis_date, end_date, amount, ratio, label, detail) VALUES
                ('2026-11-09', 'DIV_EX', '$KRX300_TICKER', '예탁원법인명', 'KSD_TEST', 'k1', '2026-11-10', NULL, NULL, NULL, '분기', '{"estimated": false}'),
                ('2026-11-10', 'DIV_RECORD', '$KRX300_TICKER', '예탁원법인명', 'KSD_TEST', 'k1', '2026-11-10', NULL, NULL, NULL, '분기', '{}'),
                ('2026-11-09', 'AGM', '$FAVORITE_TICKER', '캘린더관심(주)', 'KSD_TEST', 'k2', '2026-10-20', NULL, NULL, NULL, '임시총회', '{"agenda": ["정관변경", "이사선임"], "agenda_truncated": true}'),
                ('2026-11-12', 'BONUS_EX', '$OTHER_TICKER', '예탁원기타법인명', 'KSD_TEST', 'k3', '2026-11-13', NULL, NULL, 50.00, NULL, '{}'),
                ('2026-11-10', 'DIV_RECORD', '$DELISTED_KRX300_TICKER', '캘린더상폐', 'KSD_TEST', 'k4', '2026-11-10', NULL, 100, NULL, '결산', '{}'),
                ('2026-12-20', 'RIGHTS_SUBSCRIBE', '$KRX300_TICKER', '예탁원법인명', 'KSD_TEST', 'k5', '2026-11-20', '2026-12-21', 5000, 30.00, NULL, '{}');
            INSERT INTO market_days (trade_date, is_open, is_business_day, is_settlement_day, weekday_code) VALUES
                ('2026-11-07', false, false, false, '07'),
                ('2026-11-08', false, false, false, '01'),
                ('2026-11-09', true, true, true, '02'),
                ('2026-11-11', false, true, false, '04')
            ON CONFLICT (trade_date) DO NOTHING;
            INSERT INTO ipo_offerings
                (ticker, name, subscr_start, subscr_end, offer_price, pay_date, refund_date, listing_date, lead_managers, basis_date) VALUES
                ('960101', '공모예정', ${d(3)}, ${d(4)}, NULL, NULL, NULL, NULL, '테스트증권', ${d(3)}),
                ('960102', '공모청약중', ${d(-1)}, ${d(1)}, 23500, ${d(3)}, ${d(3)}, NULL, '테스트증권', ${d(-1)}),
                ('960103', '공모상장대기', ${d(-5)}, ${d(-4)}, 12300, ${d(-2)}, ${d(-2)}, ${d(2)}, '테스트증권', ${d(-5)}),
                ('960104', '공모상장', ${d(-12)}, ${d(-11)}, 10000, ${d(-9)}, ${d(-9)}, ${d(-5)}, '테스트증권', ${d(-12)}),
                ('960105', '공모오래전', ${d(-40)}, ${d(-39)}, 9000, ${d(-37)}, ${d(-37)}, ${d(-30)}, '테스트증권', ${d(-40)});
            """.trimIndent(),
        )
    }

    fun delistOther() = execute("UPDATE stocks SET is_active = false WHERE id = 9603;")

    fun insertUnknownKind() = execute(
        """
        ALTER TABLE stock_calendar_events DROP CONSTRAINT chk_stock_calendar_events_kind;
        INSERT INTO stock_calendar_events (event_date, kind, ticker, stock_name, source, source_key, basis_date, detail)
        VALUES ('2026-11-13', 'EARNINGS', '$KRX300_TICKER', '예탁원법인명', 'KSD_TEST', 'k9', '2026-11-13', '{}');
        """.trimIndent(),
    )

    fun removeUnknownKind() = execute(
        """
        DELETE FROM stock_calendar_events WHERE source = 'KSD_TEST' AND kind = 'EARNINGS';
        ALTER TABLE stock_calendar_events ADD CONSTRAINT chk_stock_calendar_events_kind CHECK (kind IN (
            'DIV_EX', 'DIV_RECORD', 'DIV_PAY', 'BONUS_EX', 'BONUS_LIST',
            'RIGHTS_EX', 'RIGHTS_SUBSCRIBE', 'RIGHTS_LIST', 'AGM'));
        """.trimIndent(),
    )

    fun cleanup() = execute(
        """
        DELETE FROM stock_calendar_events WHERE source = 'KSD_TEST';
        DELETE FROM ipo_offerings WHERE ticker BETWEEN '960101' AND '960199';
        DELETE FROM market_days WHERE trade_date IN ('2026-11-07', '2026-11-08', '2026-11-09', '2026-11-11');
        DELETE FROM stocks WHERE id BETWEEN 9601 AND 9604;
        """.trimIndent(),
    )

    private fun execute(sql: String) {
        val etl = TestContainers.etlPostgres
        DriverManager.getConnection(etl.jdbcUrl, etl.username, etl.password)
            .use { connection -> connection.createStatement().use { it.execute(sql) } }
    }
}
