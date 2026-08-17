package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.PrisonReligion
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.PrisonReligion.ReligionCode
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.model.PrisonReligion.ReligionCode.BAHA
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ReligionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePersonMergeRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorePersonReligionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderBelief
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiMockServer
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class CorePersonResourceIntTest(
  @Autowired private val corePersonNomisApi: CorePersonNomisApiMockServer,
) : IntegrationTestBase() {

  private val corePersonCprApi = CorePersonCprApiExtension.corePersonCprApi

  private val mappingServer = MappingExtension.mappingServer

  @Captor
  lateinit var telemetryCaptor: ArgumentCaptor<Map<String, String>>

  @DisplayName("GET /core-person/reconciliation/{prisonNumber}")
  @Nested
  inner class GenerateReconciliationReportForPrisoner {
    private val prisonNumber = "A1234KT"

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/core-person/reconciliation/$prisonNumber")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/core-person/reconciliation/$prisonNumber")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/core-person/reconciliation/$prisonNumber")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `return null when offender not found`() {
        corePersonNomisApi.stubGetCorePersonReligions("A9999BC", status = HttpStatus.NOT_FOUND)
        webTestClient.get().uri("/core-person/reconciliation/A9999BC")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
          .expectBody().isEmpty
      }
    }

    @Nested
    inner class HappyPath {

      @BeforeEach
      fun setup() {
        corePersonNomisApi.stubGetCorePersonReligions()
        corePersonCprApi.stubGetCorePerson()
      }

      @Test
      fun `will return no differences`() {
        webTestClient.get().uri("/core-person/reconciliation/$prisonNumber")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody().isEmpty

        verifyNoInteractions(telemetryClient)
      }

      @Test
      fun `will return mismatch with nomis`() {
        corePersonNomisApi.stubGetCorePersonReligions(
          prisonNumber,
          listOf(
            OffenderBelief(
              beliefId = 12345,
              belief = CodeDescription("BR", "British"),
              startDate = LocalDate.parse("2021-01-01"),
              audit = NomisAudit(
                createDatetime = LocalDateTime.parse("2012-01-02T10:20:30"),
                createUsername = "BillyBob",
              ),
            ),
          ),
        )

        webTestClient.get().uri("/core-person/reconciliation/$prisonNumber")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .jsonPath("prisonNumber").isEqualTo(prisonNumber)
          .jsonPath("differences").value<Map<String, String>> {
            assertThat(it).containsExactly(
              entry("religion", "nomis=BR, cpr=null"),
              entry("religions", "nomis=1, cpr=0"),
            )
          }

        verify(telemetryClient).trackEvent(
          eq("coreperson-reports-reconciliation-mismatch"),
          any(),
          isNull(),
        )
      }
    }
  }

  @DisplayName("POST /core-person/prisoner/{prisonNumberTo}/merge")
  @Nested
  inner class UpdateOffenderByPrisonNumberAfterMerge {
    private val prisonNumber = "A1234KT"

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/core-person/prisoner/$prisonNumber/merge")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/core-person/prisoner/$prisonNumber/merge")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/core-person/prisoner/$prisonNumber/merge")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {

      val religionHistory = listOf(
        prisonReligion(
          cprReligionId = "ab77e4c0-e55f-4551-85a7-a379cb2674b2",
          startDate = LocalDate.parse("2024-06-30"),
        ),
      )
      val religionMappingDto = listOf(
        ReligionMappingDto(
          cprId = religionHistory[0].cprReligionId!!,
          nomisId = 10001L,
          nomisPrisonNumber = prisonNumber,
          mappingType = ReligionMappingDto.MappingType.MIGRATED,
        ),
      )

      @BeforeEach
      fun setup() {
        corePersonCprApi.stubGetCorePerson(
          prisonNumber = prisonNumber,
          response = corePersonDto().copy(religionHistory = religionHistory),
        )
        mappingServer.stubGetReligionMappings(religionMappingDto)
        corePersonNomisApi.stubMergeCorePersonReligions(prisonNumber)
      }

      @Test
      fun `will merge religions in NOMIS`() {
        webTestClient.post().uri("/core-person/prisoner/$prisonNumber/merge")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNoContent

        val corePersonMergeRequest: CorePersonMergeRequest = NomisApiMockServer.getRequestBody(
          postRequestedFor(urlPathEqualTo("/core-person/$prisonNumber/merge")),
        )

        assertThat(corePersonMergeRequest).isEqualTo(
          CorePersonMergeRequest(
            listOf(
              CorePersonReligionRequest(
                beliefId = religionMappingDto[0].nomisId,
              ),
            ),
          ),
        )
      }

      @Test
      fun `will track telemetry for the merge`() {
        webTestClient.post().uri("/core-person/prisoner/$prisonNumber/merge")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()

        verify(telemetryClient).trackEvent(
          eq("cpr-religions-merged-success"),
          check {
            assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
          },
          isNull(),
        )
        verify(telemetryClient).trackEvent(
          eq("cpr-person-merged-success"),
          check {
            assertThat(it["prisonNumber"]).isEqualTo(prisonNumber)
          },
          isNull(),
        )
      }
    }
  }

  private fun prisonReligion(
    cprReligionId: String,
    endDate: LocalDate? = null,
    startDate: LocalDate,
    code: ReligionCode = BAHA,
  ) = PrisonReligion(
    cprReligionId = cprReligionId,
    religionCode = code,
    changeReasonKnown = false,
    startDate = startDate,
    endDate = endDate,
    current = endDate == null,
    createDateTime = LocalDateTime.parse("2025-02-03T10:20:30"),
    createUserId = "abcdef",
  )
}
