package com.finngraph.news.adapter

import com.finngraph.news.adapter.jooq.tables.references.NEWS
import com.finngraph.news.adapter.jooq.tables.references.RELATION_SOURCES
import com.finngraph.news.model.NewsDetail
import com.finngraph.news.model.NewsId
import com.finngraph.news.model.NewsView
import com.finngraph.news.model.PageResult
import com.finngraph.news.port.NewsQueryPort
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.Record1
import org.jooq.Select
import org.springframework.stereotype.Component

@Component
class JooqNewsQuery(private val dsl: DSLContext) : NewsQueryPort {
    override fun findPage(page: Int, size: Int): PageResult<NewsView> =
        fetchPage(VISIBLE, page, size)

    override fun findByIds(ids: List<NewsId>): Map<NewsId, NewsDetail> {
        if (ids.isEmpty()) return emptyMap()

        return dsl.select(
            NEWS.ID,
            NEWS.TITLE,
            NEWS.SUMMARY,
            NEWS.LINK,
            NEWS.ORIGINALLINK,
            NEWS.PUBLISHED_AT,
            NEWS.COLLECTED_AT,
        )
            .from(NEWS)
            .where(NEWS.ID.`in`(ids.map { it.value }).and(VISIBLE))
            .fetch { it.toNewsDetail() }
            .associateBy { NewsId(it.id) }
    }

    override fun findPageByTicker(ticker: String, page: Int, size: Int): PageResult<NewsView> =
        fetchPage(NEWS.ID.`in`(relatedNewsIds(ticker)).and(VISIBLE), page, size)

    private fun fetchPage(condition: Condition, page: Int, size: Int): PageResult<NewsView> {
        val rows = dsl.select(
            NEWS.ID,
            NEWS.TITLE,
            NEWS.SUMMARY,
            NEWS.LINK,
            NEWS.PUBLISHED_AT,
            NEWS.COLLECTED_AT,
        )
            .from(NEWS)
            .where(condition)
            .orderBy(NEWS.PUBLISHED_AT.desc().nullsLast(), NEWS.ID.desc())
            .limit(size)
            .offset(page.toLong() * size)
            .fetch { it.toNewsView() }

        val total = dsl.selectCount()
            .from(NEWS)
            .where(condition)
            .fetchOne(0, Long::class.javaObjectType) ?: 0L

        return PageResult(content = rows, page = page, size = size, totalElements = total)
    }

    private fun relatedNewsIds(ticker: String): Select<Record1<Long?>> =
        dsl.select(RELATION_SOURCES.NEWS_ID)
            .from(RELATION_SOURCES)
            .where(RELATION_SOURCES.SUBJECT_CODE.eq(ticker))
            .union(
                dsl.select(RELATION_SOURCES.NEWS_ID)
                    .from(RELATION_SOURCES)
                    .where(RELATION_SOURCES.OBJECT_CODE.eq(ticker)),
            )

    private fun Record.toNewsView() = NewsView(
        id = requireNotNull(get(NEWS.ID)),
        title = get(NEWS.TITLE),
        summary = get(NEWS.SUMMARY),
        url = get(NEWS.LINK),
        publishedAt = get(NEWS.PUBLISHED_AT),
        collectedAt = get(NEWS.COLLECTED_AT),
    )

    private fun Record.toNewsDetail() = NewsDetail(
        id = requireNotNull(get(NEWS.ID)),
        title = get(NEWS.TITLE),
        summary = get(NEWS.SUMMARY),
        url = get(NEWS.LINK),
        originalUrl = get(NEWS.ORIGINALLINK),
        publishedAt = get(NEWS.PUBLISHED_AT),
        collectedAt = get(NEWS.COLLECTED_AT),
    )

    companion object {
        // isTrue() 가 만드는 IS TRUE 는 부분 인덱스 idx_news_visible 의 술어와 매칭되지 않는다
        // (플래너가 동치를 증명하지 못해 Seq Scan 으로 떨어진다). eq(true) 형태를 써야 한다.
        private val VISIBLE: Condition = NEWS.TRIPLE_EXTRACTED.eq(true)
    }
}
