package uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers

fun prisonVisitUpdateMessage() = """
      {
        "Type": "Notification", 
        "MessageId": "48e8a79a-0f43-4338-bbd4-b0d745f1f8ec", 
        "Token": null, 
        "TopicArn": "arn:aws:sns:eu-west-2:000000000000:hmpps-domain-events", 
        "Message": "{\"eventType\":\"book-a-prison-visit.visit.amended\"}", 
        "SubscribeURL": null, 
        "Timestamp": "2021-03-05T11:23:56.031Z", 
        "SignatureVersion": "1", 
        "Signature": "EXAMPLEpH+..", 
        "SigningCertURL": "https://sns.us-east-1.amazonaws.com/SimpleNotificationService-0000000000000000000000.pem"}      
""".trimIndent()

fun prisonVisitCreateMessage() = """
      {
        "Type": "Notification", 
        "MessageId": "48e8a79a-0f43-4338-bbd4-b0d745f1f8ec", 
        "Token": null, 
        "TopicArn": "arn:aws:sns:eu-west-2:000000000000:hmpps-domain-events", 
        "Message": "{\"eventType\":\"book-a-prison-visit.visit.created\"}", 
        "SubscribeURL": null, 
        "Timestamp": "2021-03-05T11:23:56.031Z", 
        "SignatureVersion": "1", 
        "Signature": "EXAMPLEpH+..", 
        "SigningCertURL": "https://sns.us-east-1.amazonaws.com/SimpleNotificationService-0000000000000000000000.pem"}      
""".trimIndent()
