package uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.hmpps.sqs.HmppsSqsProperties

class PurgeDlqIntTest(
  @Autowired private val hmppsQueueProperties: HmppsSqsProperties,
) : IntegrationTestBase() {

  @Test
  fun `access unauthorised when no authority`() {
    webTestClient.put().uri("/queue-admin/purge-queue/${hmppsQueueProperties.queues["activity"]!!.dlqName}")
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `access unauthorised with invalid custom auth header`() {
    webTestClient.put().uri("/queue-admin/purge-queue/${hmppsQueueProperties.queues["activity"]!!.dlqName}")
      .headers { it.add("X-Auth-CronJob", "wrong-secret") }
      .exchange()
      .expectStatus().isUnauthorized
  }

  @Test
  fun `access allowed with custom auth header`() {
    webTestClient.put().uri("/queue-admin/purge-queue/${hmppsQueueProperties.queues["activity"]!!.dlqName}")
      .headers { it.add("X-Auth-CronJob", "some-secret") }
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `access allowed with oauth token`() {
    webTestClient.put().uri("/queue-admin/purge-queue/${hmppsQueueProperties.queues["activity"]!!.dlqName}")
      .headers(setAuthorisation(roles = listOf("ROLE_QUEUE_ADMIN")))
      .exchange()
      .expectStatus().isOk
  }
}
