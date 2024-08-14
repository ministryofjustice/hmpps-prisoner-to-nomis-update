package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.future.await
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.SQSMessage
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.eventTypeMessageAttributes

open class RetryQueueService(
  private val queueId: String,
  private val hmppsQueueService: HmppsQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) {
  private val queue by lazy { hmppsQueueService.findByQueueId(queueId) as HmppsQueue }
  private val sqsClient by lazy { queue.sqsClient }
  private val queueUrl by lazy { queue.queueUrl }

  suspend fun sendMessage(mapping: Any, telemetryAttributes: Map<String, String>, entityName: String) {
    val sqsMessage = SQSMessage(
      Type = RETRY_CREATE_MAPPING,
      Message = CreateMappingRetryMessage(mapping, telemetryAttributes, entityName).toJson(),
    )
    val result = sqsClient.sendMessage(
      SendMessageRequest.builder()
        .queueUrl(queueUrl)
        .messageBody(sqsMessage.toJson())
        .eventTypeMessageAttributes(RETRY_CREATE_MAPPING)
        .build(),
    ).await()

    telemetryClient.trackEvent(
      "$queueId-create-mapping-retry",
      mapOf(
        "messageId" to result.messageId()!!,
      ) + telemetryAttributes,
    )
  }

  private inline fun <reified T> T.toJson(): String =
    objectMapper.writeValueAsString(this)
}

data class CreateMappingRetryMessage<T>(
  val mapping: T,
  val telemetryAttributes: Map<String, String>,
  val entityName: String,
)
