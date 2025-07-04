package uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers

import kotlinx.coroutines.future.await
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.eventTypeMessageAttributes

internal suspend fun SqsAsyncClient.sendMessage(queueOffenderEventsUrl: String, eventType: String, message: String) = sendMessage(
  SendMessageRequest.builder().queueUrl(queueOffenderEventsUrl)
    .messageBody(message)
    .eventTypeMessageAttributes(eventType)
    .build(),
).await()

internal suspend fun HmppsQueue.sendMessage(eventType: String, message: String) = this.sqsClient.sendMessage(this.queueUrl, eventType = eventType, message = message)
