package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.future.await
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.SQSMessage
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService

abstract class UpdateQueueService<MAPPING>(
  private val hmppsQueueService: HmppsQueueService,
  internal val telemetryClient: TelemetryClient,
  internal val objectMapper: ObjectMapper,
  private val name: String,
  queueId: String,
) {
  private val queue by lazy { hmppsQueueService.findByQueueId(queueId) as HmppsQueue }
  private val sqsClient by lazy { queue.sqsClient }
  private val queueUrl by lazy { queue.queueUrl }

  suspend fun sendMessage(telemetry: Map<String, String>, message: MAPPING) {
    val sqsMessage = SQSMessage(
      Type = RETRY_CREATE_MAPPING,
      Message = CreateMappingRetryMessage(telemetry, message).toJson(),
    )
    val result = sqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(queueUrl).messageBody(sqsMessage.toJson()).build()
    ).await()

    telemetryClient.trackEvent(
      "$name-create-mapping-retry",
      telemetry +
        mapOf(
          "messageId" to result.messageId()!!,
        ),
    )
  }

  private inline fun <reified T> T.toJson(): String =
    objectMapper.writeValueAsString(this)
}

data class CreateMappingRetryMessage<MAPPING>(
  val telemetry: Map<String, String>,
  val mapping: MAPPING
)
