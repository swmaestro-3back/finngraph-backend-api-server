package com.finngraph.user.model

import java.time.OffsetDateTime

data class UserView(
    val id: Long,
    val nickname: String,
    val createdAt: OffsetDateTime,
)
