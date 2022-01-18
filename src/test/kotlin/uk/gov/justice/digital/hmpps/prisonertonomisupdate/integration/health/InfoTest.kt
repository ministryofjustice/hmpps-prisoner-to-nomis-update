package uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.health

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.context.TestPropertySource
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@TestPropertySource(
  properties =
  ["spring.autoconfigure.exclude=uk.gov.justice.hmpps.sqs.HmppsSqsConfiguration"]
)
class InfoTest : IntegrationTestBase() {

  @Test
  fun `Info page is accessible`() {
    webTestClient.get()
      .uri("/info")
      .exchange()
      .expectStatus()
      .isOk
      .expectBody()
      .jsonPath("build.name").isEqualTo("hmpps-prisoner-to-nomis-update")
  }

  @Test
  fun `Info page reports version`() {
    webTestClient.get().uri("/info")
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("build.version").value<String> {
        assertThat(it).startsWith(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE))
      }
  }
}
