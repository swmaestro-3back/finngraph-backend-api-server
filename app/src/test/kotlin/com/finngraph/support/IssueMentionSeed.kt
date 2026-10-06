package com.finngraph.support

import java.sql.DriverManager
import java.time.LocalDate
import java.time.ZoneId

object IssueMentionSeed {

    val TODAY: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))
    const val THEME_DAY = "2020-02-03"
    const val THEME_PREV_DAY = "2020-02-02"

    const val RECENT = 401L
    const val MIDDLE = 402L
    const val OLD = 403L
    const val OUT_OF_RANGE = 404L
    const val HIDDEN_MENTION = 405L
    const val SPANNING = 406L
    const val INACTIVE_ONLY = 407L
    const val EDGE_IN = 408L
    const val EDGE_OUT = 409L
    const val UNEXTRACTED = 410L

    const val CROWDED = 411L
    const val TWO_MEDIA = 412L
    const val TWO_MEDIA_MORE_ARTICLES = 413L
    const val SINGLE_EARLY = 414L
    const val SINGLE_LATE = 415L
    const val SECOND_THEME = 416L
    const val INACTIVE_THEME = 417L
    const val HIDDEN_THEME_MENTION = 418L
    const val PREV_DAY_ONLY = 419L
    const val UNEXTRACTED_THEME = 420L

    const val TICKER_A = "975001"
    const val TICKER_B = "975002"
    const val TICKER_C = "975003"
    const val TICKER_INACTIVE = "975004"
    const val TICKER_OLD = "975005"
    const val TICKER_EDGE_IN = "975014"
    const val TICKER_EDGE_OUT = "975015"
    const val TICKER_T1 = "975011"
    const val TICKER_T2 = "975012"
    const val TICKER_T3 = "975013"
    const val TICKER_UNKNOWN = "975099"
    val CROWD = listOf("975006", "975007", "975008", "975009", "975010")

    const val THEME_MAIN = 9501L
    const val THEME_SECOND = 9502L
    const val THEME_INACTIVE = 9503L
    const val THEME_TODAY = 9504L
    const val THEME_UNKNOWN = 9599L

    fun day(daysAgo: Long): LocalDate = TODAY.minusDays(daysAgo)

    private fun at(daysAgo: Long, time: String) = "${day(daysAgo)} $time+09"

    fun seed() {
        cleanup()
        val stocks = (1..15).map { it to "9750%02d".format(it) }
        execute(
            buildString {
                appendLine("INSERT INTO companies (id, name, ticker, is_listed, country) VALUES")
                appendLine(stocks.joinToString(",\n") { (n, ticker) -> "(${9500 + n}, '가상기업$n', '$ticker', true, 'KR')" } + ";")
                appendLine("INSERT INTO stocks (id, company_id, name, ticker, market, standard_code, source, is_active) VALUES")
                appendLine(
                    stocks.joinToString(",\n") { (n, ticker) ->
                        "(${9500 + n}, ${9500 + n}, '가상종목$n', '$ticker', 'KOSPI', 'KR7${ticker}00$n', 'TEST', ${ticker != TICKER_INACTIVE})"
                    } + ";",
                )
            },
        )
        execute(
            """
            INSERT INTO news_clusters (id, title, keywords, member_count, original_size, first_published_at, last_published_at) VALUES
                ($RECENT, '가상 최근 이슈', '{최근}', 3, 3, '${at(1, "09:00:00")}', '${at(0, "09:00:00")}'),
                ($MIDDLE, '가상 중간 이슈', '{중간}', 2, 2, '${at(3, "09:00:00")}', '${at(3, "10:00:00")}'),
                ($OLD, '가상 오래된 이슈', '{오래}', 1, 1, '${at(40, "09:00:00")}', '${at(40, "09:00:00")}'),
                ($OUT_OF_RANGE, '가상 기간 밖 이슈', '{기간}', 1, 1, '${at(400, "09:00:00")}', '${at(400, "09:00:00")}'),
                ($HIDDEN_MENTION, '가상 비공개 언급 이슈', '{비공개}', 1, 1, '${at(1, "10:00:00")}', '${at(1, "10:00:00")}'),
                ($UNEXTRACTED, '가상 추출 전 이슈', '{추출전}', 2, 2, '${at(1, "11:00:00")}', '${at(1, "12:00:00")}'),
                ($SPANNING, '가상 긴 이슈', '{긴}', 2, 2, '${at(400, "10:00:00")}', '${at(20, "09:00:00")}'),
                ($INACTIVE_ONLY, '가상 비활성 이슈', '{비활성}', 1, 1, '${at(1, "13:00:00")}', '${at(1, "13:00:00")}'),
                ($EDGE_IN, '가상 경계 안 이슈', '{경계}', 1, 1, '${at(30, "00:00:00")}', '${at(30, "00:00:00")}'),
                ($EDGE_OUT, '가상 경계 밖 이슈', '{경계}', 1, 1, '${at(31, "23:59:59")}', '${at(31, "23:59:59")}'),
                ($CROWDED, '가상 붐비는 이슈', '{붐빔}', 3, 3, '$THEME_DAY 08:00:00+09', '$THEME_DAY 09:00:00+09'),
                ($TWO_MEDIA, '가상 두 매체 이슈', '{둘}', 2, 2, '$THEME_DAY 08:00:00+09', '$THEME_DAY 13:00:00+09'),
                ($TWO_MEDIA_MORE_ARTICLES, '가상 기사 많은 이슈', '{셋}', 3, 3, '$THEME_DAY 08:00:00+09', '$THEME_DAY 09:30:00+09'),
                ($SINGLE_EARLY, '가상 이른 단독 이슈', '{단독}', 1, 1, '$THEME_DAY 10:00:00+09', '$THEME_DAY 10:00:00+09'),
                ($SINGLE_LATE, '가상 늦은 단독 이슈', '{단독}', 1, 1, '$THEME_DAY 12:00:00+09', '$THEME_DAY 12:00:00+09'),
                ($SECOND_THEME, '가상 둘째 테마 이슈', '{둘째}', 1, 1, '$THEME_DAY 11:00:00+09', '$THEME_DAY 11:00:00+09'),
                ($INACTIVE_THEME, '가상 비활성 테마 이슈', '{비활성}', 1, 1, '$THEME_DAY 11:30:00+09', '$THEME_DAY 11:30:00+09'),
                ($HIDDEN_THEME_MENTION, '가상 숨은 테마 언급', '{숨김}', 1, 1, '$THEME_DAY 14:00:00+09', '$THEME_DAY 14:00:00+09'),
                ($UNEXTRACTED_THEME, '가상 추출 전 테마 언급', '{추출전}', 1, 1, '$THEME_DAY 15:00:00+09', '$THEME_DAY 15:00:00+09'),
                ($PREV_DAY_ONLY, '가상 전날 이슈', '{전날}', 1, 1, '$THEME_PREV_DAY 15:00:00+09', '$THEME_PREV_DAY 15:00:00+09');

            INSERT INTO news (id, title, summary, link, originallink, published_at, triple_extracted, cluster_id) VALUES
                (9501, '최근 첫 기사', '최근 첫 요약', 'https://seed.test/mention/9501', 'https://press-a.test/9501', '${at(1, "09:00:00")}', true, $RECENT),
                (9502, '최근 둘째 기사', '최근 둘째 요약', 'https://seed.test/mention/9502', 'https://press-b.test/9502', '${at(0, "08:00:00")}', true, $RECENT),
                (9503, '최근 미처리 기사', NULL, 'https://seed.test/mention/9503', 'https://press-c.test/9503', '${at(0, "09:00:00")}', NULL, $RECENT),
                (9511, '중간 첫 기사', '중간 첫 요약', 'https://seed.test/mention/9511', 'https://press-a.test/9511', '${at(3, "09:00:00")}', true, $MIDDLE),
                (9512, '중간 둘째 기사', '중간 둘째 요약', 'https://seed.test/mention/9512', 'https://press-b.test/9512', '${at(3, "10:00:00")}', true, $MIDDLE),
                (9513, '중간 삼중항 없음', NULL, 'https://seed.test/mention/9513', 'https://press-c.test/9513', '${at(1, "12:00:00")}', false, $UNEXTRACTED),
                (9521, '오래된 기사', '오래된 요약', 'https://seed.test/mention/9521', 'https://press-a.test/9521', '${at(40, "09:00:00")}', true, $OLD),
                (9531, '기간 밖 기사', '기간 밖 요약', 'https://seed.test/mention/9531', 'https://press-a.test/9531', '${at(400, "09:00:00")}', true, $OUT_OF_RANGE),
                (9541, '비공개 언급 공개 기사', '공개 요약', 'https://seed.test/mention/9541', 'https://press-a.test/9541', '${at(1, "10:00:00")}', true, $HIDDEN_MENTION),
                (9542, '비공개 언급 기사', NULL, 'https://seed.test/mention/9542', 'https://press-b.test/9542', '${at(1, "11:00:00")}', false, $UNEXTRACTED),
                (9551, '긴 이슈 첫 기사', '긴 첫 요약', 'https://seed.test/mention/9551', 'https://press-a.test/9551', '${at(400, "10:00:00")}', true, $SPANNING),
                (9552, '긴 이슈 최근 기사', '긴 최근 요약', 'https://seed.test/mention/9552', 'https://press-b.test/9552', '${at(20, "09:00:00")}', true, $SPANNING),
                (9561, '비활성 기사', '비활성 요약', 'https://seed.test/mention/9561', 'https://press-a.test/9561', '${at(1, "13:00:00")}', true, $INACTIVE_ONLY),
                (9571, '경계 안 기사', '경계 안 요약', 'https://seed.test/mention/9571', 'https://press-a.test/9571', '${at(30, "00:00:00")}', true, $EDGE_IN),
                (9581, '경계 밖 기사', '경계 밖 요약', 'https://seed.test/mention/9581', 'https://press-a.test/9581', '${at(31, "23:59:59")}', true, $EDGE_OUT),
                (9611, '붐비는 첫 기사', '붐비는 요약', 'https://seed.test/mention/9611', 'https://press-f.test/9611', '$THEME_DAY 08:00:00+09', true, $CROWDED),
                (9612, '붐비는 둘째 기사', '붐비는 둘째 요약', 'https://seed.test/mention/9612', 'https://press-g.test/9612', '$THEME_DAY 08:30:00+09', true, $CROWDED),
                (9613, '붐비는 셋째 기사', '붐비는 셋째 요약', 'https://seed.test/mention/9613', 'https://press-h.test/9613', '$THEME_DAY 09:00:00+09', true, $CROWDED),
                (9621, '두 매체 첫 기사', '두 매체 요약', 'https://seed.test/mention/9621', 'https://press-a.test/9621', '$THEME_DAY 08:00:00+09', true, $TWO_MEDIA),
                (9622, '두 매체 둘째 기사', '두 매체 둘째 요약', 'https://seed.test/mention/9622', 'https://press-b.test/9622', '$THEME_DAY 13:00:00+09', true, $TWO_MEDIA),
                (9631, '기사 많은 첫 기사', '기사 많은 요약', 'https://seed.test/mention/9631', 'https://press-a.test/9631', '$THEME_DAY 08:00:00+09', true, $TWO_MEDIA_MORE_ARTICLES),
                (9632, '기사 많은 둘째 기사', '기사 많은 둘째 요약', 'https://seed.test/mention/9632', 'https://www.press-a.test/9632', '$THEME_DAY 09:00:00+09', true, $TWO_MEDIA_MORE_ARTICLES),
                (9633, '기사 많은 셋째 기사', '기사 많은 셋째 요약', 'https://seed.test/mention/9633', 'https://press-b.test/9633', '$THEME_DAY 09:30:00+09', true, $TWO_MEDIA_MORE_ARTICLES),
                (9641, '이른 단독 기사', '이른 단독 요약', 'https://seed.test/mention/9641', 'https://press-a.test/9641', '$THEME_DAY 10:00:00+09', true, $SINGLE_EARLY),
                (9651, '늦은 단독 기사', '늦은 단독 요약', 'https://seed.test/mention/9651', 'https://press-a.test/9651', '$THEME_DAY 12:00:00+09', true, $SINGLE_LATE),
                (9661, '둘째 테마 기사', '둘째 테마 요약', 'https://seed.test/mention/9661', 'https://press-a.test/9661', '$THEME_DAY 11:00:00+09', true, $SECOND_THEME),
                (9671, '비활성 테마 기사', '비활성 테마 요약', 'https://seed.test/mention/9671', 'https://press-a.test/9671', '$THEME_DAY 11:30:00+09', true, $INACTIVE_THEME),
                (9681, '숨은 테마 공개 기사', '숨은 테마 요약', 'https://seed.test/mention/9681', 'https://press-a.test/9681', '$THEME_DAY 14:00:00+09', true, $HIDDEN_THEME_MENTION),
                (9682, '숨은 테마 언급 기사', NULL, 'https://seed.test/mention/9682', 'https://press-b.test/9682', '$THEME_DAY 15:00:00+09', NULL, $UNEXTRACTED_THEME),
                (9691, '전날 기사', '전날 요약', 'https://seed.test/mention/9691', 'https://press-a.test/9691', '$THEME_PREV_DAY 15:00:00+09', true, $PREV_DAY_ONLY);

            INSERT INTO news_companies (news_id, company_id) VALUES
                (9501, 9501), (9501, 9502),
                (9502, 9502),
                (9503, 9501),
                (9511, 9501),
                (9512, 9501), (9512, 9503),
                (9513, 9501),
                (9521, 9501), (9521, 9505),
                (9531, 9501),
                (9541, 9503),
                (9542, 9501),
                (9551, 9501),
                (9561, 9504),
                (9571, 9514),
                (9581, 9515),
                (9611, 9506), (9611, 9507), (9611, 9508), (9611, 9509), (9611, 9510), (9611, 9511),
                (9612, 9506), (9612, 9507), (9612, 9508), (9612, 9509), (9612, 9510),
                (9621, 9511),
                (9631, 9511),
                (9641, 9513),
                (9651, 9513),
                (9661, 9512),
                (9671, 9504),
                (9682, 9511),
                (9691, 9511);

            INSERT INTO themes (id, name) VALUES
                ($THEME_MAIN, '가상주력테마'),
                ($THEME_SECOND, '가상둘째테마'),
                ($THEME_INACTIVE, '가상비활성테마'),
                ($THEME_TODAY, '가상오늘테마');

            INSERT INTO theme_stocks (theme_id, stock_id) VALUES
                ($THEME_MAIN, 9511), ($THEME_MAIN, 9513), ($THEME_MAIN, 9504),
                ($THEME_SECOND, 9512),
                ($THEME_INACTIVE, 9504),
                ($THEME_TODAY, 9502);
            """.trimIndent(),
        )
    }

    fun cleanup() = execute(
        """
        DELETE FROM news WHERE id BETWEEN 9501 AND 9699;
        DELETE FROM news_clusters WHERE id BETWEEN 400 AND 449;
        DELETE FROM theme_stocks WHERE theme_id BETWEEN 9501 AND 9599;
        DELETE FROM themes WHERE id BETWEEN 9501 AND 9599;
        DELETE FROM stocks WHERE id BETWEEN 9501 AND 9515;
        DELETE FROM companies WHERE id BETWEEN 9501 AND 9515;
        """.trimIndent(),
    )

    private fun execute(sql: String) {
        val etl = TestContainers.etlPostgres
        DriverManager.getConnection(etl.jdbcUrl, etl.username, etl.password)
            .use { connection -> connection.createStatement().use { it.execute(sql) } }
    }
}
