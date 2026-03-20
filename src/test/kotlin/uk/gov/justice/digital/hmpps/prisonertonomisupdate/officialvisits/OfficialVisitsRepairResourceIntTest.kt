package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.eq
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OfficialVisitMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OfficialVisitorMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.VisitSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateOfficialVisitRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateOfficialVisitorRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateOfficialVisitRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncOfficialVisit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncOfficialVisitor
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsNomisApiMockServer.Companion.officialVisitResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsNomisApiMockServer.Companion.officialVisitor
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.getRequestBody
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import java.util.*

class OfficialVisitsRepairResourceIntTest(
  @Autowired
  private val nomisApi: OfficialVisitsNomisApiMockServer,
  @Autowired
  private val mappingApi: OfficialVisitsMappingApiMockServer,
  @Autowired
  private val visitSlotsMappingApi: VisitSlotsMappingApiMockServer,
) : SqsIntegrationTestBase() {
  private val dpsApi = OfficialVisitsDpsApiExtension.dpsOfficialVisitsServer

  @DisplayName("POST /prison/{prisonId}/prisoners/{offenderNo}/official-visits/{dpsVisitId}")
  @Nested
  inner class CreateOfficialVisitFromNomis {
    val offenderNo = "A1234KT"
    val prisonId = "MDI"
    val nomisVisitId = 65432L
    val nomisVisitorId = 76544L
    val dpsOfficialVisitId = 8549934L
    val nomisVisitSlotId = 8484L
    val dpsLocationId: UUID = UUID.randomUUID()
    val nomisLocationId = 765L
    val dpsVisitSlotId = 123L
    val dpsOfficialVisitorId = 173193L
    val dpsOfficialVisitorId2 = 273193L
    val nomisVisitorId2 = 73738L
    val contactAndPersonId = 1373L
    val contactAndPersonId2 = 94943L

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prison/$prisonId/prisoners/$offenderNo/official-visits/$dpsOfficialVisitId")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prison/$prisonId/prisoners/$offenderNo/official-visits/$dpsOfficialVisitId")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prison/$prisonId/prisoners/$offenderNo/official-visits/$dpsOfficialVisitId")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetByVisitDpsIdOrNullNotFoundTwiceFollowedBySuccessForever(
          dpsOfficialVisitId,
          OfficialVisitMappingDto(
            dpsId = dpsOfficialVisitId.toString(),
            nomisId = nomisVisitId,
            mappingType = OfficialVisitMappingDto.MappingType.NOMIS_CREATED,
          ),
        )

        dpsApi.stubGetOfficialVisit(
          officialVisitId = dpsOfficialVisitId,
          response = syncOfficialVisit().copy(
            officialVisitId = dpsOfficialVisitId,
            prisonCode = prisonId,
            dpsLocationId = dpsLocationId,
            prisonVisitSlotId = dpsVisitSlotId,
            prisonerNumber = offenderNo,
            visitors = listOf(
              syncOfficialVisitor().copy(
                officialVisitorId = dpsOfficialVisitorId,
                contactId = contactAndPersonId,
              ),
              syncOfficialVisitor().copy(
                officialVisitorId = dpsOfficialVisitorId2,
                contactId = contactAndPersonId2,
              ),
            ),
          ),
        )
        visitSlotsMappingApi.stubGetVisitSlotByDpsId(
          dpsVisitSlotId.toString(),
          VisitSlotMappingDto(
            dpsId = dpsVisitSlotId.toString(),
            nomisId = nomisVisitSlotId,
            mappingType = VisitSlotMappingDto.MappingType.MIGRATED,
          ),
        )
        visitSlotsMappingApi.stubGetInternalLocationByDpsId(
          dpsLocationId.toString(),
          LocationMappingDto(
            dpsLocationId = dpsLocationId.toString(),
            nomisLocationId = nomisLocationId,
            mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
          ),
        )

        nomisApi.stubCreateOfficialVisit(
          offenderNo = offenderNo,
          response = officialVisitResponse().copy(visitId = nomisVisitId),
        )
        mappingApi.stubCreateVisitMapping()

        mappingApi.stubGetVisitorByDpsIdOrNull(dpsOfficialVisitorId, null)
        mappingApi.stubGetVisitorByDpsIdOrNull(dpsOfficialVisitorId2, null)
        nomisApi.stubCreateOfficialVisitors(
          visitId = nomisVisitId,
          response1 = officialVisitor().copy(id = nomisVisitorId),
          response2 = officialVisitor().copy(id = nomisVisitorId2),
        )
        mappingApi.stubCreateVisitorMapping()

        webTestClient.post().uri("/prison/$prisonId/prisoners/$offenderNo/official-visits/$dpsOfficialVisitId")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__SYNCHRONISATION__RW")))
          .exchange()
          .expectStatus().isCreated
      }

      @Test
      fun `will create visit in NOMIS`() {
        val request: CreateOfficialVisitRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/prisoner/$offenderNo/official-visits")), jsonMapper)
        assertThat(request.prisonId).isEqualTo(prisonId)
        assertThat(request.visitSlotId).isEqualTo(nomisVisitSlotId)
        assertThat(request.internalLocationId).isEqualTo(nomisLocationId)
      }

      @Test
      fun `will create visitor in NOMIS`() {
        val request: List<CreateOfficialVisitorRequest> = NomisApiExtension.nomisApi.getRequestBodies(postRequestedFor(urlEqualTo("/official-visits/$nomisVisitId/official-visitor")), jsonMapper)
        assertThat(request).hasSize(2)
        with(request[0]) {
          assertThat(personId).isEqualTo(contactAndPersonId)
        }
        with(request[1]) {
          assertThat(personId).isEqualTo(contactAndPersonId2)
        }
      }

      @Test
      fun `will create mappings for visit and visitors`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/official-visits/visit"))
            .withRequestBodyJsonPath("dpsId", dpsOfficialVisitId.toString())
            .withRequestBodyJsonPath("nomisId", nomisVisitId)
            .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
        )

        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/official-visits/visitor"))
            .withRequestBodyJsonPath("dpsId", dpsOfficialVisitorId.toString())
            .withRequestBodyJsonPath("nomisId", nomisVisitorId)
            .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
        )

        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/official-visits/visitor"))
            .withRequestBodyJsonPath("dpsId", dpsOfficialVisitorId2.toString())
            .withRequestBodyJsonPath("nomisId", nomisVisitorId2)
            .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visit-create-repair-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["dpsOfficialVisitId"]).isEqualTo("$dpsOfficialVisitId")
            assertThat(it["dpsOfficialVisitorIds"]).isEqualTo("$dpsOfficialVisitorId, $dpsOfficialVisitorId2")
            assertThat(it["prisonId"]).isEqualTo(prisonId)
            assertThat(it["reason"]).isEqualTo("Visit created. Manual repair")
          },
          isNull(),
        )
      }
    }
  }

  @DisplayName("PUT /prison/{prisonId}/prisoners/{offenderNo}/official-visits/{dpsVisitId}")
  @Nested
  inner class UpdateOfficialVisitFromNomis {
    val offenderNo = "A1234KT"
    val prisonId = "MDI"
    val nomisVisitId = 65432L
    val nomisVisitorId = 76544L
    val dpsOfficialVisitId = 8549934L
    val nomisVisitSlotId = 8484L
    val dpsLocationId: UUID = UUID.randomUUID()
    val nomisLocationId = 765L
    val dpsVisitSlotId = 123L
    val dpsOfficialVisitorId = 173193L
    val dpsOfficialVisitorId2 = 273193L
    val nomisVisitorId2 = 73738L
    val contactAndPersonId = 1373L
    val contactAndPersonId2 = 94943L

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/prison/$prisonId/prisoners/$offenderNo/official-visits/$dpsOfficialVisitId")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/prison/$prisonId/prisoners/$offenderNo/official-visits/$dpsOfficialVisitId")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.put().uri("/prison/$prisonId/prisoners/$offenderNo/official-visits/$dpsOfficialVisitId")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingApi.stubGetVisitByDpsIdOrNull(
          dpsOfficialVisitId,
          OfficialVisitMappingDto(
            dpsId = dpsOfficialVisitId.toString(),
            nomisId = nomisVisitId,
            mappingType = OfficialVisitMappingDto.MappingType.NOMIS_CREATED,
          ),
        )

        dpsApi.stubGetOfficialVisit(
          officialVisitId = dpsOfficialVisitId,
          response = syncOfficialVisit().copy(
            officialVisitId = dpsOfficialVisitId,
            prisonCode = prisonId,
            dpsLocationId = dpsLocationId,
            prisonVisitSlotId = dpsVisitSlotId,
            prisonerNumber = offenderNo,
            visitors = listOf(
              syncOfficialVisitor().copy(
                officialVisitorId = dpsOfficialVisitorId,
                contactId = contactAndPersonId,
              ),
              syncOfficialVisitor().copy(
                officialVisitorId = dpsOfficialVisitorId2,
                contactId = contactAndPersonId2,
              ),
            ),
          ),
        )
        visitSlotsMappingApi.stubGetVisitSlotByDpsId(
          dpsVisitSlotId.toString(),
          VisitSlotMappingDto(
            dpsId = dpsVisitSlotId.toString(),
            nomisId = nomisVisitSlotId,
            mappingType = VisitSlotMappingDto.MappingType.MIGRATED,
          ),
        )
        visitSlotsMappingApi.stubGetInternalLocationByDpsId(
          dpsLocationId.toString(),
          LocationMappingDto(
            dpsLocationId = dpsLocationId.toString(),
            nomisLocationId = nomisLocationId,
            mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
          ),
        )

        nomisApi.stubUpdateOfficialVisit(
          visitId = nomisVisitId,
        )

        mappingApi.stubGetVisitorByDpsIdOrNull(
          dpsOfficialVisitorId,
          OfficialVisitorMappingDto(
            dpsId = dpsOfficialVisitorId.toString(),
            nomisId = nomisVisitorId,
            mappingType = OfficialVisitorMappingDto.MappingType.MIGRATED,
          ),
        )
        mappingApi.stubGetVisitorByDpsIdOrNull(dpsOfficialVisitorId2, null)
        nomisApi.stubUpdateOfficialVisitor(
          visitId = nomisVisitId,
          visitorId = nomisVisitorId,
        )
        nomisApi.stubCreateOfficialVisitor(
          visitId = nomisVisitId,
          response = officialVisitor().copy(id = nomisVisitorId2),
        )
        mappingApi.stubCreateVisitorMapping()

        webTestClient.put().uri("/prison/$prisonId/prisoners/$offenderNo/official-visits/$dpsOfficialVisitId")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__SYNCHRONISATION__RW")))
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will update visit in NOMIS`() {
        val request: UpdateOfficialVisitRequest = NomisApiExtension.nomisApi.getRequestBody(putRequestedFor(urlEqualTo("/official-visits/$nomisVisitId")), jsonMapper)
        assertThat(request.visitSlotId).isEqualTo(nomisVisitSlotId)
        assertThat(request.internalLocationId).isEqualTo(nomisLocationId)
      }

      @Test
      fun `will update visitor in NOMIS that already existed`() {
        nomisApi.verify(putRequestedFor(urlEqualTo("/official-visits/$nomisVisitId/official-visitor/$nomisVisitorId")))
      }

      @Test
      fun `will create visitor in NOMIS that was missing`() {
        val request: CreateOfficialVisitorRequest = NomisApiExtension.nomisApi.getRequestBody(postRequestedFor(urlEqualTo("/official-visits/$nomisVisitId/official-visitor")), jsonMapper)
        assertThat(request.personId).isEqualTo(contactAndPersonId2)
      }

      @Test
      fun `will create mappings for missing visitor`() {
        mappingApi.verify(
          postRequestedFor(urlPathEqualTo("/mapping/official-visits/visitor"))
            .withRequestBodyJsonPath("dpsId", dpsOfficialVisitorId2.toString())
            .withRequestBodyJsonPath("nomisId", nomisVisitorId2)
            .withRequestBodyJsonPath("mappingType", "DPS_CREATED"),
        )
      }

      @Test
      fun `will track telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("officialvisits-visit-update-repair-success"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["dpsOfficialVisitId"]).isEqualTo("$dpsOfficialVisitId")
            assertThat(it["dpsOfficialVisitorIds"]).isEqualTo("$dpsOfficialVisitorId, $dpsOfficialVisitorId2")
            assertThat(it["prisonId"]).isEqualTo(prisonId)
            assertThat(it["reason"]).isEqualTo("Visit updated. Manual repair")
          },
          isNull(),
        )
      }
    }
  }
}
