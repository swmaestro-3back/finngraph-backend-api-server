package com.finngraph.mail

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.env.MockEnvironment
import kotlin.test.assertEquals
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
    fun `provider=ses인데 from이 비면 기동을 실패시킨다`() {
        assertThrows(IllegalStateException::class.java) {
            config.mailSenderPort(MailProperties(provider = MailProvider.SES), MockEnvironment())
        }
    }

    @Test
    fun `provider=ses는 자격 증명 없이도 SesMailSender를 만든다`() {
        val properties = MailProperties(provider = MailProvider.SES, from = "noreply@finngraph.com")

        val sender = config.mailSenderPort(properties, MockEnvironment().apply { setActiveProfiles("prod") })

        assertIs<SesMailSender>(sender)
        sender.close()
    }

    @Test
    fun `SES 리전 기본값은 서울`() {
        assertEquals("ap-northeast-2", MailProperties().region)
    }
}
