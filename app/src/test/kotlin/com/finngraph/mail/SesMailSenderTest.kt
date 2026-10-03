package com.finngraph.mail

import com.finngraph.composition.port.MailDeliveryFailedException
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import software.amazon.awssdk.core.exception.SdkClientException
import software.amazon.awssdk.services.sesv2.SesV2Client
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse
import software.amazon.awssdk.services.sesv2.model.SesV2Exception
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SesMailSenderTest {

    private class RecordingSesClient(private val failure: RuntimeException? = null) : SesV2Client {
        val requests = mutableListOf<SendEmailRequest>()
        var closed = false

        override fun sendEmail(sendEmailRequest: SendEmailRequest): SendEmailResponse {
            failure?.let { throw it }
            requests += sendEmailRequest
            return SendEmailResponse.builder().messageId("message-1").build()
        }

        override fun serviceName(): String = SesV2Client.SERVICE_NAME

        override fun close() {
            closed = true
        }
    }

    @Test
    fun `발신자·수신자·제목·본문을 UTF-8 텍스트 메일로 담는다`() {
        val client = RecordingSesClient()

        SesMailSender(client, "noreply@finngraph.com").send("u@x.com", "[finngraph] 인증 코드", "본문 https://finngraph.com/verify")

        val request = client.requests.single()
        assertEquals("noreply@finngraph.com", request.fromEmailAddress())
        assertEquals(listOf("u@x.com"), request.destination().toAddresses())
        val simple = request.content().simple()
        assertEquals("[finngraph] 인증 코드", simple.subject().data())
        assertEquals("UTF-8", simple.subject().charset())
        assertEquals("본문 https://finngraph.com/verify", simple.body().text().data())
        assertEquals("UTF-8", simple.body().text().charset())
        assertNull(simple.body().html())
    }

    @Test
    fun `SES 거부와 자격 증명·네트워크 오류는 MailDeliveryFailedException으로 감싼다`() {
        val rejected = SesV2Exception.builder().message("Email address is not verified").build()
        val unreachable = SdkClientException.create("Unable to load credentials")

        listOf(rejected, unreachable).forEach { failure ->
            val thrown = assertThrows(MailDeliveryFailedException::class.java) {
                SesMailSender(RecordingSesClient(failure), "noreply@finngraph.com").send("u@x.com", "제목", "본문")
            }
            assertEquals(failure, thrown.cause)
        }
    }

    @Test
    fun `닫으면 SES 클라이언트도 닫는다`() {
        val client = RecordingSesClient()

        SesMailSender(client, "noreply@finngraph.com").close()

        assertTrue(client.closed)
    }
}
