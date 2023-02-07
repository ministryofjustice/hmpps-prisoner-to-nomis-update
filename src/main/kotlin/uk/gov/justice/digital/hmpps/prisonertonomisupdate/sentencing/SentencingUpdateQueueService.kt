package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.future.await
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.SQSMessage
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService

const val RETRY_CREATE_MAPPING = "RETRY_CREATE_MAPPING"

@Service
class SentencingUpdateQueueService(
  private val hmppsQueueService: HmppsQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) {
  private val queue by lazy { hmppsQueueService.findByQueueId("sentencing") as HmppsQueue }
  private val sqsClient by lazy { queue.sqsClient }
  private val queueUrl by lazy { queue.queueUrl }

  suspend fun sendMessage(offenderNo: String, message: SentencingAdjustmentMappingDto) {
    val sqsMessage = SQSMessage(
      Type = RETRY_CREATE_MAPPING,
      Message = SentencingAdjustmentCreateMappingRetryMessage(offenderNo, message).toJson(),
    )
    val result = sqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(queueUrl).messageBody(sqsMessage.toJson()).build()
    ).await()

    telemetryClient.trackEvent(
      "sentencing-adjustment-create-mapping-retry",
      mapOf(
        "messageId" to result.messageId()!!,
        "nomisAdjustmentId" to message.nomisAdjustmentId.toString(),
        "sentenceAdjustmentId" to message.adjustmentId,
      ),
    )
  }

  private inline fun <reified T> T.toJson(): String =
    objectMapper.writeValueAsString(this)
}

data class SentencingAdjustmentCreateMappingRetryMessage(
  val offenderNo: String,
  val mapping: SentencingAdjustmentMappingDto
)
