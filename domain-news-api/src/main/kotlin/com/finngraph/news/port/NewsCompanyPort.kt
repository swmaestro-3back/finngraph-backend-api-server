package com.finngraph.news.port

import com.finngraph.news.model.CompanyRef
import com.finngraph.news.model.NewsId

interface NewsCompanyPort {

    fun findByNewsIds(ids: List<NewsId>): Map<NewsId, List<CompanyRef>>
}
