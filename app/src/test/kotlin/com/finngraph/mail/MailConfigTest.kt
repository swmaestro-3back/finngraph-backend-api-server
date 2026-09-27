package com.finngraph.mail

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import kotlin.test.assertIs

class MailConfigTest {

    private val config = MailConfig()

    @Test
    fun `provider=log는 LoggingMailSender를 만든다`() {
        val sender = config.mailSenderPort(MailProperties(provider = MailProvider.LOG), MockEnvironment())

        assertIs<LoggingMailSender>(sender)
    }

    @Test
    fun `prod에서 provider=log면 기동을 실패시킨다`() {
        val prod = MockEnvironment().apply { setActiveProfiles("prod") }

        assertThrows(IllegalStateException::class.java) {
            config.mailSenderPort(MailProperties(provider = MailProvider.LOG), prod)
        }
    }

    @Test
    fun `provider=smtp인데 from이나 host가 비면 기동을 실패시킨다`() {
        assertThrows(IllegalStateException::class.java) {
            config.mailSenderPort(MailProperties(provider = MailProvider.SMTP, host = "smtp.x"), MockEnvironment())
        }
        assertThrows(IllegalStateException::class.java) {
            config.mailSenderPort(MailProperties(provider = MailProvider.SMTP, from = "a@x"), MockEnvironment())
        }
    }

    @Test
    fun `provider=smtp가 완전하면 SmtpMailSender를 만든다`() {
        val properties = MailProperties(
            provider = MailProvider.SMTP,
            from = "no-reply@x",
            host = "smtp.x",
            username = "u",
            password = "p",
        )

        val sender = config.mailSenderPort(properties, MockEnvironment().apply { setActiveProfiles("prod") })

        assertIs<SmtpMailSender>(sender)
    }
}
