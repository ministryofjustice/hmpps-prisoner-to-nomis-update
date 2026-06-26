package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.test.web.reactive.server.WebTestClient
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerDpsApiExtension.Companion.courtSchedulerDpsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertCourtScheduleOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertCourtScheduleOutResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiMockServer
import java.util.*

class CourtSchedulerResourceIntTest(
  @Autowired private val nomisApi: CourtSchedulerNomisApiMockServer,
  @Autowired private val mappingApi: CourtSchedulerMappingApiMockServer,
) : IntegrationTestBase() {

  private val dpsApi = courtSchedulerDpsApiServer

  @Nested
  inner class RecreateCourtSchedulerSchedule {

    private val prisonerNumber = "A1234BC"
    private val dpsCourtAppearanceId = UUID.randomUUID()
    private val nomisEventId = 123L

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetCourtScheduleMapping(dpsId = dpsCourtAppearanceId, nomisEventId = nomisEventId)
        dpsApi.stubGetCourtAppearance(id = dpsCourtAppearanceId)
        nomisApi.stubUpsertCourtScheduleOut(response = UpsertCourtScheduleOutResponse(12345L, nomisEventId))

        webTestClient.recreateCourtSchedulerScheduleOk(prisonerNumber, dpsCourtAppearanceId)
      }

      @Test
      fun `will publish request telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-schedule-sync-requested"),
          check {
            assertThat(it).containsEntry("recreate", "true")
            assertThat(it).containsEntry("dpsCourtAppearanceId", "$dpsCourtAppearanceId")
            assertThat(it).containsEntry("offenderNo", prisonerNumber)
          },
          isNull(),
        )
      }

      @Test
      fun `will check for existing mapping`() {
        mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/court-scheduler/schedule/dps-id/$dpsCourtAppearanceId")))
      }

      @Test
      fun `will get DPS court appearance`() {
        dpsApi.verify(getRequestedFor(urlEqualTo("/sync/court-appearances/$dpsCourtAppearanceId")))
      }

      @Test
      fun `will upsert NOMIS court schedule with event ID`() {
        NomisApiMockServer.getRequestBody<UpsertCourtScheduleOut>(
          putRequestedFor(urlEqualTo("/movements/A1234BC/court/schedule/out?recreate=true")),
        ).also { request ->
          assertThat(request.eventId).isEqualTo(nomisEventId)
        }
      }

      @Test
      fun `will NOT create mapping`() {
        mappingApi.verify(
          count = 0,
          postRequestedFor(urlEqualTo("/mapping/court-scheduler/schedule")),
        )
      }

      @Test
      fun `will publish success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-schedule-update-success"),
          check {
            assertThat(it).containsEntry("recreate", "true")
            assertThat(it).containsEntry("dpsCourtAppearanceId", "$dpsCourtAppearanceId")
            assertThat(it).containsEntry("nomisEventId", nomisEventId.toString())
            assertThat(it).containsEntry("offenderNo", prisonerNumber)
            assertThat(it).containsEntry("bookingId", "12345")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class MappingNotFound {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetCourtScheduleMapping(status = NOT_FOUND)

        webTestClient.recreateCourtSchedulerSchedule(prisonerNumber, dpsCourtAppearanceId)
          .expectStatus().isBadRequest
      }

      @Test
      fun `will publish request telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("court-scheduler-schedule-sync-requested"),
          check {
            assertThat(it).containsEntry("recreate", "true")
            assertThat(it).containsEntry("dpsCourtAppearanceId", "$dpsCourtAppearanceId")
            assertThat(it).containsEntry("offenderNo", prisonerNumber)
          },
          isNull(),
        )
      }

      @Test
      fun `will check for existing mapping`() {
        mappingApi.verify(getRequestedFor(urlEqualTo("/mapping/court-scheduler/schedule/dps-id/$dpsCourtAppearanceId")))
      }

      @Test
      fun `will NOT upsert NOMIS court schedule with event ID`() {
        nomisApi.verify(count = 0, putRequestedFor(urlEqualTo("/movements/A1234BC/court/schedule/out?recreate=true")))
      }
    }

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/court-scheduler/court/schedule/out/$prisonerNumber/$dpsCourtAppearanceId")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/court-scheduler/court/schedule/out/$prisonerNumber/$dpsCourtAppearanceId")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.put().uri("/court-scheduler/court/schedule/out/$prisonerNumber/$dpsCourtAppearanceId")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    private fun WebTestClient.recreateCourtSchedulerScheduleOk(prisoner: String = prisonerNumber, courtAppearance: UUID = dpsCourtAppearanceId) = recreateCourtSchedulerSchedule(prisoner, courtAppearance)
      .expectStatus().isOk

    private fun WebTestClient.recreateCourtSchedulerSchedule(prisoner: String = prisonerNumber, courtAppearance: UUID = dpsCourtAppearanceId) = put().uri {
      it.path("/court-scheduler/court/schedule/out/$prisoner/$courtAppearance")
        .queryParam("recreate", true)
        .build()
    }
      .headers(setAuthorisation(roles = listOf("ROLE_PRISONER_TO_NOMIS__UPDATE__RW")))
      .exchange()
  }
}
