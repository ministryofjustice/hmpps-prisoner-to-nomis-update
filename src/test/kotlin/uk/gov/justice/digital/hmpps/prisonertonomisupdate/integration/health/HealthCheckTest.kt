package uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.health

import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.AdjudicationsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.AlertsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.CaseNotesDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.CorePersonCprApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.CSIPDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.FinanceDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.IncidentsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances.VisitBalanceDpsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.ActivitiesApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.AppointmentsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.IncentivesApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.LocationsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NonAssociationsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.SentencingAdjustmentsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.VisitsApiExtension

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
      .jsonPath("components.courtSentencingApi.status").isEqualTo("UP")
      .jsonPath("components.alertsApi.status").isEqualTo("UP")
      .jsonPath("components.caseNotesApi.status").isEqualTo("UP")
      .jsonPath("components.csipApi.status").isEqualTo("UP")
      .jsonPath("components.financeApi.status").isEqualTo("UP")
      .jsonPath("components.incidentsApi.status").isEqualTo("UP")
      .jsonPath("components.personalRelationshipsApi.status").isEqualTo("UP")
      .jsonPath("components.organisationsApi.status").isEqualTo("UP")
      .jsonPath("components.visitBalanceApi.status").isEqualTo("UP")
      .jsonPath("components.corePersonApi.status").isEqualTo("UP")
      .jsonPath("components.officialVisitsApi.status").isEqualTo("UP")
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
    LocationsApiExtension.locationsApi.stubHealthPing(status)
    CourtSentencingApiExtension.courtSentencingApi.stubHealthPing(status)
    AlertsDpsApiExtension.alertsDpsApi.stubHealthPing(status)
    CaseNotesDpsApiExtension.caseNotesDpsApi.stubHealthPing(status)
    CSIPDpsApiExtension.csipDpsApi.stubHealthPing(status)
    IncidentsDpsApiExtension.incidentsDpsApi.stubHealthPing(status)
    ContactPersonDpsApiExtension.dpsContactPersonServer.stubHealthPing(status)
    FinanceDpsApiExtension.dpsFinanceServer.stubHealthPing(status)
    OrganisationsDpsApiExtension.dpsOrganisationsServer.stubHealthPing(status)
    VisitBalanceDpsApiExtension.visitBalanceDpsApi.stubHealthPing(status)
    CorePersonCprApiExtension.corePersonCprApi.stubHealthPing(status)
    OfficialVisitsDpsApiExtension.dpsOfficialVisitsServer.stubHealthPing(status)
  }
}
