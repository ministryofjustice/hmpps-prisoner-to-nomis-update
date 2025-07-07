package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.retry.policy.SimpleRetryPolicy
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.sendMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.listeners.SQSMessage
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@Service
class QueueService(
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
  private val hmppsQueueService: HmppsQueueService,
) {

  fun sendMessageTrackOnFailure(queueId: String, eventType: String, message: Any, retryMaxAttempts: Int = 10) {
    val queue = hmppsQueueService.findByQueueId(queueId)!!
    runCatching {
      SQSMessage(
        Type = eventType,
        Message = message.toJson(),
      ).also {
        queue.sendMessage(
          eventType = it.Type,
          message = it.toJson(),
          retryPolicy = SimpleRetryPolicy().apply { maxAttempts = retryMaxAttempts },
        )
      }
    }.onFailure {
      // this requires manual intervention; we have retried several times and just cannot send
      telemetryClient.trackEvent("send-message-$eventType-failed", mapOf("message" to it.toJson()), null)
    }
  }
  fun sendMessageThrowOnFailure(queue: HmppsQueue, eventType: String, message: Any, retryMaxAttempts: Int = 10) = SQSMessage(
    Type = eventType,
    Message = message.toJson(),
  ).let {
    queue.sendMessage(
      eventType = it.Type,
      message = it.toJson(),
      retryPolicy = SimpleRetryPolicy().apply { maxAttempts = retryMaxAttempts },
    )
  }

  private inline fun <reified T> T.toJson(): String = objectMapper.writeValueAsString(this)
}
