package com.finngraph.news.adapter

import com.finngraph.news.CompanyRef
import com.finngraph.news.NewsCompanyPort
import com.finngraph.news.NewsId
import com.finngraph.news.adapter.jooq.tables.references.NEWS_COMPANIES
import org.jooq.DSLContext
import org.springframework.stereotype.Component

@Component
class ViewNewsCompany(private val dsl: DSLContext) : NewsCompanyPort {
    override fun findByNewsIds(ids: List<NewsId>): Map<NewsId, List<CompanyRef>> {
        if (ids.isEmpty()) return emptyMap()

        val grouped: Map<Long, List<CompanyRef>> = dsl.select(
            NEWS_COMPANIES.NEWS_ID,
            NEWS_COMPANIES.COMPANY_NAME,
            NEWS_COMPANIES.TICKER,
        )
            .from(NEWS_COMPANIES)
            .where(NEWS_COMPANIES.NEWS_ID.`in`(ids.map { it.value }))
            .fetch()
            .groupBy(
                { requireNotNull(it.get(NEWS_COMPANIES.NEWS_ID)) },
                {
                    CompanyRef(
                        companyName = requireNotNull(it.get(NEWS_COMPANIES.COMPANY_NAME)),
                        ticker = it.get(NEWS_COMPANIES.TICKER),
                    )
                },
            )

        return ids.associateWith { grouped[it.value] ?: emptyList() }
    }
}
