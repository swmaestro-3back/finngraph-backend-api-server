package com.finngraph.web

class NewsNotFoundException(val newsId: Long) :
    RuntimeException("뉴스를 찾을 수 없습니다: $newsId")

class InvalidParameterException(
    message: String,
    val fieldErrors: Map<String, String>,
) : RuntimeException(message)
