package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.CSIPDpsApiExtension.Companion.csipDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CSIPFullMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import java.util.UUID

class CSIPDataRepairResourceIntTest : IntegrationTestBase() {
  @Autowired
  private lateinit var csipNomisApi: CSIPNomisApiMockServer

  @Autowired
  private lateinit var csipMappingApi: CSIPMappingApiMockServer

  @DisplayName("POST /prisoners/{prisonNumber}/csip/{dpsCsipReportId}/repair")
  @Nested
  inner class RepairCSIPReport {

    @Nested
    inner class Security {
      val offenderNo = "A1234KT"
      val dpsCsipReportId = UUID.randomUUID().toString()

      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisoners/$offenderNo/csip/$dpsCsipReportId/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisoners/$offenderNo/csip/$dpsCsipReportId/repair")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisoners/$offenderNo/csip/$dpsCsipReportId/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    @DisplayName("when mapping is found")
    inner class HappyPath {
      private val dpsCSIPReportId = UUID.randomUUID().toString()
      private val nomisCSIPReportId = 43217L
      private val offenderNo = "A1234KT"

      @BeforeEach
      fun setUp() {
        csipMappingApi.stubGetByDpsReportId(
          dpsCSIPReportId,
          CSIPFullMappingDto(
            dpsCSIPReportId = dpsCSIPReportId,
            nomisCSIPReportId = nomisCSIPReportId,
            mappingType = CSIPFullMappingDto.MappingType.DPS_CREATED,
            attendeeMappings = listOf(),
            factorMappings = listOf(),
            interviewMappings = listOf(),
            planMappings = listOf(),
            reviewMappings = listOf(),
          ),
        )
        csipDpsApi.stubGetCsipReport(
          dpsCsipReport = dpsCsipRecord().copy(
            recordUuid = UUID.fromString(dpsCSIPReportId),
            logCode = "LG123",
          ),
        )
        csipNomisApi.stubPutCSIP(upsertCSIPResponse().copy(nomisCSIPReportId = nomisCSIPReportId))
        csipMappingApi.stubPostChildrenMapping()

        webTestClient.post().uri("/prisoners/$offenderNo/csip/$dpsCSIPReportId/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_CSIP")))
          .exchange()
          .expectStatus().isOk

        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("csip-resynchronisation-repair"),
            any(),
            isNull(),
          )
        }
      }

      @Test
      fun `will send telemetry event showing the update`() {
        verify(telemetryClient).trackEvent(
          eq("csip-children-create-success"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `telemetry will contain key facts about the updated csip`() {
        verify(telemetryClient).trackEvent(
          eq("csip-children-create-success"),
          check {
            assertThat(it).containsEntry("dpsCSIPReportId", dpsCSIPReportId)
            assertThat(it).containsEntry("offenderNo", offenderNo)
            assertThat(it).containsEntry("nomisCSIPReportId", "$nomisCSIPReportId")
          },
          isNull(),
        )
      }

      @Test
      fun `will create audit telemetry`() {
        verify(telemetryClient).trackEvent(
          org.mockito.kotlin.eq("csip-resynchronisation-repair"),
          check {
            assertThat(it["dpsCSIPReportId"]).isEqualTo(dpsCSIPReportId)
            assertThat(it["prisonNumber"]).isEqualTo(offenderNo)
          },
          isNull(),
        )
      }

      @Test
      fun `will call the mapping service to get the NOMIS csip id`() {
        csipMappingApi.verify(getRequestedFor(urlMatching("/mapping/csip/dps-csip-id/$dpsCSIPReportId/all")))
      }

      @Test
      fun `will call back to DPS to get csip details`() {
        csipDpsApi.verify(getRequestedFor(urlMatching("/csip-records/$dpsCSIPReportId")))
      }

      @Test
      fun `will update the csip in NOMIS`() {
        csipNomisApi.verify(putRequestedFor(urlEqualTo("/csip")))
      }

      @Test
      fun `the update csip will contain details of the DPS csip`() {
        csipNomisApi.verify(
          putRequestedFor(anyUrl())
            .withRequestBodyJsonPath("id", nomisCSIPReportId)
            .withRequestBodyJsonPath("logNumber", "LG123")
            .withRequestBodyJsonPath("offenderNo", "A1234KT")
            .withRequestBodyJsonPath("incidentDate", "2024-06-12")
            .withRequestBodyJsonPath("typeCode", "INT")
            .withRequestBodyJsonPath("locationCode", "LIB")
            .withRequestBodyJsonPath("areaOfWorkCode", "EDU")
            .withRequestBodyJsonPath("reportedBy", "JIM_ADM")
            .withRequestBodyJsonPath("reportedDate", "2024-10-01"),
        )
      }

      @Test
      fun `will create a mapping for the csip children`() {
        csipMappingApi.verify(
          1,
          postRequestedFor(urlPathEqualTo("/mapping/csip/children/all"))
            .withRequestBody(matchingJsonPath("factorMappings[0].nomisId", equalTo("111")))
            .withRequestBody(matchingJsonPath("factorMappings[0].dpsId", equalTo("8cdadcf3-b003-4116-9956-c99bd8df6111")))
            .withRequestBody(matchingJsonPath("factorMappings[0].mappingType", equalTo("DPS_CREATED")))
            .withRequestBody(matchingJsonPath("planMappings[0].nomisId", equalTo("222")))
            .withRequestBody(matchingJsonPath("planMappings[0].dpsId", equalTo("8cdadcf3-b003-4116-9956-c99bd8df6333")))
            .withRequestBody(matchingJsonPath("planMappings[0].mappingType", equalTo("DPS_CREATED"))),
        )
      }
    }
  }
}
