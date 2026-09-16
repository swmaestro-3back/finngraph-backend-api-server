package com.finngraph.news.adapter

import com.finngraph.news.adapter.jooq.tables.references.COMPANIES
import com.finngraph.news.adapter.jooq.tables.references.NEWS
import com.finngraph.news.adapter.jooq.tables.references.NEWS_COMPANIES
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
            .where(NEWS.ID.`in`(ids.map { it.value }).and(PROCESSED))
            .fetch { it.toNewsDetail() }
            .associateBy { NewsId(it.id) }
    }

    override fun findPageByTicker(ticker: String, page: Int, size: Int): PageResult<NewsView> =
        fetchPage(NEWS.ID.`in`(companyNewsIds(listOf(ticker))).and(PROCESSED), page, size)

    override fun findPageByCompanyTickers(tickers: List<String>, page: Int, size: Int): PageResult<NewsView> {
        if (tickers.isEmpty()) return PageResult(emptyList(), page, size, 0)
        return fetchPage(NEWS.ID.`in`(companyNewsIds(tickers)).and(PROCESSED), page, size)
    }

    private fun fetchPage(condition: Condition, page: Int, size: Int): PageResult<NewsView> {
        val rows = dsl.select(
            NEWS.ID,
            NEWS.TITLE,
            NEWS.SUMMARY,
            NEWS.LINK,
            NEWS.PUBLISHED_AT,
            NEWS.COLLECTED_AT,
            NEWS.TRIPLE_EXTRACTED,
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

    private fun companyNewsIds(tickers: List<String>): Select<Record1<Long?>> =
        dsl.select(NEWS_COMPANIES.NEWS_ID)
            .from(NEWS_COMPANIES)
            .join(COMPANIES).on(COMPANIES.ID.eq(NEWS_COMPANIES.COMPANY_ID))
            .where(COMPANIES.TICKER.`in`(tickers).and(COMPANIES.DELISTED_AT.isNull))

    private fun Record.toNewsView() = NewsView(
        id = requireNotNull(get(NEWS.ID)),
        title = get(NEWS.TITLE),
        summary = get(NEWS.SUMMARY),
        url = get(NEWS.LINK),
        publishedAt = get(NEWS.PUBLISHED_AT),
        collectedAt = get(NEWS.COLLECTED_AT),
        tripleExtracted = get(NEWS.TRIPLE_EXTRACTED),
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

        // 트리플 추출을 한 번이라도 거친 뉴스 (성공·실패 불문). 미처리(NULL) 건만 제외한다.
        private val PROCESSED: Condition = NEWS.TRIPLE_EXTRACTED.isNotNull
    }
}
