package com.finngraph.mail

import com.finngraph.composition.port.MailDeliveryFailedException
import com.finngraph.composition.port.MailSenderPort
import org.springframework.mail.MailException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

class SmtpMailSender(
    private val delegate: JavaMailSender,
    private val from: String,
) : MailSenderPort {

    override fun send(to: String, subject: String, body: String) {
        val message = SimpleMailMessage().apply {
            this.from = this@SmtpMailSender.from
            setTo(to)
            this.subject = subject
            text = body
        }
        try {
            delegate.send(message)
        } catch (e: MailException) {
            throw MailDeliveryFailedException(e)
        }
    }
}
