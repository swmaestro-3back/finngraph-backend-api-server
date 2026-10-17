package com.finngraph.web.favorite

class FavoriteLimitExceededException(val limit: Int, val count: Int) :
    RuntimeException("관심 목록은 ${limit}개까지 등록할 수 있습니다")
