package uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.health

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.ActivitiesApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.AdjudicationsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.AppointmentsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.IncentivesApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NonAssociationsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.SentencingAdjustmentsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.VisitsApiExtension
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.function.Consumer

class HealthCheckTest : IntegrationTestBase() {

  @Test
  fun `Health page reports ok`() {
    stubPingWithResponse(200)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
      .jsonPath("components.visitsApi.status").isEqualTo("UP")
      .jsonPath("components.activitiesApi.status").isEqualTo("UP")
      .jsonPath("components.incentivesApi.status").isEqualTo("UP")
      .jsonPath("components.nomisApi.status").isEqualTo("UP")
      .jsonPath("components.hmppsAuthApi.status").isEqualTo("UP")
      .jsonPath("components.visitsApi.status").isEqualTo("UP")
      .jsonPath("components.mappingApi.status").isEqualTo("UP")
      .jsonPath("components.sentenceAdjustmentsApi.status").isEqualTo("UP")
      .jsonPath("components.adjudicationsApi.status").isEqualTo("UP")
      .jsonPath("components.nonAssociationsApi.status").isEqualTo("UP")
  }

  @Test
  fun `Health page reports down`() {
    stubPingWithResponse(404)

    webTestClient.get()
      .uri("/health")
      .exchange()
      .expectStatus().is5xxServerError
      .expectBody()
      .jsonPath("status").isEqualTo("DOWN")
  }

  @Test
  fun `Health info reports version`() {
    stubPingWithResponse(200)
    webTestClient.get().uri("/health")
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("components.healthInfo.details.version").value(
        Consumer<String> {
          assertThat(it).startsWith(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE))
        },
      )
  }

  @Test
  fun `Health ping page is accessible`() {
    webTestClient.get()
      .uri("/health/ping")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `readiness reports ok`() {
    webTestClient.get()
      .uri("/health/readiness")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  @Test
  fun `liveness reports ok`() {
    webTestClient.get()
      .uri("/health/liveness")
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("status").isEqualTo("UP")
  }

  private fun stubPingWithResponse(status: Int) {
    HmppsAuthApiExtension.hmppsAuth.stubHealthPing(status)
    NomisApiExtension.nomisApi.stubHealthPing(status)
    VisitsApiExtension.visitsApi.stubHealthPing(status)
    IncentivesApiExtension.incentivesApi.stubHealthPing(status)
    MappingExtension.mappingServer.stubHealthPing(status)
    ActivitiesApiExtension.activitiesApi.stubHealthPing(status)
    AppointmentsApiExtension.appointmentsApi.stubHealthPing(status)
    SentencingAdjustmentsApiExtension.sentencingAdjustmentsApi.stubHealthPing(status)
    AdjudicationsApiExtension.adjudicationsApiServer.stubHealthPing(status)
    NonAssociationsApiExtension.nonAssociationsApiServer.stubHealthPing(status)
  }
}
