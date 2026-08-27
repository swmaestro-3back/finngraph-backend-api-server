package com.finngraph.news

interface NewsCompanyPort {

    fun findByNewsIds(ids: List<NewsId>): Map<NewsId, List<CompanyRef>>
}
