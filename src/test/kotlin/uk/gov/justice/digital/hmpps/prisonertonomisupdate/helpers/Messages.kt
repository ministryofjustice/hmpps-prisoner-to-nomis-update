package uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers

import com.amazonaws.services.sqs.AmazonSQS
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.slf4j.Logger
import org.slf4j.LoggerFactory

fun prisonVisitCreatedMessage(
  visitId: String = "12",
  prisonerId: String = "AB12345",
  occurredAt: String = "2021-03-05T11:23:56.031Z"
) = """
      {
        "Type": "Notification", 
        "MessageId": "48e8a79a-0f43-4338-bbd4-b0d745f1f8ec", 
        "Token": null, 
        "TopicArn": "arn:aws:sns:eu-west-2:000000000000:hmpps-domain-events", 
        "Message": "{\"eventType\":\"prison-visit.booked\", \"prisonerId\": \"$prisonerId\", \"occurredAt\": \"$occurredAt\", \"additionalInformation\": {\"reference\": \"$visitId\",\"visitType\": \"STANDARD_SOCIAL\"}}", 
        "SubscribeURL": null, 
        "Timestamp": "2021-03-05T11:23:56.031Z", 
        "SignatureVersion": "1", 
        "Signature": "EXAMPLEpH+..", 
        "SigningCertURL": "https://sns.us-east-1.amazonaws.com/SimpleNotificationService-0000000000000000000000.pem"}      
""".trimIndent()

fun prisonVisitCancelledMessage(
  visitId: String = "12",
  prisonerId: String = "AB12345",
  occurredAt: String = "2021-03-05T11:23:56.031Z"
) = """
      {
        "Type": "Notification", 
        "MessageId": "48e8a79a-0f43-4338-bbd4-b0d745f1f8ec", 
        "Token": null, 
        "TopicArn": "arn:aws:sns:eu-west-2:000000000000:hmpps-domain-events", 
        "Message": "{\"eventType\":\"prison-visit.cancelled\", \"prisonerId\": \"$prisonerId\", \"occurredAt\": \"$occurredAt\", \"additionalInformation\": {\"reference\": \"$visitId\",\"visitType\": \"STANDARD_SOCIAL\"}}", 
        "SubscribeURL": null, 
        "Timestamp": "2021-03-05T11:23:56.031Z", 
        "SignatureVersion": "1", 
        "Signature": "EXAMPLEpH+..", 
        "SigningCertURL": "https://sns.us-east-1.amazonaws.com/SimpleNotificationService-0000000000000000000000.pem"}      
""".trimIndent()

fun prisonVisitChangedMessage(
  visitId: String = "12",
  prisonerId: String = "AB12345",
  occurredAt: String = "2021-03-05T11:23:56.031Z"
) = """
      {
        "Type": "Notification", 
        "MessageId": "48e8a79a-0f43-4338-bbd4-b0d745f1f8ec", 
        "Token": null, 
        "TopicArn": "arn:aws:sns:eu-west-2:000000000000:hmpps-domain-events", 
        "Message": "{\"eventType\":\"prison-visit.changed\", \"prisonerId\": \"$prisonerId\", \"occurredAt\": \"$occurredAt\", \"additionalInformation\": {\"reference\": \"$visitId\"}}", 
        "SubscribeURL": null, 
        "Timestamp": "2021-03-05T11:23:56.031Z", 
        "SignatureVersion": "1", 
        "Signature": "EXAMPLEpH+..", 
        "SigningCertURL": "https://sns.us-east-1.amazonaws.com/SimpleNotificationService-0000000000000000000000.pem"}      
""".trimIndent()

fun retryMessage() = """
      {
        "Type":"RETRY",
        "Message":"{\"type\":\"VISIT\",\"nomisId\":\"12345\",\"vsipId\":\"12\"}",
        "MessageId":"retry-12"
      }
""".trimIndent()

fun incentiveCreatedMessage(incentiveId: Long) = """
      {
        "Type": "Notification", 
        "MessageId": "48e8a79a-0f43-4338-bbd4-b0d745f1f8ec", 
        "Token": null, 
        "TopicArn": "arn:aws:sns:eu-west-2:000000000000:hmpps-domain-events", 
        "Message": "{\"eventType\":\"incentives.iep-review.inserted\", \"additionalInformation\": {\"id\":\"$incentiveId\"}}",
        "SubscribeURL": null, 
        "Timestamp": "2021-03-05T11:23:56.031Z", 
        "SignatureVersion": "1", 
        "Signature": "EXAMPLEpH+..", 
        "SigningCertURL": "https://sns.us-east-1.amazonaws.com/SimpleNotificationService-0000000000000000000000.pem"}      
""".trimIndent()

fun incentiveRetryMessage() = """
      {
        "Type":"RETRY",
        "Message":"{\"type\":\"INCENTIVE\",\"nomisBookingId\":12345,\"nomisIncentiveSequence\":2,\"incentiveId\":15}",
        "MessageId":"retry-15"
      }
""".trimIndent()

private object logger {
  val log: Logger = LoggerFactory.getLogger(this::class.java)
}

fun getNumberOfMessagesCurrentlyOnQueue(awsSqsClient: AmazonSQS, url: String): Int? {
  val queueAttributes = awsSqsClient.getQueueAttributes(url, listOf("ApproximateNumberOfMessages"))
  val messagesOnQueue = queueAttributes.attributes["ApproximateNumberOfMessages"]?.toInt()
  logger.log.info("Number of messages on $url: $messagesOnQueue")
  return messagesOnQueue
}

fun objectMapper(): ObjectMapper {
  return ObjectMapper()
    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
    .registerModule(JavaTimeModule())
    .registerKotlinModule()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}
