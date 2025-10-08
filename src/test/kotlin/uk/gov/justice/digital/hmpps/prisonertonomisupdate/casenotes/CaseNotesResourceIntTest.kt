package uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.NOMIS_BOOKING_ID
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.CaseNotesDpsApiExtension.Companion.caseNotesDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.casenotes.model.CaseNoteAmendment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CaseNoteMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerCaseNotesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
import java.time.LocalDateTime
import java.util.UUID

private val NOMIS_CASE_NOTE_ID = 123456L
private val OFFENDER_NO = "A1234KT"
private val DPS_CASE_NOTE_ID = UUID.randomUUID().toString()

class CaseNotesResourceIntTest : IntegrationTestBase() {

  @MockitoSpyBean
  private lateinit var caseNotesReconciliationService: CaseNotesReconciliationService

  @Autowired
  private lateinit var caseNotesNomisApiMockServer: CaseNotesNomisApiMockServer

  @Autowired
  private lateinit var caseNotesMappingApi: CaseNotesMappingApiMockServer

  @DisplayName("Reconciliation batch job")
  @Nested
  inner class GenerateReconciliationReport {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
      reset(caseNotesReconciliationService)
    }

    @Test
    fun `will output report success telemetry`() = runTest {
      nomisApi.stubGetAllPrisonersInitialCount(8, 1)
      nomisApi.stubGetAllPrisonersPage1()
      nomisApi.stubGetAllPrisonersPage2()
      caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner("A0001BB")
      caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner("A0002BB")
      caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner("A0003BB")
      caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner("A0004BB")
      caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner("A0005BB")
      caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner("A0006BB")
      caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner("A0007BB")
      caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner("A0008BB")
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A0001BB", "[]")
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A0002BB", "[]")
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A0003BB", "[]")
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A0004BB", "[]")
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A0005BB", "[]")
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A0006BB", "[]")
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A0007BB", "[]")
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A0008BB", "[]")

      caseNotesReconciliationService.generateReconciliationReport(activeOnly = false)

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("casenotes-reports-reconciliation-requested"),
        check {
          assertThat(it).containsEntry("casenotes-nomis-total", "8")
          assertThat(it).containsEntry("activeOnly", "false")
        },
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("casenotes-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "0")
          assertThat(it).containsEntry("success", "true")
        },
        isNull(),
      )
    }

    @Test
    fun `will output report failure telemetry`() = runTest {
      nomisApi.stubGetAllPrisonersInitialCount(1, 1)
      doThrow(RuntimeException("test")).whenever(caseNotesReconciliationService).generateReconciliationReport(1, false)

      caseNotesReconciliationService.generateReconciliationReport(activeOnly = false)

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("casenotes-reports-reconciliation-requested"),
        check {
          assertThat(it).containsEntry("casenotes-nomis-total", "1")
        },
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("casenotes-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("success", "false")
          assertThat(it).containsEntry("error", "test")
        },
        isNull(),
      )
    }

    @Test
    fun `will output report failure telemetry after excessive errors`() = runTest {
      nomisApi.stubGetAllPrisonersInitialCount(1, 1)
      nomisApi.stubGetAllPrisonersPage1()
      doThrow(RuntimeException("test")).whenever(caseNotesReconciliationService).checkMatch(any())

      caseNotesReconciliationService.generateReconciliationReport(activeOnly = false)

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("casenotes-reports-reconciliation-requested"),
        check {
          assertThat(it).containsEntry("casenotes-nomis-total", "1")
        },
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("casenotes-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("success", "false")
          assertThat(it).containsEntry("error", "Aborted: Too many page errors, at page 1")
        },
        isNull(),
      )
    }

    @Test
    fun `active only`() = runTest {
      nomisApi.stubGetActivePrisonersInitialCount(8)
      nomisApi.stubGetActivePrisonersPage(8, 0, 5, 5)
      nomisApi.stubGetActivePrisonersPage(8, 1, 3, 5)
      caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner("A0001TZ")
      caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner("A0002TZ")
      caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner("A0003TZ")
      caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner("A0004TZ")
      caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner("A0005TZ")
      caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner("A0006TZ")
      caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner("A0007TZ")
      caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner("A0008TZ")
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A0001TZ", "[]")
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A0002TZ", "[]")
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A0003TZ", "[]")
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A0004TZ", "[]")
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A0005TZ", "[]")
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A0006TZ", "[]")
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A0007TZ", "[]")
      caseNotesDpsApi.stubGetCaseNotesForPrisoner("A0008TZ", "[]")

      caseNotesReconciliationService.generateReconciliationReport(activeOnly = true)

      awaitReportFinished()

      verify(telemetryClient).trackEvent(
        eq("casenotes-reports-reconciliation-requested"),
        check {
          assertThat(it).containsEntry("casenotes-nomis-total", "8")
          assertThat(it).containsEntry("activeOnly", "true")
        },
        isNull(),
      )

      verify(telemetryClient).trackEvent(
        eq("casenotes-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("mismatch-count", "0")
          assertThat(it).containsEntry("success", "true")
        },
        isNull(),
      )
    }
  }

  @DisplayName("GET /casenotes/reconciliation/{prisonNumber}")
  @Nested
  inner class GenerateReconciliationReportForPrisoner {
    private val prisonNumber = "A0008BB"

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/casenotes/reconciliation/$prisonNumber")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/casenotes/reconciliation/$prisonNumber")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/casenotes/reconciliation/$prisonNumber")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `return 404 when offender not found`() {
        webTestClient.get().uri("/casenotes/reconciliation/AB1234C")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNotFound
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setup() {
        caseNotesMappingApi.stubGetByPrisoner(prisonNumber)
        caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner(prisonNumber)
        caseNotesDpsApi.stubGetCaseNotesForPrisoner(prisonNumber, "[]")
      }

      @Test
      fun `will return no differences`() {
        webTestClient.get().uri("/casenotes/reconciliation/$prisonNumber")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody().isEmpty

        verifyNoInteractions(telemetryClient)
      }

      @Test
      fun `will return mismatch with nomis`() {
        caseNotesNomisApiMockServer.stubGetCaseNotesForPrisoner(
          prisonNumber,
          PrisonerCaseNotesResponse(
            caseNotes = listOf(caseNoteResponse(1), caseNoteResponse(2)),
          ),
        )
        webTestClient.get().uri("/casenotes/reconciliation/$prisonNumber")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody()
          .jsonPath("offenderNo").isEqualTo(prisonNumber)
          .jsonPath("notes[0]").isEqualTo("mappings.size = 0, mappingsDpsDistinctIds.size = 0, nomisCaseNotes.size = 2, dpsCaseNotes.size = 0")
          .jsonPath("notes.length()").isEqualTo(1)
          .jsonPath("diffsForNomis").value<List<Int>> {
            assertThat(it).containsExactlyInAnyOrder(1, 2)
          }
          .jsonPath("diffsForDps.length()").isEqualTo(0)

        verify(telemetryClient).trackEvent(
          eq("casenotes-reports-reconciliation-mismatch-size-nomis"),
          any(),
          isNull(),
        )
      }
    }
  }

  @DisplayName("PUT /casenotes/{offenderNo}/{dpsId}/resynchronise")
  @Nested
  inner class RepairCaseNote {
    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.put().uri("/casenotes/$OFFENDER_NO/$DPS_CASE_NOTE_ID/resynchronise")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.put().uri("/casenotes/$OFFENDER_NO/$DPS_CASE_NOTE_ID/resynchronise")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.put().uri("/casenotes/$OFFENDER_NO/$DPS_CASE_NOTE_ID/resynchronise")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {

      @BeforeEach
      fun setUp() {
        caseNotesMappingApi.stubGetByDpsId(
          DPS_CASE_NOTE_ID,
          listOf(
            CaseNoteMappingDto(
              dpsCaseNoteId = DPS_CASE_NOTE_ID,
              nomisBookingId = NOMIS_BOOKING_ID,
              offenderNo = OFFENDER_NO,
              nomisCaseNoteId = NOMIS_CASE_NOTE_ID,
              mappingType = CaseNoteMappingDto.MappingType.DPS_CREATED,
            ),
          ),
        )
        caseNotesDpsApi.stubGetCaseNote(
          caseNote = dpsCaseNote().copy(
            caseNoteId = DPS_CASE_NOTE_ID,
            offenderIdentifier = OFFENDER_NO,
            type = "HPI",
            amendments = listOf(
              CaseNoteAmendment(
                additionalNoteText = "amendment",
                authorUserId = "54321",
                creationDateTime = LocalDateTime.parse("2024-05-06T07:08:09"),
                authorName = "ME SMITH",
                authorUserName = "ME",
              ),
            ),
          ),
        )
        caseNotesNomisApiMockServer.stubPutCaseNote(caseNoteId = NOMIS_CASE_NOTE_ID)

        webTestClient.put().uri("/casenotes/$OFFENDER_NO/$DPS_CASE_NOTE_ID/resynchronise")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will send telemetry event showing the update`() {
        verify(telemetryClient).trackEvent(
          ArgumentMatchers.eq("casenotes-repair-success"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `telemetry will contain key facts about the updated caseNote`() {
        verify(telemetryClient).trackEvent(
          ArgumentMatchers.eq("casenotes-repair-success"),
          check {
            assertThat(it).containsEntry("dpsCaseNoteId", DPS_CASE_NOTE_ID)
            assertThat(it).containsEntry("offenderNo", OFFENDER_NO)
          },
          isNull(),
        )
      }

      @Test
      fun `will call the mapping service to get the NOMIS caseNote id`() {
        caseNotesMappingApi.verify(getRequestedFor(urlMatching("/mapping/casenotes/dps-casenote-id/$DPS_CASE_NOTE_ID/all")))
      }

      @Test
      fun `will call back to DPS to get caseNote details`() {
        caseNotesDpsApi.verify(getRequestedFor(urlMatching("/case-notes/$OFFENDER_NO/$DPS_CASE_NOTE_ID")))
      }

      @Test
      fun `will update the caseNote in NOMIS with details of the DPS caseNote`() {
        caseNotesNomisApiMockServer.verify(
          putRequestedFor(urlEqualTo("/casenotes/${NOMIS_CASE_NOTE_ID}"))
            .withRequestBodyJsonPath("text", "contents of case note")
            .withRequestBodyJsonPath("amendments[0].text", "amendment")
            .withRequestBodyJsonPath("amendments[0].authorUsername", "ME")
            .withRequestBodyJsonPath("amendments[0].createdDateTime", "2024-05-06T07:08:09"),
        )
      }
    }

    @Nested
    inner class Exceptions {
      @Nested
      @DisplayName("when Nomis fails")
      inner class WhenUnexpectedDPsError {
        @BeforeEach
        fun setUp() {
          caseNotesMappingApi.stubGetByDpsId(
            DPS_CASE_NOTE_ID,
            listOf(
              CaseNoteMappingDto(
                dpsCaseNoteId = DPS_CASE_NOTE_ID,
                nomisBookingId = NOMIS_BOOKING_ID,
                offenderNo = OFFENDER_NO,
                nomisCaseNoteId = NOMIS_CASE_NOTE_ID,
                mappingType = CaseNoteMappingDto.MappingType.DPS_CREATED,
              ),
            ),
          )
          caseNotesDpsApi.stubGetCaseNote(
            caseNote = dpsCaseNote().copy(
              caseNoteId = DPS_CASE_NOTE_ID,
              offenderIdentifier = OFFENDER_NO,
              type = "HPI",
              amendments = listOf(
                CaseNoteAmendment(
                  additionalNoteText = "amendment",
                  authorUserId = "54321",
                  creationDateTime = LocalDateTime.parse("2024-05-06T07:08:09"),
                  authorName = "ME SMITH",
                  authorUserName = "ME",
                ),
              ),
            ),
          )
          caseNotesNomisApiMockServer.stubPutCaseNoteError(caseNoteId = NOMIS_CASE_NOTE_ID)

          webTestClient.put().uri("/casenotes/$OFFENDER_NO/$DPS_CASE_NOTE_ID/resynchronise")
            .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
            .exchange()
            .expectStatus().is5xxServerError
        }

        @Test
        fun `will send telemetry event showing it failed to update`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              ArgumentMatchers.eq("casenotes-repair-failed"),
              check {
                assertThat(it).containsEntry("dpsCaseNoteId", DPS_CASE_NOTE_ID)
                assertThat(it).containsEntry("offenderNo", OFFENDER_NO)
              },
              isNull(),
            )
          }
        }
      }
    }
  }

  private fun awaitReportFinished() {
    await untilAsserted { verify(telemetryClient).trackEvent(eq("casenotes-reports-reconciliation-report"), any(), isNull()) }
  }
}
