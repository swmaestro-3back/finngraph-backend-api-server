package com.finngraph.support

import java.sql.DriverManager
import java.time.LocalDate
import java.time.ZoneId

object IssueSameEventSeed {

    const val ROOT = 650L
    const val LEAD = 651L
    const val TWIN = 652L
    const val SOLO = 653L

    const val TICKER = "976001"
    const val THEME_ID = 9851L
    const val QUERY = "동일사건수주"

    private val TODAY: LocalDate = LocalDate.now(ZoneId.of("Asia/Seoul"))

    val EVENT_DAY: LocalDate = TODAY.minusDays(1)

    private fun at(daysAgo: Long, time: String) = "${TODAY.minusDays(daysAgo)} $time+09"

    fun seed() {
        cleanup()
        execute(
            """
            INSERT INTO companies (id, name, ticker, is_listed, country) VALUES (9851, '가상동일사건기업', '$TICKER', true, 'KR');
            INSERT INTO stocks (id, company_id, name, ticker, market, standard_code, source, is_active) VALUES
                (9851, 9851, '가상동일사건종목', '$TICKER', 'KOSPI', 'KR7${TICKER}009', 'TEST', true);
            INSERT INTO themes (id, name) VALUES ($THEME_ID, '가상동일사건테마');
            INSERT INTO theme_stocks (theme_id, stock_id) VALUES ($THEME_ID, 9851);

            INSERT INTO news_clusters (
                id, title, keywords, member_count, first_published_at, mean_published_at, last_published_at,
                story_root_id, parent_cluster_id, link_score, link_relation, linked_at
            ) VALUES
                ($ROOT, '가상 수주 로드맵 발표', '{로드맵}', 1,
                 '${at(5, "09:00:00")}', '${at(5, "09:00:00")}', '${at(5, "09:00:00")}', $ROOT, NULL, NULL, NULL, '${at(5, "10:00:00")}'),
                ($LEAD, '가상 장비 수주 확정', '{수주}', 4,
                 '${at(1, "09:00:00")}', '${at(1, "09:30:00")}', '${at(1, "10:00:00")}', $ROOT, $ROOT, 0.7, 'follow_up', '${at(1, "11:00:00")}'),
                ($TWIN, '가상 장비 수주 잇따라', '{수주}', 2,
                 '${at(1, "11:00:00")}', '${at(1, "11:30:00")}', '${at(1, "12:00:00")}', $ROOT, $LEAD, 0.8, 'same_event', '${at(1, "13:00:00")}'),
                ($SOLO, '가상 단독 공시', '{공시}', 1,
                 '${at(1, "08:00:00")}', '${at(1, "08:00:00")}', '${at(1, "08:00:00")}', NULL, NULL, NULL, NULL, NULL);

            INSERT INTO news (id, title, summary, link, originallink, published_at, triple_extracted, cluster_id) VALUES
                (9851, '동일사건 로드맵 기사', '로드맵 요약', 'https://seed.test/same/9851', 'https://press-a.test/9851', '${at(5, "09:00:00")}', true, $ROOT),
                (9852, '$QUERY 첫 기사', '수주 첫 요약', 'https://seed.test/same/9852', 'https://press-a.test/9852', '${at(1, "09:00:00")}', true, $LEAD),
                (9853, '$QUERY 둘째 기사', '수주 둘째 요약', 'https://seed.test/same/9853', 'https://press-b.test/9853', '${at(1, "10:00:00")}', true, $LEAD),
                (9854, '$QUERY 셋째 기사', '수주 셋째 요약', 'https://seed.test/same/9854', 'https://press-c.test/9854', '${at(1, "11:00:00")}', true, $TWIN),
                (9855, '$QUERY 넷째 기사', '수주 넷째 요약', 'https://seed.test/same/9855', 'https://press-d.test/9855', '${at(1, "12:00:00")}', true, $TWIN),
                (9856, '동일사건 단독 기사', '단독 요약', 'https://seed.test/same/9856', 'https://press-a.test/9856', '${at(1, "08:00:00")}', true, $SOLO);

            INSERT INTO news_companies (news_id, company_id) VALUES
                (9851, 9851), (9852, 9851), (9853, 9851), (9854, 9851), (9855, 9851), (9856, 9851);

            UPDATE news_clusters c
               SET representative_news_id = (SELECT min(n.id) FROM news n WHERE n.cluster_id = c.id)
             WHERE c.id BETWEEN 650 AND 659;
            """.trimIndent(),
        )
    }

    fun cleanup() = execute(
        """
        DELETE FROM news WHERE id BETWEEN 9851 AND 9869;
        DELETE FROM news_clusters WHERE id BETWEEN 650 AND 659;
        DELETE FROM theme_stocks WHERE theme_id = $THEME_ID;
        DELETE FROM themes WHERE id = $THEME_ID;
        DELETE FROM stocks WHERE id = 9851;
        DELETE FROM companies WHERE id = 9851;
        """.trimIndent(),
    )

    private fun execute(sql: String) {
        val etl = TestContainers.etlPostgres
        DriverManager.getConnection(etl.jdbcUrl, etl.username, etl.password)
            .use { connection -> connection.createStatement().use { it.execute(sql) } }
    }
}
