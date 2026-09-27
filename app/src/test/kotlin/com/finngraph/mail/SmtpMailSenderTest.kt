package com.finngraph.mail

import com.finngraph.composition.port.MailDeliveryFailedException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mail.MailSendException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSenderImpl
import kotlin.test.assertEquals

class SmtpMailSenderTest {

    private class RecordingJavaMailSender(private val failure: MailSendException? = null) : JavaMailSenderImpl() {
        val sent = mutableListOf<SimpleMailMessage>()

        override fun send(vararg simpleMessages: SimpleMailMessage) {
            failure?.let { throw it }
            sent += simpleMessages
        }
    }

    @Test
    fun `발신자·수신자·제목·본문을 담아 위임한다`() {
        val delegate = RecordingJavaMailSender()

        SmtpMailSender(delegate, "no-reply@finngraph.test").send("u@x.com", "제목", "본문")

        val message = delegate.sent.single()
        assertEquals("no-reply@finngraph.test", message.from)
        assertEquals(listOf("u@x.com"), message.to?.toList())
        assertEquals("제목", message.subject)
        assertEquals("본문", message.text)
    }

    @Test
    fun `발송 예외는 MailDeliveryFailedException으로 감싼다`() {
        val sender = SmtpMailSender(RecordingJavaMailSender(MailSendException("smtp down")), "no-reply@finngraph.test")

        val thrown = assertThrows(MailDeliveryFailedException::class.java) {
            sender.send("u@x.com", "제목", "본문")
        }
        assertEquals("smtp down", thrown.cause?.message)
    }
}
