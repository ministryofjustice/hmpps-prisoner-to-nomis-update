package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.microsoft.applicationinsights.TelemetryClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService

open class RetryQueueService(
  private val queueId: String,
  private val hmppsQueueService: HmppsQueueService,
  private val telemetryClient: TelemetryClient,
  private val queueService: QueueService,
) {
  private val queue by lazy { hmppsQueueService.findByQueueId(queueId) as HmppsQueue }

  fun sendMessage(mapping: Any, telemetryAttributes: Map<String, String>, entityName: String) {
    val result = queueService.sendMessageThrowOnFailure(queue, RETRY_CREATE_MAPPING, CreateMappingRetryMessage(mapping, telemetryAttributes, entityName))

    telemetryClient.trackEvent(
      "$queueId-create-mapping-retry",
      mapOf(
        "messageId" to result.messageId()!!,
      ) + telemetryAttributes,
    )
  }
}

data class CreateMappingRetryMessage<T>(
  val mapping: T,
  val telemetryAttributes: Map<String, String>,
  val entityName: String,
)
