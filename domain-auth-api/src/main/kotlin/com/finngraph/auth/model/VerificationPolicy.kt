package com.finngraph.auth.model

import java.time.Duration

data class CodePolicy(
    val codeTtl: Duration,
    val maxAttempts: Int,
    val verifiedTtl: Duration,
) {
    init {
        require(!codeTtl.isZero && !codeTtl.isNegative) { "codeTtl은 양수여야 합니다" }
        require(maxAttempts > 0) { "maxAttempts는 양수여야 합니다" }
        require(!verifiedTtl.isZero && !verifiedTtl.isNegative) { "verifiedTtl은 양수여야 합니다" }
    }
}

data class SendRatePolicy(
    val cooldown: Duration,
    val emailLimit: Int,
    val emailWindow: Duration,
    val globalLimit: Int,
    val globalWindow: Duration,
) {
    init {
        require(!cooldown.isNegative) { "cooldown은 음수일 수 없습니다" }
        require(emailLimit > 0) { "emailLimit은 양수여야 합니다" }
        require(globalLimit > 0) { "globalLimit은 양수여야 합니다" }
        require(!emailWindow.isZero && !emailWindow.isNegative) { "emailWindow는 양수여야 합니다" }
        require(!globalWindow.isZero && !globalWindow.isNegative) { "globalWindow는 양수여야 합니다" }
    }
}

data class ConfirmRatePolicy(
    val ipLimit: Int,
    val ipWindow: Duration,
) {
    init {
        require(ipLimit > 0) { "ipLimit은 양수여야 합니다" }
        require(!ipWindow.isZero && !ipWindow.isNegative) { "ipWindow는 양수여야 합니다" }
    }
}
