package com.finngraph.theme.port

import com.finngraph.theme.model.NewsRef
import com.finngraph.theme.model.PageResult
import com.finngraph.theme.model.ThemeName

interface ThemeNewsPort {

    fun findNews(name: ThemeName, page: Int, size: Int): PageResult<NewsRef>
}
