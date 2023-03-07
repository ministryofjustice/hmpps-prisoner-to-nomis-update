package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.SQSMessage
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Service
class VisitsUpdateQueueService(
  private val hmppsQueueService: HmppsQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) {
  private val prisonerQueue by lazy { hmppsQueueService.findByQueueId("visit") as HmppsQueue }
  private val sqsClient by lazy { prisonerQueue.sqsClient }
  private val queueUrl by lazy { prisonerQueue.queueUrl }

  fun sendMessage(context: VisitContext) {
    val sqsMessage = SQSMessage(
      Type = "RETRY",
      Message = objectMapper.writeValueAsString(context),
      MessageId = "retry-${context.vsipId}",
    )
    val result = sqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(queueUrl).messageBody(objectMapper.writeValueAsString(sqsMessage)).build(),
    ).get()

    telemetryClient.trackEvent(
      "create-visit-map-queue-retry",
      mapOf("messageId" to result.messageId()!!, "id" to context.vsipId),
    )
  }
}

data class VisitContext(val nomisId: String, val vsipId: String)
