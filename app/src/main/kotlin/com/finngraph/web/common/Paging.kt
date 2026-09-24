package com.finngraph.web.common

import com.finngraph.news.model.PageResult

fun validatePaging(page: Int, size: Int) {
    val errors = buildMap {
        if (page < 0) put("page", "must be >= 0")
        if (size < 1) put("size", "must be >= 1")
        if (size > PageResult.MAX_SIZE) put("size", "must be <= ${PageResult.MAX_SIZE}")
    }
    if (errors.isEmpty()) return

    val message =
        if (size > PageResult.MAX_SIZE) "size는 ${PageResult.MAX_SIZE} 이하여야 합니다"
        else "페이징 파라미터가 올바르지 않습니다"
    throw InvalidParameterException(message, errors)
}
