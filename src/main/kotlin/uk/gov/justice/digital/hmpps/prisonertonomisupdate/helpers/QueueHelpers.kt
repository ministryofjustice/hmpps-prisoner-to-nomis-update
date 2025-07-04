package uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers

import org.slf4j.LoggerFactory
import org.springframework.retry.RetryPolicy
import org.springframework.retry.backoff.BackOffPolicy
import org.springframework.retry.backoff.ExponentialBackOffPolicy
import org.springframework.retry.policy.SimpleRetryPolicy
import org.springframework.retry.support.RetryTemplate
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import software.amazon.awssdk.services.sqs.model.SendMessageResponse
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.eventTypeMessageAttributes

val DEFAULT_RETRY_POLICY: RetryPolicy = SimpleRetryPolicy().apply { maxAttempts = 4 }
val DEFAULT_BACKOFF_POLICY: BackOffPolicy = ExponentialBackOffPolicy().apply { initialInterval = 1000L }
private val log = LoggerFactory.getLogger(HmppsQueue::class.java)

internal fun SqsAsyncClient.sendMessage(queueUrl: String, eventType: String, message: String): SendMessageResponse {
  val retryTemplate = RetryTemplate().apply {
    setRetryPolicy(DEFAULT_RETRY_POLICY)
    setBackOffPolicy(DEFAULT_BACKOFF_POLICY)
  }

  return runCatching {
    retryTemplate.execute<SendMessageResponse, Exception> {
      sendMessage(
        SendMessageRequest.builder().queueUrl(queueUrl)
          .messageBody(message)
          .eventTypeMessageAttributes(eventType)
          .build(),
      ).get()
    }
  }.onFailure {
    log.error("""Unable to send message {} with body "{}"""", eventType, message)
  }.getOrThrow()
}

internal fun HmppsQueue.sendMessage(eventType: String, message: String) = this.sqsClient.sendMessage(this.queueUrl, eventType = eventType, message = message)
