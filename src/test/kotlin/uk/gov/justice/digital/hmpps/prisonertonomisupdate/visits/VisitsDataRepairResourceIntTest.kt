package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.VisitsApiExtension.Companion.visitsApi
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class VisitsDataRepairResourceIntTest : IntegrationTestBase() {
  @DisplayName("POST /prisoners/{offenderNo}/visits/resynchronise/{visitId}")
  @Nested
  inner class RepairVisits {
    val offenderNo = "A1234KT"
    val visitId = "BGHJ-1991-GG"

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisoners/$offenderNo/visits/resynchronise/$visitId")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisoners/$offenderNo/visits/resynchronise/$visitId")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisoners/$offenderNo/visits/resynchronise/$visitId")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {

      @BeforeEach
      fun setUp() {
        visitsApi.stubVisitGet(
          visitId,
          buildVisitApiDtoJsonResponse(
            visitId = visitId,
            prisonerId = offenderNo,
            visitRoom = "Main visits room",
            visitRestriction = "OPEN",
          ),
        )
        mappingServer.stubGetVsipWithError(visitId, 404)
        mappingServer.stubCreate()
        nomisApi.stubVisitCreate(prisonerId = offenderNo)

        webTestClient.post().uri("/prisoners/$offenderNo/visits/resynchronise/$visitId")
          .headers(setAuthorisation(roles = listOf("MIGRATE_NOMIS_SYSCON")))
          .exchange()
          .expectStatus().isNoContent
      }

      @Test
      fun `will create visit and mappings`() {
        nomisApi.verify(
          postRequestedFor(urlEqualTo("/prisoners/$offenderNo/visits"))
            .withRequestBody(matchingJsonPath("offenderNo", equalTo(offenderNo)))
            .withRequestBody(matchingJsonPath("prisonId", equalTo("MDI")))
            .withRequestBody(matchingJsonPath("visitType", equalTo("SCON")))
            .withRequestBody(matchingJsonPath("room", equalTo("Main visits room")))
            .withRequestBody(matchingJsonPath("openClosedStatus", equalTo("OPEN")))
            .withRequestBody(matchingJsonPath("startDateTime", equalTo("2019-12-02T09:00:00")))
            .withRequestBody(matchingJsonPath("issueDate", equalTo(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))))
            .withRequestBody(
              matchingJsonPath(
                "visitComment",
                equalTo("DPS booking reference: $visitId"),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "visitOrderComment",
                equalTo("DPS booking reference: $visitId"),
              ),
            ),
        )

        mappingServer.verify(
          postRequestedFor(urlEqualTo("/mapping/visits"))
            .withRequestBody(matchingJsonPath("nomisId", equalTo("12345")))
            .withRequestBody(matchingJsonPath("vsipId", equalTo(visitId)))
            .withRequestBody(matchingJsonPath("mappingType", equalTo("ONLINE"))),
        )
      }

      @Test
      fun `will track telemetry for the resynchronise`() {
        verify(telemetryClient).trackEvent(
          eq("visit-create-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["visitId"]).isEqualTo(visitId)
          },
          isNull(),
        )
      }

      @Test
      fun `will track telemetry for the repair`() {
        verify(telemetryClient).trackEvent(
          eq("to-nomis-synch-visit-resynchronisation-repair"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["visitId"]).isEqualTo(visitId)
          },
          isNull(),
        )
      }
    }
  }
}
