package com.finngraph.mail

import com.finngraph.composition.port.MailDeliveryFailedException
import com.finngraph.composition.port.MailSenderPort
import software.amazon.awssdk.core.exception.SdkException
import software.amazon.awssdk.services.sesv2.SesV2Client
import software.amazon.awssdk.services.sesv2.model.Body
import software.amazon.awssdk.services.sesv2.model.Content
import software.amazon.awssdk.services.sesv2.model.Destination
import software.amazon.awssdk.services.sesv2.model.EmailContent
import software.amazon.awssdk.services.sesv2.model.Message
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest

class SesMailSender(
    private val client: SesV2Client,
    private val from: String,
) : MailSenderPort, AutoCloseable {

    override fun send(to: String, subject: String, body: String) {
        val request = SendEmailRequest.builder()
            .fromEmailAddress(from)
            .destination(Destination.builder().toAddresses(to).build())
            .content(
                EmailContent.builder()
                    .simple(
                        Message.builder()
                            .subject(utf8(subject))
                            .body(Body.builder().text(utf8(body)).build())
                            .build(),
                    )
                    .build(),
            )
            .build()
        try {
            client.sendEmail(request)
        } catch (e: SdkException) {
            throw MailDeliveryFailedException(e)
        }
    }

    override fun close() = client.close()

    private fun utf8(data: String): Content = Content.builder().data(data).charset(UTF_8).build()

    private companion object {
        const val UTF_8 = "UTF-8"
    }
}
