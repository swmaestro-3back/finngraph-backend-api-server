package com.finngraph.news.adapter

import com.finngraph.news.adapter.jooq.tables.references.NEWS
import com.finngraph.news.model.NewsDetail
import com.finngraph.news.model.NewsId
import com.finngraph.news.model.NewsView
import com.finngraph.news.model.PageResult
import com.finngraph.news.port.NewsQueryPort
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Component

@Component
class JooqNewsQuery(private val dsl: DSLContext) : NewsQueryPort {
    override fun findPage(page: Int, size: Int): PageResult<NewsView> {

        val rows = dsl.select(
            NEWS.ID,
            NEWS.TITLE,
            NEWS.SUMMARY,
            NEWS.LINK,
            NEWS.PUBLISHED_AT,
        )
            .from(NEWS)
            .where(VISIBLE)
            .orderBy(NEWS.PUBLISHED_AT.desc().nullsLast(), NEWS.ID.desc())
            .limit(size)
            .offset(page.toLong() * size)
            .fetch { it.toNewsView() }

        val total = dsl.selectCount()
            .from(NEWS)
            .where(VISIBLE)
            .fetchOne(0, Long::class.javaObjectType) ?: 0L

        return PageResult(content = rows, page = page, size = size, totalElements = total)
    }

    override fun findByIds(ids: List<NewsId>): Map<NewsId, NewsDetail> {
        if (ids.isEmpty()) return emptyMap()

        return dsl.select(
            NEWS.ID,
            NEWS.TITLE,
            NEWS.SUMMARY,
            NEWS.LINK,
            NEWS.ORIGINALLINK,
            NEWS.PUBLISHED_AT,
        )
            .from(NEWS)
            .where(NEWS.ID.`in`(ids.map { it.value }))
            .fetch { it.toNewsDetail() }
            .associateBy { NewsId(it.id) }
    }

    private fun Record.toNewsView() = NewsView(
        id = requireNotNull(get(NEWS.ID)),
        title = get(NEWS.TITLE),
        summary = get(NEWS.SUMMARY),
        link = get(NEWS.LINK),
        publishedAt = get(NEWS.PUBLISHED_AT),
    )

    private fun Record.toNewsDetail() = NewsDetail(
        id = requireNotNull(get(NEWS.ID)),
        title = get(NEWS.TITLE),
        summary = get(NEWS.SUMMARY),
        link = get(NEWS.LINK),
        originallink = get(NEWS.ORIGINALLINK),
        publishedAt = get(NEWS.PUBLISHED_AT),
    )

    companion object {
        // isTrue() 가 만드는 IS TRUE 는 부분 인덱스 idx_news_visible 의 술어와 매칭되지 않는다
        // (플래너가 동치를 증명하지 못해 Seq Scan 으로 떨어진다). eq(true) 형태를 써야 한다.
        private val VISIBLE: Condition = NEWS.TRIPLE_EXTRACTED.eq(true)
    }
}
