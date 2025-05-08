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
) = """{"eventType":"$eventType", "prisonerId": "$prisonerId", "occurredAt": "$occurredAt", "additionalInformation": {"reference": "$visitId","visitType": "STANDARD_SOCIAL"}}"""

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

fun incentiveMessagePayload(incentiveId: Long) = """{"eventType":"incentives.iep-review.inserted", "additionalInformation": {"id":"$incentiveId"}}"""

fun incentiveLevelChangedMessagePayload(incentiveLevel: String) = """{"eventType":"incentives.level.changed", "additionalInformation": {"incentiveLevel":"$incentiveLevel"}}"""

fun incentiveLevelsReorderedMessagePayload() = """{"eventType":"incentives.levels.reordered", "additionalInformation": {}}"""

fun incentivePrisonLevelChangedMessagePayload(prisonId: String, incentiveLevel: String) = """{"eventType":"incentives.prison-level.changed", "additionalInformation": {"incentiveLevel":"$incentiveLevel", "prisonId":"$prisonId"}}"""

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

fun activityMessagePayload(eventType: String, scheduleId: Long) = """{"eventType":"$eventType", "additionalInformation": { "activityScheduleId": "$scheduleId" }, "version": "1.0", "description": "description", "occurredAt": "2021-03-05T11:23:56.031Z"}"""

fun scheduledInstanceMessagePayload(eventType: String, scheduledInstanceId: Long) = """{"eventType":"$eventType", "additionalInformation": { "scheduledInstanceId": "$scheduledInstanceId" }, "version": "1.0", "description": "description", "occurredAt": "2021-03-05T11:23:56.031Z"}"""

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

fun allocationMessagePayload(eventType: String, scheduleId: Long, allocationId: Long) = """{"eventType":"$eventType",
    "version": "1.0", "description": "description", "occurredAt": "2021-03-05T11:23:56.031Z",
    "additionalInformation": {
        "scheduleId"  : "$scheduleId",
        "allocationId": "$allocationId"
      }
    }
""".trimMargin()

fun attendanceMessagePayload(eventType: String, attendanceId: Long) = """{"eventType":"$eventType",
    "version": "1.0", "description": "description", "occurredAt": "2021-03-05T11:23:56.031Z",
    "additionalInformation": {
        "attendanceId"  : "$attendanceId"
      }
    }
""".trimMargin()

fun attendanceDeletedMessagePayload(eventType: String, scheduledInstanceId: Long, bookingId: Long) = """{"eventType":"$eventType",
    "version": "1.0", "description": "description", "occurredAt": "2021-03-05T11:23:56.031Z",
    "additionalInformation": {
        "scheduledInstanceId"  : "$scheduledInstanceId",
        "bookingId"  : "$bookingId"
      }
    }
""".trimMargin()

fun objectMapper(): ObjectMapper = ObjectMapper()
  .setSerializationInclusion(JsonInclude.Include.NON_NULL)
  .registerModule(JavaTimeModule())
  .registerKotlinModule()
  .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

fun bookingMovedDomainEvent(
  eventType: String = "prison-offender-events.prisoner.booking.moved",
  bookingId: Long = 1234567,
  movedToNomsNumber: String = "A1234KT",
  movedFromNomsNumber: String = "A1000KT",
  bookingStartDateTime: String = "2019-10-21T15:00:25.489964",
  occurredAt: String = "2024-10-21T15:00:25.000Z",
) = //language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\", \"description\": \"some desc\", \"additionalInformation\": {\"movedToNomsNumber\":\"$movedToNomsNumber\", \"movedFromNomsNumber\":\"$movedFromNomsNumber\", \"bookingId\":\"$bookingId\", \"bookingStartDateTime\":\"$bookingStartDateTime\"}}",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun mergeDomainEvent(
  eventType: String = "prison-offender-events.prisoner.merged",
  bookingId: Long = 1234567,
  offenderNo: String = "A1234KT",
  removedOffenderNo: String = "A1000KT",
  occurredAt: String = "2024-10-21T15:00:25.000Z",
) = //language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\", \"description\": \"some desc\", \"additionalInformation\": {\"nomsNumber\":\"$offenderNo\", \"removedNomsNumber\":\"$removedOffenderNo\", \"bookingId\":\"$bookingId\", \"reason\":\"MERGE\"}}",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()

fun prisonerReceivedDomainEvent(
  eventType: String = "prisoner-offender-search.prisoner.received",
  offenderNo: String = "A1234KT",
  reason: String = "READMISSION_SWITCH_BOOKING",
) = //language=JSON
  """{
    "MessageId": "ae06c49e-1f41-4b9f-b2f2-dcca610d02cd", "Type": "Notification", "Timestamp": "2019-10-21T14:01:18.500Z", 
    "Message": "{\"eventType\":\"$eventType\", \"description\": \"some desc\", \"additionalInformation\": {\"nomsNumber\":\"$offenderNo\", \"reason\":\"$reason\"}}",
    "TopicArn": "arn:aws:sns:eu-west-1:000000000000:offender_events", 
    "MessageAttributes": {
      "eventType": {"Type": "String", "Value": "$eventType"}, 
      "id": {"Type": "String", "Value": "8b07cbd9-0820-0a0f-c32f-a9429b618e0b"}, 
      "contentType": {"Type": "String", "Value": "text/plain;charset=UTF-8"}, 
      "timestamp": {"Type": "Number.java.lang.Long", "Value": "1571666478344"}
    }
}
  """.trimIndent()
