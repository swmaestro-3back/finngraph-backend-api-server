package com.finngraph.mail

import com.finngraph.composition.port.MailSenderPort
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles

class LoggingMailSender(environment: Environment) : MailSenderPort {

    private val log = LoggerFactory.getLogger(javaClass)

    init {
        require(!environment.acceptsProfiles(Profiles.of(PROD))) { "prod 프로파일에서는 LoggingMailSender를 쓸 수 없습니다" }
    }

    override fun send(to: String, subject: String, body: String) {
        log.info("mail not sent (provider=log): to={}, subject={}", to, subject)
        log.debug("mail body:\n{}", body)
    }

    private companion object {
        const val PROD = "prod"
    }
}
