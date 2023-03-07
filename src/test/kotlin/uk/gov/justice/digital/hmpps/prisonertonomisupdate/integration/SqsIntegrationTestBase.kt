package uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration

import com.microsoft.applicationinsights.TelemetryClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.LocalStackContainer.setLocalStackProperties
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.ActivitiesApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.AppointmentsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.IncentivesApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.SentencingAdjustmentsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.VisitsApiExtension
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsTopic

@ExtendWith(
  NomisApiExtension::class,
  MappingExtension::class,
  HmppsAuthApiExtension::class,
  VisitsApiExtension::class,
  IncentivesApiExtension::class,
  ActivitiesApiExtension::class,
  AppointmentsApiExtension::class,
  SentencingAdjustmentsApiExtension::class,
)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
abstract class SqsIntegrationTestBase {
  @SpyBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  lateinit var webTestClient: WebTestClient

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
  internal val awsSqsActivityDlqClient by lazy { activityQueue.sqsDlqClient }
  internal val activityQueueUrl by lazy { activityQueue.queueUrl }
  internal val activityDlqUrl by lazy { activityQueue.dlqUrl }

  internal val appointmentQueue by lazy { hmppsQueueService.findByQueueId("appointment") as HmppsQueue }

  internal val awsSqsAppointmentClient by lazy { appointmentQueue.sqsClient }
  internal val awsSqsAppointmentDlqClient by lazy { appointmentQueue.sqsDlqClient }
  internal val appointmentQueueUrl by lazy { appointmentQueue.queueUrl }
  internal val appointmentDlqUrl by lazy { appointmentQueue.dlqUrl }

  internal val sentencingQueue by lazy { hmppsQueueService.findByQueueId("sentencing") as HmppsQueue }

  internal val awsSqsSentencingClient by lazy { sentencingQueue.sqsClient }
  internal val awsSqsSentencingDlqClient by lazy { sentencingQueue.sqsDlqClient }
  internal val sentencingQueueUrl by lazy { sentencingQueue.queueUrl }
  internal val sentencingDlqUrl by lazy { sentencingQueue.dlqUrl }

  internal val awsSnsClient by lazy { topic.snsClient }
  internal val topicArn by lazy { topic.arn }

  @BeforeEach
  fun cleanQueue() {
    awsSqsVisitClient.purgeQueue(visitQueueUrl).get()
    awsSqsVisitDlqClient?.purgeQueue(visitDlqUrl)?.get()

    awsSqsIncentiveClient.purgeQueue(incentiveQueueUrl).get()
    awsSqsIncentiveDlqClient?.purgeQueue(incentiveDlqUrl)?.get()

    awsSqsActivityClient.purgeQueue(activityQueueUrl).get()
    awsSqsActivityDlqClient?.purgeQueue(activityDlqUrl)?.get()

    awsSqsAppointmentClient.purgeQueue(appointmentQueueUrl).get()
    awsSqsAppointmentDlqClient?.purgeQueue(appointmentDlqUrl)?.get()

    awsSqsSentencingClient.purgeQueue(sentencingQueueUrl).get()
    awsSqsSentencingDlqClient?.purgeQueue(sentencingDlqUrl)?.get()
  }

  companion object {
    private val localStackContainer = LocalStackContainer.instance

    @JvmStatic
    @DynamicPropertySource
    fun testcontainers(registry: DynamicPropertyRegistry) {
      localStackContainer?.also { setLocalStackProperties(it, registry) }
    }
  }
}

private fun SqsAsyncClient.purgeQueue(queueUrl: String?) = purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl!!).build())
