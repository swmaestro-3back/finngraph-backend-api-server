package com.finngraph.theme.adapter

import com.finngraph.theme.adapter.jooq.tables.references.NEWS
import com.finngraph.theme.adapter.jooq.tables.references.NEWS_COMPANIES
import com.finngraph.theme.adapter.jooq.tables.references.STOCKS
import com.finngraph.theme.adapter.jooq.tables.references.THEMES
import com.finngraph.theme.adapter.jooq.tables.references.THEME_STOCKS
import com.finngraph.theme.model.NewsRef
import com.finngraph.theme.model.PageResult
import com.finngraph.theme.model.ThemeName
import com.finngraph.theme.port.ThemeNewsPort
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Select
import org.jooq.impl.DSL
import org.springframework.stereotype.Component

@Component
class JooqThemeNews(private val dsl: DSLContext) : ThemeNewsPort {

    override fun findNews(name: ThemeName, page: Int, size: Int): PageResult<NewsRef> {
        val visible = NEWS.ID.`in`(relatedNewsIds(name)).and(NEWS_VISIBLE)

        val rows = dsl.select(
            NEWS.ID,
            NEWS.TITLE,
            NEWS.SUMMARY,
            NEWS.LINK,
            NEWS.PUBLISHED_AT,
            NEWS.COLLECTED_AT,
        )
            .from(NEWS)
            .where(visible)
            .orderBy(NEWS.PUBLISHED_AT.desc().nullsLast(), NEWS.ID.desc())
            .limit(size)
            .offset(page.toLong() * size)
            .fetch {
                NewsRef(
                    id = requireNotNull(it.get(NEWS.ID)),
                    title = it.get(NEWS.TITLE),
                    summary = it.get(NEWS.SUMMARY),
                    url = it.get(NEWS.LINK),
                    publishedAt = it.get(NEWS.PUBLISHED_AT),
                    collectedAt = it.get(NEWS.COLLECTED_AT),
                )
            }

        val total = dsl.selectCount()
            .from(NEWS)
            .where(visible)
            .fetchOne(0, Long::class.javaObjectType) ?: 0L

        return PageResult(content = rows, page = page, size = size, totalElements = total)
    }

    private fun relatedNewsIds(name: ThemeName): Select<Record1<Long?>> =
        DSL.select(NEWS_COMPANIES.NEWS_ID)
            .from(NEWS_COMPANIES)
            .join(STOCKS).on(STOCKS.COMPANY_ID.eq(NEWS_COMPANIES.COMPANY_ID))
            .join(THEME_STOCKS).on(THEME_STOCKS.STOCK_ID.eq(STOCKS.ID))
            .join(THEMES).on(THEMES.ID.eq(THEME_STOCKS.THEME_ID))
            .where(THEMES.NAME.eq(name.value))

    companion object {
        private val NEWS_VISIBLE: Condition =
            NEWS.IS_PROCESSED.eq(true).and(NEWS.RELATION_EXTRACTED.eq(true))
    }
}
