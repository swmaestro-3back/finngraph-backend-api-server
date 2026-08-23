package com.finngraph.news

interface NewsQueryPort {

    fun findPage(page: Int, size: Int): PageResult<NewsView>

    fun findByIds(ids: List<NewsId>): Map<NewsId, NewsDetail>
}
