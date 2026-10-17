package com.finngraph.web.user

data class NicknameUpdateRequest(val nickname: String?)

data class PasswordChangeRequest(
    val currentPassword: String?,
    val newPassword: String?,
)

data class WithdrawalRequest(val password: String?)
