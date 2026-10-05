package com.finngraph.support

import java.sql.DriverManager

object IssueTimelineSeed {

    const val ROOT = 611L
    const val MIDDLE = 612L
    const val HIDDEN = 613L
    const val UNTITLED = 614L
    const val CURRENT = 615L
    const val SIBLING = 616L
    const val DUPLICATE = 617L
    const val LATER = 618L
    const val TWIN_EARLY = 621L
    const val TWIN_LATE = 622L
    const val UNLINKED = 623L
    const val UNLINKED_UNTITLED = 624L
    const val NO_PUBLIC = 625L
    const val CYCLE_A = 626L
    const val CYCLE_B = 627L
    const val GAP_ROOT = 628L
    const val GAP_HIDDEN = 629L
    const val GAP_LATEST = 630L
    const val BRIDGE_ROOT = 631L
    const val BRIDGE_HIDDEN = 632L
    const val BRIDGE_LATEST = 633L
    const val BRIDGE_SIBLING = 634L

    const val SIBLING_PUBLIC_NEWS = 9751L

    // 부모 사슬(← 는 follow_up, ⇐ 는 same_event):
    //   LATER ← DUPLICATE ⇐ CURRENT ← UNTITLED ← HIDDEN ← MIDDLE ← ROOT
    //   SIBLING ← MIDDLE                 같은 루트의 다른 갈래
    //   TWIN_LATE ⇐ TWIN_EARLY           편입 기사 수가 같은 같은 사건
    //   CYCLE_A ← CYCLE_B ← CYCLE_A      잘못된 데이터(순환)
    //   GAP_LATEST ⇐ GAP_HIDDEN ← GAP_ROOT                같은 사건 묶음의 맨 위가 노드가 못 되는 경우
    //   BRIDGE_LATEST ⇐ BRIDGE_HIDDEN ⇐ BRIDGE_ROOT       같은 사건 묶음의 가운데가 노드가 못 되는 경우
    //   BRIDGE_SIBLING ⇐ BRIDGE_ROOT                      같은 사건이 사슬 밖으로 갈라진 갈래
    // HIDDEN·GAP_HIDDEN·BRIDGE_HIDDEN 은 공개 기사가 없고 UNTITLED 는 클러스터 제목이 없어 노드가 못 된다.
    // 다른 이슈 테스트의 날짜 창(오늘 기준, 2020-02-03)에 걸리지 않도록 날짜를 전부 2025~2026년 초로 둔다.
    fun seed() {
        cleanup()
        execute(
            """
            INSERT INTO news_clusters (
                id, title, keywords, member_count, original_size, first_published_at, last_published_at,
                story_root_id, parent_cluster_id, link_score, link_relation, linked_at
            ) VALUES
                ($ROOT, '마이크론 실적 발표 앞둬', '{마이크론}', 3, 3,
                 '2025-09-29 15:00:00+00', '2025-09-29 20:00:00+00', $ROOT, NULL, NULL, NULL, '2025-09-30 06:00:00+09'),
                ($MIDDLE, '마이크론 HBM 공급 확대', '{HBM}', 1, 1,
                 '2025-10-15 09:00:00+09', '2025-10-15 09:00:00+09', $ROOT, $ROOT, 0.61, 'follow_up', '2025-10-15 10:00:00+09'),
                ($HIDDEN, '마이크론 감산 종료', '{감산}', 1, 1,
                 '2025-11-01 09:00:00+09', '2025-11-01 09:00:00+09', $ROOT, $MIDDLE, 0.55, 'follow_up', '2025-11-01 10:00:00+09'),
                ($UNTITLED, NULL, '{}', 1, 1,
                 '2025-11-05 09:00:00+09', '2025-11-05 09:00:00+09', $ROOT, $HIDDEN, 0.5, 'follow_up', '2025-11-05 10:00:00+09'),
                ($CURRENT, '마이크론 4분기 실적 발표', '{실적}', 3, 4,
                 '2025-12-17 09:00:00+09', '2025-12-18 10:00:00+09', $ROOT, $UNTITLED, 0.66, 'follow_up', '2025-12-18 11:00:00+09'),
                ($SIBLING, '마이크론 주가 급등', '{주가}', 2, 2,
                 '2025-12-17 10:00:00+09', '2025-12-17 11:00:00+09', $ROOT, $MIDDLE, 0.52, 'follow_up', '2025-12-17 12:00:00+09'),
                ($DUPLICATE, '마이크론 4분기 매출 46% 증가', '{매출}', 1, 2,
                 '2025-12-17 11:00:00+09', '2025-12-17 11:00:00+09', $ROOT, $CURRENT, 0.81, 'same_event', '2025-12-18 11:00:00+09'),
                ($LATER, '마이크론 1분기 실적 발표', '{실적}', 1, 3,
                 '2026-03-20 09:00:00+09', '2026-03-20 09:00:00+09', $ROOT, $DUPLICATE, 0.7, 'follow_up', '2026-03-20 10:00:00+09'),
                ($TWIN_EARLY, '미국 8월 건설지출 발표', '{건설}', 1, 3,
                 '2025-10-01 23:00:00+09', '2025-10-01 23:00:00+09', $TWIN_EARLY, NULL, NULL, NULL, '2025-10-02 00:00:00+09'),
                ($TWIN_LATE, '미 건설지출 0.9% 증가', '{건설}', 2, 3,
                 '2025-10-02 08:00:00+09', '2025-10-02 12:00:00+09', $TWIN_EARLY, $TWIN_EARLY, 0.78, 'same_event', '2025-10-02 13:00:00+09'),
                ($UNLINKED, '삼성전자 실적 발표 앞둬', '{삼성}', 1, 1,
                 '2025-10-03 09:00:00+09', '2025-10-03 09:00:00+09', NULL, NULL, NULL, NULL, NULL),
                ($UNLINKED_UNTITLED, NULL, '{}', 1, 1,
                 '2025-10-03 10:00:00+09', '2025-10-03 10:00:00+09', NULL, NULL, NULL, NULL, NULL),
                ($NO_PUBLIC, '비공개 이슈', '{비공개}', 1, 1,
                 '2025-10-04 09:00:00+09', '2025-10-04 09:00:00+09', NULL, NULL, NULL, NULL, NULL),
                ($CYCLE_A, '순환 이슈 A', '{순환}', 1, 1,
                 '2025-10-05 09:00:00+09', '2025-10-05 09:00:00+09', $CYCLE_B, $CYCLE_B, 0.6, 'follow_up', '2025-10-05 10:00:00+09'),
                ($CYCLE_B, '순환 이슈 B', '{순환}', 1, 1,
                 '2025-10-05 08:00:00+09', '2025-10-05 08:00:00+09', $CYCLE_B, $CYCLE_A, 0.6, 'follow_up', '2025-10-05 10:00:00+09'),
                ($GAP_ROOT, '테슬라 리콜 발표', '{리콜}', 1, 1,
                 '2025-11-10 09:00:00+09', '2025-11-10 09:00:00+09', $GAP_ROOT, NULL, NULL, NULL, '2025-11-10 10:00:00+09'),
                ($GAP_HIDDEN, '테슬라 리콜 대상 확대', '{리콜}', 3, 3,
                 '2025-11-20 09:00:00+09', '2025-11-20 09:00:00+09', $GAP_ROOT, $GAP_ROOT, 0.58, 'follow_up', '2025-11-20 10:00:00+09'),
                ($GAP_LATEST, '테슬라 리콜 차종 공개', '{리콜}', 1, 1,
                 '2025-11-20 11:00:00+09', '2025-11-20 11:00:00+09', $GAP_ROOT, $GAP_HIDDEN, 0.8, 'same_event', '2025-11-20 12:00:00+09'),
                ($BRIDGE_ROOT, '애플 신제품 발표', '{애플}', 2, 2,
                 '2025-11-12 09:00:00+09', '2025-11-12 09:00:00+09', $BRIDGE_ROOT, NULL, NULL, NULL, '2025-11-12 10:00:00+09'),
                ($BRIDGE_HIDDEN, '애플 신제품 가격 공개', '{애플}', 3, 3,
                 '2025-11-12 10:00:00+09', '2025-11-12 10:00:00+09', $BRIDGE_ROOT, $BRIDGE_ROOT, 0.83, 'same_event', '2025-11-12 11:00:00+09'),
                ($BRIDGE_LATEST, '애플 신제품 사전 주문', '{애플}', 1, 1,
                 '2025-11-12 11:00:00+09', '2025-11-12 11:00:00+09', $BRIDGE_ROOT, $BRIDGE_HIDDEN, 0.79, 'same_event', '2025-11-12 12:00:00+09'),
                ($BRIDGE_SIBLING, '애플 신제품 출시일 확정', '{애플}', 1, 1,
                 '2025-11-12 12:00:00+09', '2025-11-12 12:00:00+09', $BRIDGE_ROOT, $BRIDGE_ROOT, 0.77, 'same_event', '2025-11-12 13:00:00+09');

            INSERT INTO news (id, title, summary, summary_points, link, originallink, published_at, triple_extracted, cluster_id) VALUES
                (9701, '루트 미처리 기사', NULL, NULL,
                 'https://seed.test/timeline/9701', 'https://press-a.test/9701', '2025-09-29 15:00:00+00', NULL, $ROOT),
                (9702, '루트 이른 기사', '루트 이른 기사 요약이에요.', NULL,
                 'https://seed.test/timeline/9702', 'https://press-b.test/9702', '2025-09-29 16:00:00+00', true, $ROOT),
                (9703, '루트 대표 기사', '마이크론이 다음 주 실적을 발표해요. 시장 기대가 커요.',
                 '[{"kind": "AFFECTED", "text": "메모리 업종이 영향을 받아요."}, {"kind": "CHANGE", "text": "마이크론이 4분기 실적 발표 일정을 확정했어요."}]',
                 'https://seed.test/timeline/9703', 'https://press-c.test/9703', '2025-09-29 20:00:00+00', true, $ROOT),
                (9711, '중간 대표 기사', '매출이 0.9% 늘었어요. HBM 공급이 확대돼요.',
                 '[{"kind": "SCALE", "text": "공급 규모가 두 배예요."}]',
                 'https://seed.test/timeline/9711', 'https://press-a.test/9711', '2025-10-15 09:00:00+09', true, $MIDDLE),
                (9721, '숨은 기사', '숨은 요약이에요.', NULL,
                 'https://seed.test/timeline/9721', 'https://press-a.test/9721', '2025-11-01 09:00:00+09', false, $HIDDEN),
                (9731, '제목 없는 이슈 기사', '제목 없는 이슈 요약이에요.', NULL,
                 'https://seed.test/timeline/9731', 'https://press-a.test/9731', '2025-11-05 09:00:00+09', true, $UNTITLED),
                (9741, '현재 미처리 기사', NULL, NULL,
                 'https://seed.test/timeline/9741', 'https://press-a.test/9741', '2025-12-17 09:00:00+09', NULL, $CURRENT),
                (9742, '현재 대표 기사', '마이크론 4분기 매출이 전년 대비 46% 늘었어요. 가이던스도 올렸어요.',
                 '[{"kind": "CHANGE", "text": "마이크론 4분기 매출이 46% 늘었어요."}]',
                 'https://seed.test/timeline/9742', 'https://press-b.test/9742', '2025-12-17 12:00:00+09', true, $CURRENT),
                (9743, '현재 후속 기사', '후속 요약이에요.', NULL,
                 'https://seed.test/timeline/9743', 'https://press-c.test/9743', '2025-12-18 10:00:00+09', true, $CURRENT),
                ($SIBLING_PUBLIC_NEWS, '갈래 공개 기사', '실적 발표 뒤 주가가 7% 올랐어요. 거래량도 늘었어요.', NULL,
                 'https://seed.test/timeline/9751', 'https://press-a.test/9751', '2025-12-17 10:00:00+09', true, $SIBLING),
                (9752, '갈래 대표 미처리 기사', '갈래 숨은 요약이에요.',
                 '[{"kind": "CHANGE", "text": "공개 전 기사의 변화 문장이에요."}]',
                 'https://seed.test/timeline/9752', 'https://press-b.test/9752', '2025-12-17 11:00:00+09', false, $SIBLING),
                (9761, '중복 대표 기사', '중복 요약이에요.',
                 '[{"kind": "CHANGE", "text": "중복 이슈의 변화 문장이에요."}]',
                 'https://seed.test/timeline/9761', 'https://press-a.test/9761', '2025-12-17 11:00:00+09', true, $DUPLICATE),
                (9771, '다음 분기 기사', '  1분기 실적을 발표했어요  ', '[]',
                 'https://seed.test/timeline/9771', 'https://press-a.test/9771', '2026-03-20 09:00:00+09', true, $LATER),
                (9781, '건설지출 첫 기사', NULL, '{"oops": true}',
                 'https://seed.test/timeline/9781', 'https://press-a.test/9781', '2025-10-01 23:00:00+09', true, $TWIN_EARLY),
                (9791, '건설지출 둘째 기사', '건설지출 요약이에요.', NULL,
                 'https://seed.test/timeline/9791', 'https://press-a.test/9791', '2025-10-02 08:00:00+09', true, $TWIN_LATE),
                (9792, '건설지출 셋째 기사', NULL, NULL,
                 'https://seed.test/timeline/9792', 'https://press-b.test/9792', '2025-10-02 12:00:00+09', true, $TWIN_LATE),
                (9795, '연결 전 기사', NULL, NULL,
                 'https://seed.test/timeline/9795', 'https://press-a.test/9795', '2025-10-03 09:00:00+09', true, $UNLINKED),
                (9796, '제목 없는 연결 전 기사', NULL, NULL,
                 'https://seed.test/timeline/9796', 'https://press-a.test/9796', '2025-10-03 10:00:00+09', true, $UNLINKED_UNTITLED),
                (9797, '비공개 기사', NULL, NULL,
                 'https://seed.test/timeline/9797', 'https://press-a.test/9797', '2025-10-04 09:00:00+09', false, $NO_PUBLIC),
                (9798, '순환 A 기사', NULL, NULL,
                 'https://seed.test/timeline/9798', 'https://press-a.test/9798', '2025-10-05 09:00:00+09', true, $CYCLE_A),
                (9799, '순환 B 기사', NULL, NULL,
                 'https://seed.test/timeline/9799', 'https://press-a.test/9799', '2025-10-05 08:00:00+09', true, $CYCLE_B),
                (9801, '리콜 발표 기사', NULL, NULL,
                 'https://seed.test/timeline/9801', 'https://press-a.test/9801', '2025-11-10 09:00:00+09', true, $GAP_ROOT),
                (9802, '리콜 확대 비공개 기사', NULL, NULL,
                 'https://seed.test/timeline/9802', 'https://press-a.test/9802', '2025-11-20 09:00:00+09', false, $GAP_HIDDEN),
                (9803, '리콜 차종 기사', NULL, NULL,
                 'https://seed.test/timeline/9803', 'https://press-a.test/9803', '2025-11-20 11:00:00+09', true, $GAP_LATEST),
                (9804, '신제품 발표 기사', NULL, NULL,
                 'https://seed.test/timeline/9804', 'https://press-a.test/9804', '2025-11-12 09:00:00+09', true, $BRIDGE_ROOT),
                (9805, '신제품 가격 비공개 기사', NULL, NULL,
                 'https://seed.test/timeline/9805', 'https://press-a.test/9805', '2025-11-12 10:00:00+09', false, $BRIDGE_HIDDEN),
                (9806, '신제품 사전 주문 기사', NULL, NULL,
                 'https://seed.test/timeline/9806', 'https://press-a.test/9806', '2025-11-12 11:00:00+09', true, $BRIDGE_LATEST),
                (9807, '신제품 출시일 기사', NULL, NULL,
                 'https://seed.test/timeline/9807', 'https://press-a.test/9807', '2025-11-12 12:00:00+09', true, $BRIDGE_SIBLING);

            UPDATE news_clusters c
               SET representative_news_id = v.news_id
              FROM (VALUES
                  ($ROOT, 9703), ($MIDDLE, 9711), ($HIDDEN, 9721), ($UNTITLED, 9731), ($CURRENT, 9742),
                  ($SIBLING, 9752), ($DUPLICATE, 9761), ($LATER, 9771), ($TWIN_EARLY, 9781), ($TWIN_LATE, 9791),
                  ($UNLINKED, 9795), ($UNLINKED_UNTITLED, 9796), ($NO_PUBLIC, 9797), ($CYCLE_A, 9798), ($CYCLE_B, 9799),
                  ($GAP_ROOT, 9801), ($GAP_HIDDEN, 9802), ($GAP_LATEST, 9803),
                  ($BRIDGE_ROOT, 9804), ($BRIDGE_HIDDEN, 9805), ($BRIDGE_LATEST, 9806), ($BRIDGE_SIBLING, 9807)
              ) AS v (cluster_id, news_id)
             WHERE c.id = v.cluster_id;
            """.trimIndent(),
        )
    }

    fun cleanup() = execute(
        """
        DELETE FROM news WHERE id BETWEEN 9701 AND 9819;
        DELETE FROM news_clusters WHERE id BETWEEN 610 AND 639;
        """.trimIndent(),
    )

    private fun execute(sql: String) {
        val etl = TestContainers.etlPostgres
        DriverManager.getConnection(etl.jdbcUrl, etl.username, etl.password)
            .use { connection -> connection.createStatement().use { it.execute(sql) } }
    }
}
