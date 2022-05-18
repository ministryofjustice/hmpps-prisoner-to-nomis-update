package uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers

fun prisonVisitCreatedMessage(visitId: String = "12", prisonerId: String = "AB12345", occurredAt: String = "2021-03-05T11:23:56.031Z") = """
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

fun prisonVisitCancelledMessage(visitId: String = "12", prisonerId: String = "AB12345", occurredAt: String = "2021-03-05T11:23:56.031Z") = """
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

fun retryMessage() = """
      {
        "Type":"RETRY",
        "Message":"{\"nomisId\":\"12345\",\"vsipId\":\"12\"}",
        "MessageId":"retry-12"
      }
""".trimIndent()
