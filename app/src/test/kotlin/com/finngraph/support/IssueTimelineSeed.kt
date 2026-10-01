package com.finngraph.support

import java.sql.DriverManager

object IssueTimelineSeed {

    const val ROOT = 611L
    const val MIDDLE = 612L
    const val UNTITLED = 613L
    const val CURRENT = 614L
    const val SIBLING = 615L
    const val LATER = 616L
    const val UNLINKED = 617L
    const val UNLINKED_UNTITLED = 618L
    const val OTHER_STORY = 619L
    const val PULLED_EARLIER = 620L
    const val BEHIND_UNTITLED = 621L

    // 브리핑 테스트의 최근 클러스터 조회 창(2026-07-29 이후)에 걸리지 않도록 날짜를 전부 그 전으로 둔다.
    // PULLED_EARLIER 는 부모(CURRENT)에 이어진 뒤 더 이른 기사가 합류해 시작 시각이 부모보다 앞당겨진 경우다.
    fun seed() {
        cleanup()
        execute(
            """
            INSERT INTO news_clusters (
                id, title, summary, member_count, original_size, first_published_at, last_published_at,
                story_root_id, parent_cluster_id, link_score, linked_at
            ) VALUES
                ($ROOT, '마이크론 실적 발표 앞둬', '마이크론이 다음 주 회계연도 4분기 실적을 발표해요.', 3, 3,
                 '2025-09-29 16:00:00+00', '2025-09-29 20:00:00+00', $ROOT, NULL, NULL, '2025-09-30 02:00:00+09'),
                ($MIDDLE, '마이크론 HBM 공급 확대', NULL, 3, 3,
                 '2025-10-15 09:00:00+09', '2025-10-15 18:00:00+09', $ROOT, $ROOT, 0.81, '2025-10-15 19:00:00+09'),
                ($UNTITLED, NULL, NULL, 2, 2,
                 '2025-11-01 09:00:00+09', '2025-11-01 10:00:00+09', $ROOT, $MIDDLE, 0.77, '2025-11-01 11:00:00+09'),
                ($CURRENT, '마이크론 4분기 실적 발표', '마이크론 4분기 매출이 전년 대비 46% 늘었어요.', 4, 4,
                 '2025-12-17 09:00:00+09', '2025-12-18 10:00:00+09', $ROOT, $MIDDLE, 0.86, '2025-12-18 11:00:00+09'),
                ($SIBLING, '마이크론 주가 급등', '실적 발표 뒤 마이크론 주가가 7% 올랐어요.', 3, 3,
                 '2025-12-17 09:00:00+09', '2025-12-17 15:00:00+09', $ROOT, $MIDDLE, 0.79, '2025-12-17 16:00:00+09'),
                ($LATER, '마이크론 1분기 실적 발표', NULL, 3, 3,
                 '2026-03-20 09:00:00+09', '2026-03-20 12:00:00+09', $ROOT, $CURRENT, 0.83, '2026-03-20 13:00:00+09'),
                ($UNLINKED, '미국 8월 건설지출 발표', '미국 8월 건설지출이 전월 대비 0.9% 늘었어요.', 3, 3,
                 '2025-10-01 23:00:00+09', '2025-10-02 01:00:00+09', NULL, NULL, NULL, NULL),
                ($UNLINKED_UNTITLED, NULL, NULL, 1, 1,
                 '2025-10-02 09:00:00+09', '2025-10-02 09:00:00+09', NULL, NULL, NULL, NULL),
                ($OTHER_STORY, '삼성전자 실적 발표 앞둬', NULL, 3, 3,
                 '2025-10-01 09:00:00+09', '2025-10-01 12:00:00+09', $OTHER_STORY, NULL, NULL, '2025-10-01 13:00:00+09'),
                ($PULLED_EARLIER, '마이크론 실적 후 목표가 상향', NULL, 3, 3,
                 '2025-12-16 09:00:00+09', '2025-12-19 10:00:00+09', $ROOT, $CURRENT, 0.74, '2025-12-18 15:00:00+09'),
                ($BEHIND_UNTITLED, '마이크론 감산 종료', NULL, 3, 3,
                 '2025-11-05 09:00:00+09', '2025-11-05 12:00:00+09', $ROOT, $UNTITLED, 0.72, '2025-11-05 13:00:00+09');
            """.trimIndent(),
        )
    }

    fun cleanup() = execute("DELETE FROM news_clusters WHERE id BETWEEN 610 AND 629;")

    private fun execute(sql: String) {
        val etl = TestContainers.etlPostgres
        DriverManager.getConnection(etl.jdbcUrl, etl.username, etl.password)
            .use { connection -> connection.createStatement().use { it.execute(sql) } }
    }
}
