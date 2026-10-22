package com.finngraph.web.favorite

class FavoriteLimitExceededException(val limit: Int, val count: Int) :
    RuntimeException("관심 목록은 ${limit}개까지 등록할 수 있습니다")

class FavoriteFolderLimitExceededException(val limit: Int) :
    RuntimeException("관심 폴더는 ${limit}개까지 만들 수 있습니다")

class FavoriteFolderNameDuplicateException(val name: String) :
    RuntimeException("같은 이름의 관심 폴더가 이미 있습니다: $name")
