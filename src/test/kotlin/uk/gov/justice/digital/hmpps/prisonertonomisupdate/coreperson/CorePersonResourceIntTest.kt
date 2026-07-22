package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

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
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderBelief
import java.time.LocalDate
import java.time.LocalDateTime

@ExtendWith(MockitoExtension::class)
class CorePersonResourceIntTest(
  @Autowired private val corePersonNomisApi: CorePersonNomisApiMockServer,
) : IntegrationTestBase() {

  private val corePersonCprApi = CorePersonCprApiExtension.corePersonCprApi

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
            assertThat(it).containsExactly(entry("religion", "nomis=BR, cpr=null"), entry("religions","nomis=1, cpr=0"))
          }

        verify(telemetryClient).trackEvent(
          eq("coreperson-reports-reconciliation-mismatch"),
          any(),
          isNull(),
        )
      }
    }
  }
}
