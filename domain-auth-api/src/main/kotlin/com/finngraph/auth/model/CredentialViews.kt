package com.finngraph.auth.model

data class CredentialView(
    val provider: AuthProvider,
    val email: String?,
)

data class EmailCredentialView(
    val userId: Long,
    val passwordHash: String,
)
