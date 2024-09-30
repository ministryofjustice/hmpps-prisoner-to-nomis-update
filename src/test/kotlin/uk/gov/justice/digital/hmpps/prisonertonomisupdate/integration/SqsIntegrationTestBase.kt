package uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration

import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsTopic

abstract class SqsIntegrationTestBase : IntegrationTestBase() {

  @Autowired
  private lateinit var hmppsQueueService: HmppsQueueService

  internal val topic by lazy { hmppsQueueService.findByTopicId("domainevents") as HmppsTopic }

  internal val visitQueue by lazy { hmppsQueueService.findByQueueId("visit") as HmppsQueue }

  internal val awsSqsVisitClient by lazy { visitQueue.sqsClient }
  internal val awsSqsVisitDlqClient by lazy { visitQueue.sqsDlqClient }
  internal val visitQueueUrl by lazy { visitQueue.queueUrl }
  internal val visitDlqUrl by lazy { visitQueue.dlqUrl }

  internal val incentiveQueue by lazy { hmppsQueueService.findByQueueId("incentive") as HmppsQueue }

  internal val awsSqsIncentiveClient by lazy { incentiveQueue.sqsClient }
  internal val awsSqsIncentiveDlqClient by lazy { incentiveQueue.sqsDlqClient }
  internal val incentiveQueueUrl by lazy { incentiveQueue.queueUrl }
  internal val incentiveDlqUrl by lazy { incentiveQueue.dlqUrl }

  internal val activityQueue by lazy { hmppsQueueService.findByQueueId("activity") as HmppsQueue }

  internal val awsSqsActivityClient by lazy { activityQueue.sqsClient }
  internal val awsSqsActivityDlqClient by lazy { activityQueue.sqsDlqClient as SqsAsyncClient }
  internal val activityQueueUrl by lazy { activityQueue.queueUrl }
  internal val activityDlqUrl by lazy { activityQueue.dlqUrl as String }

  internal val appointmentQueue by lazy { hmppsQueueService.findByQueueId("appointment") as HmppsQueue }

  internal val awsSqsAppointmentClient by lazy { appointmentQueue.sqsClient }
  internal val awsSqsAppointmentDlqClient by lazy { appointmentQueue.sqsDlqClient }
  internal val appointmentQueueUrl by lazy { appointmentQueue.queueUrl }
  internal val appointmentDlqUrl by lazy { appointmentQueue.dlqUrl }

  internal val nonAssociationQueue by lazy { hmppsQueueService.findByQueueId("nonassociation") as HmppsQueue }

  internal val awsSqsNonAssociationClient by lazy { nonAssociationQueue.sqsClient }
  internal val awsSqsNonAssociationDlqClient by lazy { nonAssociationQueue.sqsDlqClient }
  internal val nonAssociationQueueUrl by lazy { nonAssociationQueue.queueUrl }
  internal val nonAssociationDlqUrl by lazy { nonAssociationQueue.dlqUrl }

  internal val locationQueue by lazy { hmppsQueueService.findByQueueId("location") as HmppsQueue }

  internal val awsSqsLocationClient by lazy { locationQueue.sqsClient }
  internal val awsSqsLocationDlqClient by lazy { locationQueue.sqsDlqClient }
  internal val locationQueueUrl by lazy { locationQueue.queueUrl }
  internal val locationDlqUrl by lazy { locationQueue.dlqUrl }

  internal val sentencingQueue by lazy { hmppsQueueService.findByQueueId("sentencing") as HmppsQueue }

  internal val awsSqsSentencingClient by lazy { sentencingQueue.sqsClient }
  internal val awsSqsSentencingDlqClient by lazy { sentencingQueue.sqsDlqClient }
  internal val sentencingQueueUrl by lazy { sentencingQueue.queueUrl }
  internal val sentencingDlqUrl by lazy { sentencingQueue.dlqUrl }

  internal val adjudicationQueue by lazy { hmppsQueueService.findByQueueId("adjudication") as HmppsQueue }

  internal val awsSqsAdjudicationClient by lazy { adjudicationQueue.sqsClient }
  internal val awsSqsAdjudicationDlqClient by lazy { adjudicationQueue.sqsDlqClient }
  internal val adjudicationQueueUrl by lazy { adjudicationQueue.queueUrl }
  internal val adjudicationDlqUrl by lazy { adjudicationQueue.dlqUrl }

  internal val courtSentencingQueue by lazy { hmppsQueueService.findByQueueId("courtsentencing") as HmppsQueue }

  internal val awsSqsCourtSentencingClient by lazy { courtSentencingQueue.sqsClient }
  internal val awsSqsCourtSentencingDlqClient by lazy { courtSentencingQueue.sqsDlqClient }
  internal val courtSentencingQueueUrl by lazy { courtSentencingQueue.queueUrl }
  internal val courtSentencingDlqUrl by lazy { courtSentencingQueue.dlqUrl }

  internal val alertsQueue by lazy { hmppsQueueService.findByQueueId("alerts") as HmppsQueue }

  internal val awsSqsAlertsClient by lazy { alertsQueue.sqsClient }
  internal val awsSqsAlertsDlqClient by lazy { alertsQueue.sqsDlqClient }
  internal val alertsQueueUrl by lazy { alertsQueue.queueUrl }
  internal val alertsDlqUrl by lazy { alertsQueue.dlqUrl }

  internal val caseNotesQueue by lazy { hmppsQueueService.findByQueueId("casenotes") as HmppsQueue }

  internal val awsSqsCaseNotesClient by lazy { caseNotesQueue.sqsClient }
  internal val awsSqsCaseNotesDlqClient by lazy { caseNotesQueue.sqsDlqClient }
  internal val caseNotesQueueUrl by lazy { caseNotesQueue.queueUrl }
  internal val caseNotesDlqUrl by lazy { caseNotesQueue.dlqUrl }

  internal val csipQueue by lazy { hmppsQueueService.findByQueueId("csip") as HmppsQueue }

  internal val awsSqsCSIPClient by lazy { csipQueue.sqsClient }
  internal val awsSqsCSIPDlqClient by lazy { csipQueue.sqsDlqClient }
  internal val csipQueueUrl by lazy { csipQueue.queueUrl }
  internal val csipDlqUrl by lazy { csipQueue.dlqUrl }

  internal val prisonPersonQueue by lazy { hmppsQueueService.findByQueueId("prisonperson") as HmppsQueue }
  internal val prisonPersonQueueClient by lazy { prisonPersonQueue.sqsClient }
  internal val prisonPersonDlqClient by lazy { prisonPersonQueue.sqsDlqClient }
  internal val prisonPersonQueueUrl by lazy { prisonPersonQueue.queueUrl }
  internal val prisonPersonDlqUrl by lazy { prisonPersonQueue.dlqUrl }

  internal val awsSnsClient by lazy { topic.snsClient }
  internal val topicArn by lazy { topic.arn }

  @BeforeEach
  fun cleanQueue() {
    Mockito.reset(telemetryClient)
    awsSqsVisitClient.purgeQueue(visitQueueUrl).get()
    awsSqsVisitDlqClient?.purgeQueue(visitDlqUrl)?.get()

    awsSqsIncentiveClient.purgeQueue(incentiveQueueUrl).get()
    awsSqsIncentiveDlqClient?.purgeQueue(incentiveDlqUrl)?.get()

    awsSqsActivityClient.purgeQueue(activityQueueUrl).get()
    awsSqsActivityDlqClient.purgeQueue(activityDlqUrl)?.get()

    awsSqsAppointmentClient.purgeQueue(appointmentQueueUrl).get()
    awsSqsAppointmentDlqClient?.purgeQueue(appointmentDlqUrl)?.get()

    awsSqsNonAssociationClient.purgeQueue(nonAssociationQueueUrl).get()
    awsSqsNonAssociationDlqClient?.purgeQueue(nonAssociationDlqUrl)?.get()

    awsSqsLocationClient.purgeQueue(locationQueueUrl).get()
    awsSqsLocationDlqClient?.purgeQueue(locationDlqUrl)?.get()

    awsSqsSentencingClient.purgeQueue(sentencingQueueUrl).get()
    awsSqsSentencingDlqClient?.purgeQueue(sentencingDlqUrl)?.get()

    awsSqsAdjudicationClient.purgeQueue(adjudicationQueueUrl).get()
    awsSqsAdjudicationDlqClient?.purgeQueue(adjudicationDlqUrl)?.get()

    awsSqsCourtSentencingClient.purgeQueue(courtSentencingQueueUrl).get()
    awsSqsCourtSentencingDlqClient?.purgeQueue(courtSentencingDlqUrl)?.get()

    awsSqsAlertsClient.purgeQueue(alertsQueueUrl).get()
    awsSqsAlertsDlqClient?.purgeQueue(alertsDlqUrl)?.get()

    awsSqsCaseNotesClient.purgeQueue(caseNotesQueueUrl).get()
    awsSqsCaseNotesDlqClient?.purgeQueue(caseNotesDlqUrl)?.get()

    awsSqsCSIPClient.purgeQueue(csipQueueUrl).get()
    awsSqsCSIPDlqClient?.purgeQueue(csipDlqUrl)?.get()

    prisonPersonQueueClient.purgeQueue(prisonPersonQueueUrl).get()
    prisonPersonDlqClient?.purgeQueue(prisonPersonDlqUrl)?.get()
  }

  internal fun waitForAnyProcessingToComplete(times: Int = 1) {
    await untilAsserted { verify(telemetryClient, times(times)).trackEvent(any(), any(), isNull()) }
  }
}

private fun SqsAsyncClient.purgeQueue(queueUrl: String?) = purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl!!).build())
