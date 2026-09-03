package com.finngraph.web.user

import com.fasterxml.jackson.annotation.JsonInclude
import com.finngraph.auth.model.AuthProvider
import java.time.OffsetDateTime

@JsonInclude(JsonInclude.Include.ALWAYS)
data class MeResponse(
    val nickname: String,
    val email: String?,
    val provider: AuthProvider,
    val joinedAt: OffsetDateTime,
)
