package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.SQSMessage
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Service
class ActivitiesUpdateQueueService(
  private val hmppsQueueService: HmppsQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) {
  private val queue by lazy { hmppsQueueService.findByQueueId("activity") as HmppsQueue }
  private val sqsClient by lazy { queue.sqsClient }
  private val queueUrl by lazy { queue.queueUrl }

  fun sendMessage(context: ActivityContext) {
    val sqsMessage = SQSMessage(
      Type = "RETRY",
      Message = objectMapper.writeValueAsString(context),
      MessageId = "retry-${context.activityScheduleId}"
    )
    val result = sqsClient.sendMessage(
      SendMessageRequest.builder().queueUrl(queueUrl).messageBody(objectMapper.writeValueAsString(sqsMessage)).build()
    ).get()

    telemetryClient.trackEvent(
      "create-activity-map-queue-retry",
      mapOf("messageId" to result.messageId(), "id" to context.activityScheduleId.toString()),
    )
  }
}

data class ActivityContext(val nomisCourseActivityId: Long, val activityScheduleId: Long)
