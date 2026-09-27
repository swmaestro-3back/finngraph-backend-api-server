package com.finngraph.support

import com.finngraph.composition.port.MailDeliveryFailedException
import com.finngraph.composition.port.MailSenderPort
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.util.concurrent.CopyOnWriteArrayList

data class CapturedMail(val to: String, val subject: String, val body: String)

class CapturingMailSender : MailSenderPort {

    val sent = CopyOnWriteArrayList<CapturedMail>()

    @Volatile
    var failNext: Boolean = false

    override fun send(to: String, subject: String, body: String) {
        if (failNext) {
            failNext = false
            throw MailDeliveryFailedException(IllegalStateException("테스트 발송 실패"))
        }
        sent += CapturedMail(to, subject, body)
    }

    fun lastTo(to: String): CapturedMail = sent.last { it.to == to }

    fun lastCodeFor(to: String): String =
        checkNotNull(CODE.find(lastTo(to).body)?.value) { "본문에 6자리 코드가 없음: ${lastTo(to).body}" }

    private companion object {
        val CODE = Regex("[0-9]{6}")
    }
}

@TestConfiguration
class CapturingMailConfig {

    @Bean
    @Primary
    fun capturingMailSender(): CapturingMailSender = CapturingMailSender()
}
