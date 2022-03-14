package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.amazonaws.services.sqs.model.SendMessageRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.PrisonerDomainEventsListener
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Service
class UpdateQueueService(
  private val hmppsQueueService: HmppsQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) {
  private val prisonerQueue by lazy { hmppsQueueService.findByQueueId("prisoner") as HmppsQueue }
  private val prisonerSqsClient by lazy { prisonerQueue.sqsClient }
  private val prisonerQueueUrl by lazy { prisonerQueue.queueUrl }

  fun sendMessage(context: VisitContext, delaySeconds: Int = 0) {
    val result =
      prisonerSqsClient.sendMessage(
        SendMessageRequest(
          prisonerQueueUrl,
          objectMapper.writeValueAsString(
            PrisonerDomainEventsListener.SQSMessage(
              Type = "RETRY",
              Message = objectMapper.writeValueAsString(context),
              MessageId = "retry-${context.vsipId}"
            )
          )
        ).withDelaySeconds(delaySeconds)
      )

    telemetryClient.trackEvent(
      "visit-booked-create-map-queue-retry",
      mapOf("messageId" to result.messageId!!, "vsipId" to context.vsipId),
    )
  }
}

data class VisitContext(val nomisId: String, val vsipId: String)
