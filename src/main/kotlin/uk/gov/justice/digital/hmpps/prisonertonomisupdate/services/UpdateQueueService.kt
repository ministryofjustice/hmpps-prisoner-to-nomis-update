package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.amazonaws.services.sqs.model.SendMessageRequest
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
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

  fun sendMessage(context: Context) {
    val sqsMessage = PrisonerDomainEventsListener.SQSMessage(
      Type = "RETRY",
      Message = objectMapper.writeValueAsString(context),
      MessageId = "retry-${context.getId()}"
    )
    val result = prisonerSqsClient.sendMessage(
      SendMessageRequest(prisonerQueueUrl, objectMapper.writeValueAsString(sqsMessage))
    )

    telemetryClient.trackEvent(
      "create-map-queue-retry",
      mapOf("messageId" to result.messageId!!, "id" to context.getId()),
    )
  }
}

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  include = JsonTypeInfo.As.PROPERTY,
  property = "type",
  defaultImpl = VisitContext::class
)
@JsonSubTypes(
  JsonSubTypes.Type(value = VisitContext::class, name = "VISIT"),
  JsonSubTypes.Type(value = IncentiveContext::class, name = "INCENTIVE")
)
abstract class Context(val type: String) {
  abstract fun getId(): String
}

data class VisitContext(
  val nomisId: String,
  val vsipId: String
) : Context("VISIT") {
  override fun getId() = vsipId
}

data class IncentiveContext(
  val nomisBookingId: Long,
  val nomisIncentiveSequence: Int,
  val incentiveId: Long
) : Context("INCENTIVE") {
  override fun getId() = incentiveId.toString()
}
