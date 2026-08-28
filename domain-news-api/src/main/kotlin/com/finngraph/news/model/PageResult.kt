package com.finngraph.news.model

data class PageResult<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
) {
    val totalPages: Int
        get() = if (size <= 0) 0 else ((totalElements + size - 1) / size).toInt()

    companion object {
        const val DEFAULT_PAGE: Int = 0
        const val DEFAULT_SIZE: Int = 20

        const val MAX_SIZE: Int = 100
    }
}
