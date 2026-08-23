package com.finngraph.news

interface NewsCompanyPort {
    val implKey: String

    fun findByNewsIds(ids: List<NewsId>): Map<NewsId, List<CompanyRef>>
}
