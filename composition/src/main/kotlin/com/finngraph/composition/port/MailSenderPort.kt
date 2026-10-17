package com.finngraph.composition.port

class MailDeliveryFailedException(cause: Throwable) : RuntimeException("메일 발송에 실패했습니다", cause)

interface MailSenderPort {
    fun send(to: String, subject: String, body: String)
}
