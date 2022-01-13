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
import uk.gov.justice.digital.hmpps.hmppsregisterstonomisupdate.integration.LocalStackContainer
import uk.gov.justice.digital.hmpps.hmppsregisterstonomisupdate.integration.LocalStackContainer.setLocalStackProperties
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.VisitsApiExtension
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService

@ExtendWith(NomisApiExtension::class, HmppsAuthApiExtension::class, VisitsApiExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class SqsIntegrationTestBase {
  @Autowired
  private lateinit var hmppsQueueService: HmppsQueueService

  internal val registersQueue by lazy { hmppsQueueService.findByQueueId("prisoner") as HmppsQueue }

  internal val awsSqsClient by lazy { registersQueue.sqsClient }
  internal val awsSqsDlqClient by lazy { registersQueue.sqsDlqClient }
  internal val queueUrl by lazy { registersQueue.queueUrl }
  internal val dlqUrl by lazy { registersQueue.dlqUrl }

  @BeforeEach
  fun cleanQueue() {
    awsSqsClient.purgeQueue(PurgeQueueRequest(queueUrl))
    awsSqsDlqClient?.purgeQueue(PurgeQueueRequest(dlqUrl))
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
