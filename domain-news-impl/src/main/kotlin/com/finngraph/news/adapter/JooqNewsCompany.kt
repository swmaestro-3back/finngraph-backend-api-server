package com.finngraph.news.adapter

import com.finngraph.news.adapter.jooq.tables.references.COMPANIES
import com.finngraph.news.adapter.jooq.tables.references.NEWS_COMPANIES
import com.finngraph.news.model.CompanyRef
import com.finngraph.news.model.NewsId
import com.finngraph.news.port.NewsCompanyPort
import org.jooq.DSLContext
import org.springframework.stereotype.Component

@Component
class JooqNewsCompany(private val dsl: DSLContext) : NewsCompanyPort {
    override fun findByNewsIds(ids: List<NewsId>): Map<NewsId, List<CompanyRef>> {
        if (ids.isEmpty()) return emptyMap()

        val grouped: Map<Long, List<CompanyRef>> = dsl.select(
            NEWS_COMPANIES.NEWS_ID,
            COMPANIES.NAME,
            COMPANIES.TICKER,
        )
            .from(NEWS_COMPANIES)
            .join(COMPANIES).on(COMPANIES.ID.eq(NEWS_COMPANIES.COMPANY_ID))
            .where(NEWS_COMPANIES.NEWS_ID.`in`(ids.map { it.value }))
            .fetch()
            .groupBy(
                { requireNotNull(it.get(NEWS_COMPANIES.NEWS_ID)) },
                {
                    CompanyRef(
                        companyName = requireNotNull(it.get(COMPANIES.NAME)),
                        ticker = it.get(COMPANIES.TICKER),
                    )
                },
            )

        return ids.associateWith { grouped[it.value] ?: emptyList() }
    }
}
