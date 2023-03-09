package uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

fun prisonVisitMessagePayload(
  eventType: String,
  visitId: String = "12",
  prisonerId: String = "AB12345",
  occurredAt: String = "2021-03-05T11:23:56.031Z",
) =
  """{"eventType":"$eventType", "prisonerId": "$prisonerId", "occurredAt": "$occurredAt", "additionalInformation": {"reference": "$visitId","visitType": "STANDARD_SOCIAL"}}"""

fun prisonVisitCreatedMessage(
  visitId: String = "12",
  prisonerId: String = "AB12345",
  occurredAt: String = "2021-03-05T11:23:56.031Z",
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

fun retryVisitsCreateMappingMessage() = """
      {
        "Type":"RETRY_CREATE_MAPPING",
        "Message":"{\"mapping\": {\"nomisId\":\"12345\",\"vsipId\":\"12\"}}"
      }
""".trimIndent()

fun incentiveMessagePayload(incentiveId: Long) =
  """{"eventType":"incentives.iep-review.inserted", "additionalInformation": {"id":"$incentiveId"}}"""

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
        "Type":"RETRY_CREATE_MAPPING",
        "Message":"{\"mapping\": {\"nomisBookingId\":12345,\"nomisIncentiveSequence\":2,\"incentiveId\":15}}"
      }
""".trimIndent()

fun activityMessagePayload(eventType: String, scheduleId: Long) =
  """{"eventType":"$eventType", "additionalInformation": { "activityScheduleId": "$scheduleId" }, "version": "1.0", "description": "description", "occurredAt": "2021-03-05T11:23:56.031Z"}"""

fun activityCreatedMessage(identifier: Long) = """
      {
        "Type": "Notification", 
        "MessageId": "48e8a79a-0f43-4338-bbd4-b0d745f1f8ec", 
        "Token": null, 
        "TopicArn": "arn:aws:sns:eu-west-2:000000000000:hmpps-domain-events", 
        "Message": "{\"eventType\":\"activities.activity-schedule.created\", \"additionalInformation\": { \"activityScheduleId\": \"$identifier\" }, \"version\": \"1.0\", \"description\": \"description\", \"occurredAt\": \"2021-03-05T11:23:56.031Z\"}",
        "SubscribeURL": null, 
        "Timestamp": "2021-03-05T11:23:56.031Z", 
        "SignatureVersion": "1", 
        "Signature": "EXAMPLEpH+..", 
        "SigningCertURL": "https://sns.us-east-1.amazonaws.com/SimpleNotificationService-0000000000000000000000.pem"}      
""".trimIndent()

fun activityRetryMessage() = """
      {
        "Type":"RETRY_CREATE_MAPPING",
        "Message":"{\"mapping\": {\"activityScheduleId\":12345,\"nomisCourseActivityId\":15}, \"telemetryAttributes\": {}}"
      }
""".trimIndent()

fun allocationMessagePayload(eventType: String, scheduleId: Long, allocationId: Long) =
  """{"eventType":"$eventType",
    "version": "1.0", "description": "description", "occurredAt": "2021-03-05T11:23:56.031Z",
    "additionalInformation": {
        "scheduleId"  : "$scheduleId",
        "allocationId": "$allocationId"
      }
    }
  """.trimMargin()

fun objectMapper(): ObjectMapper {
  return ObjectMapper()
    .setSerializationInclusion(JsonInclude.Include.NON_NULL)
    .registerModule(JavaTimeModule())
    .registerKotlinModule()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}
