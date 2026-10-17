package com.finngraph.support

import java.sql.DriverManager

object StockCalendarSeed {

    const val DIVIDEND_TICKER = "961101"
    const val RIGHTS_TICKER = "961102"
    const val BONUS_TICKER = "961103"
    const val INACTIVE_TICKER = "961104"
    const val YEAR = "?from=2027-01-01&to=2027-12-31"

    private const val SOURCE = "KSD_MODAL_TEST"

    fun seed() = execute(
        """
        INSERT INTO stocks (id, name, ticker, market, standard_code, source, is_active, krx300) VALUES
            (9611, '캘린더배당', '$DIVIDEND_TICKER', 'KOSPI', 'KR7961101001', 'TEST', true, false),
            (9612, '캘린더유상', '$RIGHTS_TICKER', 'KOSDAQ', 'KR7961102009', 'TEST', true, false),
            (9613, '캘린더무상', '$BONUS_TICKER', 'KOSDAQ', 'KR7961103007', 'TEST', true, false),
            (9614, '캘린더비활성', '$INACTIVE_TICKER', 'KOSPI', 'KR7961104005', 'TEST', false, false);
        INSERT INTO stock_candles_daily (stock_id, trade_date, open, high, low, close, volume, trade_value, source)
        SELECT s.id, d::date, 10000, 10000, 10000, 10000, 1000, 10000000, 'TEST'
          FROM (VALUES (9611), (9612), (9613)) AS s(id),
               generate_series('2027-01-04'::date, '2027-03-31'::date, interval '1 day') AS d
         WHERE extract(isodow FROM d) < 6;
        UPDATE stock_candles_daily SET open = 9600, close = 9700 WHERE stock_id = 9611 AND trade_date = '2027-01-28';
        UPDATE stock_candles_daily SET open = 9800, close = 9800 WHERE stock_id = 9611 AND trade_date = '2027-01-29';
        UPDATE stock_candles_daily SET open = 9900, close = 9900 WHERE stock_id = 9611 AND trade_date BETWEEN '2027-03-25' AND '2027-03-31';
        UPDATE stock_candles_daily SET open = 9950 WHERE stock_id = 9611 AND trade_date = '2027-03-25';
        UPDATE stock_candles_daily SET close = 11000 WHERE stock_id = 9612 AND trade_date = '2027-02-15';
        UPDATE stock_candles_daily SET open = 10300, base_price = 10400 WHERE stock_id = 9612 AND trade_date = '2027-02-16';
        UPDATE stock_candles_daily SET close = 20000 WHERE stock_id = 9613 AND trade_date = '2027-02-01';
        UPDATE stock_candles_daily SET base_price = 10000 WHERE stock_id = 9613 AND trade_date = '2027-02-02';
        UPDATE stock_candles_daily SET close = 11000 WHERE stock_id = 9613 AND trade_date = '2027-02-08';
        INSERT INTO stock_dividends (listing_id, record_date, divi_kind, dps, pay_date) VALUES
            (9611, '2027-03-31', '결산', 0, NULL),
            (9611, '2027-03-26', '분기', 100, '2027-04-20'),
            (9611, '2027-02-26', '분기', NULL, NULL),
            (9611, '2027-01-29', '결산', 500, '2027-04-15'),
            (9611, '2026-06-30', '결산', 300, NULL);
        INSERT INTO market_days (trade_date, is_open, is_business_day, is_settlement_day, weekday_code)
        SELECT d::date, extract(isodow FROM d) < 6, extract(isodow FROM d) < 6, extract(isodow FROM d) < 6,
               lpad((extract(dow FROM d)::int + 1)::text, 2, '0')
          FROM generate_series('2027-01-01'::date, '2027-04-30'::date, interval '1 day') AS d
        ON CONFLICT (trade_date) DO NOTHING;
        INSERT INTO stock_calendar_events
            (event_date, kind, ticker, stock_name, source, source_key, basis_date, end_date, amount, ratio, label, detail) VALUES
            ('2027-03-25', 'DIV_EX', '$DIVIDEND_TICKER', '예탁원배당', '$SOURCE', 'd1', '2027-03-26', NULL, 100, NULL, '분기', '{}'),
            ('2027-03-26', 'DIV_RECORD', '$DIVIDEND_TICKER', '예탁원배당', '$SOURCE', 'd1', '2027-03-26', NULL, 100, NULL, '분기', '{}'),
            ('2027-04-20', 'DIV_PAY', '$DIVIDEND_TICKER', '예탁원배당', '$SOURCE', 'd1', '2027-03-26', NULL, 100, NULL, '분기', '{}'),
            ('2027-04-29', 'DIV_EX', '$DIVIDEND_TICKER', '예탁원배당', '$SOURCE', 'd2', '2027-04-30', NULL, NULL, NULL, '결산', '{}'),
            ('2027-04-30', 'DIV_RECORD', '$DIVIDEND_TICKER', '예탁원배당', '$SOURCE', 'd2', '2027-04-30', NULL, NULL, NULL, '결산', '{}'),
            ('2027-01-05', 'DIV_PAY', '$DIVIDEND_TICKER', '예탁원배당', '$SOURCE', 'd0', '2026-12-15', NULL, 50, NULL, '분기', '{}'),
            ('2027-03-10', 'AGM', '$DIVIDEND_TICKER', '예탁원배당', '$SOURCE', 'a1', '2027-02-15', NULL, NULL, NULL, '임시총회', '{"agenda": ["합병승인", "사내이사 선임"]}'),
            ('2027-07-01', 'AGM', '$DIVIDEND_TICKER', '예탁원배당', '$SOURCE', 'a2', '2027-06-15', NULL, NULL, NULL, '임시총회', '{"agenda": ["정관변경"]}'),
            ('2027-02-16', 'RIGHTS_EX', '$RIGHTS_TICKER', '예탁원유상', '$SOURCE', 'r1', '2027-02-17', NULL, 8000, 25, NULL, '{}'),
            ('2027-03-15', 'RIGHTS_SUBSCRIBE', '$RIGHTS_TICKER', '예탁원유상', '$SOURCE', 'r1', '2027-02-17', '2027-03-16', 8000, 25, NULL, '{}'),
            ('2027-04-07', 'RIGHTS_LIST', '$RIGHTS_TICKER', '예탁원유상', '$SOURCE', 'r1', '2027-02-17', NULL, 8000, 25, NULL, '{}'),
            ('2027-02-02', 'BONUS_EX', '$BONUS_TICKER', '예탁원무상', '$SOURCE', 'b1', '2027-02-03', NULL, NULL, 100, NULL, '{}'),
            ('2027-02-24', 'BONUS_LIST', '$BONUS_TICKER', '예탁원무상', '$SOURCE', 'b1', '2027-02-03', NULL, NULL, 100, NULL, '{}');
        """.trimIndent(),
    )

    fun cleanup() = execute(
        """
        DELETE FROM stock_calendar_events WHERE source = '$SOURCE';
        DELETE FROM stock_dividends WHERE listing_id BETWEEN 9611 AND 9614;
        DELETE FROM stock_candles_daily WHERE stock_id BETWEEN 9611 AND 9614;
        DELETE FROM market_days WHERE trade_date BETWEEN '2027-01-01' AND '2027-04-30';
        DELETE FROM stocks WHERE id BETWEEN 9611 AND 9614;
        """.trimIndent(),
    )

    private fun execute(sql: String) {
        val etl = TestContainers.etlPostgres
        DriverManager.getConnection(etl.jdbcUrl, etl.username, etl.password)
            .use { connection -> connection.createStatement().use { it.execute(sql) } }
    }
}
