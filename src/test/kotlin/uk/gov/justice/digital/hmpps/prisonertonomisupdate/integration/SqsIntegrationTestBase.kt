package uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration

import com.amazonaws.services.sqs.model.PurgeQueueRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.hmppsregisterstonomisupdate.integration.LocalStackContainer
import uk.gov.justice.digital.hmpps.hmppsregisterstonomisupdate.integration.LocalStackContainer.setLocalStackProperties
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.ActivitiesApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.IncentivesApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.SentenceAdjustmentsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.VisitsApiExtension
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@ExtendWith(
  NomisApiExtension::class,
  MappingExtension::class,
  HmppsAuthApiExtension::class,
  VisitsApiExtension::class,
  IncentivesApiExtension::class,
  ActivitiesApiExtension::class,
  SentenceAdjustmentsApiExtension::class,
)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
abstract class SqsIntegrationTestBase {
  @Autowired
  lateinit var webTestClient: WebTestClient

  @Autowired
  private lateinit var hmppsQueueService: HmppsQueueService

  internal val registersQueue by lazy { hmppsQueueService.findByQueueId("prisoner") as HmppsQueue }

  internal val awsSqsClient by lazy { registersQueue.sqsClient }
  internal val awsSqsDlqClient by lazy { registersQueue.sqsDlqClient }
  internal val queueUrl by lazy { registersQueue.queueUrl }
  internal val dlqUrl by lazy { registersQueue.dlqUrl }

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

  @BeforeEach
  fun cleanQueue() {
    awsSqsClient.purgeQueue(PurgeQueueRequest(queueUrl))
    awsSqsDlqClient?.purgeQueue(PurgeQueueRequest(dlqUrl))

    awsSqsVisitClient.purgeQueue(PurgeQueueRequest(visitQueueUrl))
    awsSqsVisitDlqClient?.purgeQueue(PurgeQueueRequest(visitDlqUrl))

    awsSqsIncentiveClient.purgeQueue(PurgeQueueRequest(incentiveQueueUrl))
    awsSqsIncentiveDlqClient?.purgeQueue(PurgeQueueRequest(incentiveDlqUrl))

    awsSqsActivityClient.purgeQueue(PurgeQueueRequest(activityQueueUrl))
    awsSqsActivityDlqClient?.purgeQueue(PurgeQueueRequest(activityDlqUrl))
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
