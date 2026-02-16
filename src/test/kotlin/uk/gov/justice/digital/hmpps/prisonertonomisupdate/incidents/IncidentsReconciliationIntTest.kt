package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.IncidentsDpsApiExtension.Companion.incidentsDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.ReportWithDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.StatusHistory
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import java.time.LocalDateTime
import kotlin.jvm.java

class IncidentsReconciliationIntTest(
  @Autowired private val incidentsReconciliationService: IncidentsReconciliationService,
  @Autowired private val incidentsNomisApi: IncidentsNomisApiMockServer,
) : SqsIntegrationTestBase() {

  @DisplayName("Incidents reconciliation report")
  @Nested
  inner class GenerateIncidentsReconciliationReport {

    @BeforeEach
    fun setUp() {
      incidentsNomisApi.stubGetIncidentAgencies()

      incidentsNomisApi.stubGetReconciliationOpenIncidentIds("ASI", 33, 35)
      incidentsNomisApi.stubGetReconciliationOpenIncidentIds("BFI", 36, 38)
      incidentsNomisApi.stubGetReconciliationOpenIncidentIds("WWI", 39, 41)
      incidentsNomisApi.stubGetIncident(33)
      incidentsNomisApi.stubGetIncidents(34, 41)
      incidentsDpsApi.stubGetIncidents(33, 41)
    }

    @Nested
    @DisplayName("Happy Path")
    inner class HappyPath {

      @BeforeEach
      fun setUp() {
        incidentsDpsApi.stubGetIncidentCounts()
        incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts("ASI")
        incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts("BFI")
        incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts("WWI")
      }

      @Test
      fun `will successfully finish report with no errors`() = runTest {
        incidentsReconciliationService.incidentsReconciliation()

        verify(telemetryClient).trackEvent(
          eq("incidents-reports-reconciliation-requested"),
          check { assertThat(it).containsEntry("prisonCount", "3") },
          isNull(),
        )

        awaitReportFinished()
        verify(telemetryClient).trackEvent(
          eq("incidents-reports-reconciliation-report"),
          check {
            assertThat(it).containsEntry("mismatch-count", "0")
            assertThat(it).containsEntry("success", "true")
          },
          isNull(),
        )
      }

      @Test
      fun `will output report requested telemetry`() = runTest {
        incidentsReconciliationService.incidentsReconciliation()
        awaitReportFinished()
        verify(telemetryClient).trackEvent(
          eq("incidents-reports-reconciliation-requested"),
          check { assertThat(it).containsEntry("prisonCount", "3") },
          isNull(),
        )
      }

      @Test
      fun `will call incidents api for open and closed counts for each agency`() = runTest {
        incidentsReconciliationService.incidentsReconciliation()

        awaitReportFinished()
        await untilAsserted {
          incidentsDpsApi.verifyGetIncidentCounts(6)
        }
      }

      @Test
      fun `will call incidents api for each open incident details - 3 per agency`() = runTest {
        incidentsReconciliationService.incidentsReconciliation()

        waitForAnyProcessingToComplete("incidents-reports-reconciliation-report")
        await untilAsserted {
          incidentsDpsApi.verifyGetIncidentDetail(9)
        }
      }

      @Test
      fun `will not invoke mismatch telemetry`() = runTest {
        incidentsDpsApi.stubGetIncidentsWithError(HttpStatus.INTERNAL_SERVER_ERROR)

        incidentsReconciliationService.incidentsReconciliation()

        awaitReportFinished()

        verify(telemetryClient, times(0)).trackEvent(
          eq("incidents-reports-reconciliation-mismatch"),
          any(),
          isNull(),
        )

        verify(telemetryClient, times(0)).trackEvent(
          eq("incidents-reports-reconciliation-detail-mismatch"),
          any(),
          isNull(),
        )

        verify(telemetryClient, times(0)).trackEvent(
          eq("incidents-reports-reconciliation-detail-mismatch-error"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("DPS Draft Status")
    inner class Draft {
      @Nested
      inner class DraftHappyPath {
        @BeforeEach
        fun setUp() {
          incidentsDpsApi.stubGetIncidentCounts() // dps count needs to be less
          incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts("ASI", open = 4)
          incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts("BFI")
          incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts("WWI")

          incidentsDpsApi.stubGetIncidentByNomisId(
            33,
            response = dpsIncident().copy(
              type = ReportWithDetails.Type.ABSCOND_1,
              status = ReportWithDetails.Status.DRAFT,
              historyOfStatuses = listOf(
                StatusHistory(
                  status = StatusHistory.Status.AWAITING_REVIEW,
                  changedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
                  changedBy = "JSMITH",
                ),
              ),
            ),
          )
          incidentsDpsApi.stubGetIncidents(34, 41)
        }

        @Test
        fun `will successfully finish report with no errors`() = runTest {
          incidentsReconciliationService.incidentsReconciliation()
          awaitReportFinished()

          verify(telemetryClient).trackEvent(
            eq("incidents-reports-reconciliation-requested"),
            check { assertThat(it).containsEntry("prisonCount", "3") },
            isNull(),
          )

          awaitReportFinished()
          verify(telemetryClient).trackEvent(
            eq("incidents-reports-reconciliation-report"),
            check {
              assertThat(it).containsEntry("mismatch-count", "0")
              assertThat(it).containsEntry("success", "true")
            },
            isNull(),
          )
        }

        @Test
        fun `will output report requested telemetry`() = runTest {
          incidentsReconciliationService.incidentsReconciliation()
          awaitReportFinished()
          verify(telemetryClient).trackEvent(
            eq("incidents-reports-reconciliation-requested"),
            check { assertThat(it).containsEntry("prisonCount", "3") },
            isNull(),
          )
        }

        @Test
        fun `will call incidents api for open and closed counts for each agency`() = runTest {
          incidentsReconciliationService.incidentsReconciliation()

          awaitReportFinished()
          await untilAsserted {
            incidentsDpsApi.verifyGetIncidentCounts(6)
          }
        }

        @Test
        fun `will call incidents api for each open incident details - 3 per agency`() = runTest {
          incidentsReconciliationService.incidentsReconciliation()

          waitForAnyProcessingToComplete("incidents-reports-reconciliation-report")
          await untilAsserted {
            incidentsDpsApi.verifyGetIncidentDetail(9)
          }
        }

        @Test
        fun `will not invoke mismatch telemetry`() = runTest {
          incidentsReconciliationService.incidentsReconciliation()
          awaitReportFinished()

          verify(telemetryClient, times(0)).trackEvent(
            eq("incidents-reports-reconciliation-mismatch"),
            any(),
            isNull(),
          )

          verify(telemetryClient, times(0)).trackEvent(
            eq("incidents-reports-reconciliation-detail-mismatch"),
            any(),
            isNull(),
          )

          verify(telemetryClient, times(0)).trackEvent(
            eq("incidents-reports-reconciliation-detail-mismatch-error"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      inner class DraftWithNoOpenStatusInHistory {
        @BeforeEach
        fun setUp() {
          incidentsDpsApi.stubGetIncidentCounts() // dps count needs to be less
          incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts("ASI", open = 4)
          incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts("BFI")
          incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts("WWI")

          incidentsDpsApi.stubGetIncidentByNomisId(
            33,
            response = dpsIncident().copy(
              status = ReportWithDetails.Status.DRAFT,
              historyOfStatuses = listOf(),
            ),
          )
          incidentsDpsApi.stubGetIncidents(34, 41)
          incidentsNomisApi.stubGetIncident(33)
        }

        @Test
        fun `will show status mismatch differences in report`() = runTest {
          incidentsReconciliationService.incidentsReconciliation()

          awaitReportFinished()
          verify(telemetryClient).trackEvent(
            eq("incidents-reports-reconciliation-report"),
            check {
              assertThat(it).containsEntry("mismatch-count", "1")
              assertThat(it).containsEntry("success", "true")
              assertThat(it).containsEntry("ASI", "open-dps=3:open-nomis=4; closed-dps=3:closed-nomis=3")
              assertThat(it).doesNotContainKeys("WWI")
            },
            isNull(),
          )

          // No count mismatch
          verify(telemetryClient, times(0)).trackEvent(
            eq("incidents-reports-reconciliation-mismatch"),
            any(),
            isNull(),
          )

          verify(telemetryClient).trackEvent(
            eq("incidents-reports-reconciliation-detail-mismatch"),
            check {
              assertThat(it).containsEntry("nomisId", "33")
              assertThat(it).containsKey("dpsId")
              assertThat(it).containsEntry("verdict", "status mismatch")
              assertThat(it).containsEntry(
                "nomis",
                "IncidentReportDetail(type=ATT_ESC_E, status=AWAN, reportedBy=FSTAFF_GEN, reportedDateTime=2021-07-07T10:35:17, offenderParties=[A1234BC, A1234BD], totalStaffParties=2, totalQuestions=2, totalRequirements=1, totalResponses=3)",
              )
              assertThat(it).containsEntry(
                "dps",
                "IncidentReportDetail(type=ATT_ESC_E, status=DRAFT, reportedBy=FSTAFF_GEN, reportedDateTime=2021-07-07T10:35:17, offenderParties=[A1234BC, A1234BD], totalStaffParties=2, totalQuestions=2, totalRequirements=1, totalResponses=3)",
              )
            },
            isNull(),
          )
        }
      }
    }

    @Nested
    @DisplayName("Counts Unhappy Path")
    inner class CountsUnHappyPath {

      @BeforeEach
      fun setUp() {
        incidentsNomisApi.stubGetIncidentAgencies()
        incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts(agencyId = "ASI", open = 2)
        incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts(agencyId = "BFI", open = 1, closed = 4)
        incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts(agencyId = "WWI")
      }

      @Test
      fun `will show mismatch counts in report`() = runTest {
        incidentsDpsApi.stubGetIncidentCounts()
        incidentsDpsApi.stubGetASIClosedIncidentCounts()
        incidentsReconciliationService.incidentsReconciliation()

        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("incidents-reports-reconciliation-report"),
          check {
            assertThat(it).containsEntry("mismatch-count", "2")
            assertThat(it).containsEntry("success", "true")
            assertThat(it).containsEntry("ASI", "open-dps=3:open-nomis=2; closed-dps=8:closed-nomis=3")
            assertThat(it).containsEntry("BFI", "open-dps=3:open-nomis=1; closed-dps=3:closed-nomis=4")
            assertThat(it).doesNotContainKeys("WWI")
          },
          isNull(),
        )

        verify(telemetryClient, times(1)).trackEvent(
          eq("incidents-reports-reconciliation-mismatch"),
          check {
            assertThat(it).containsEntry("agencyId", "ASI")
            assertThat(it).containsEntry("dpsOpenIncidents", "3")
            assertThat(it).containsEntry("nomisOpenIncidents", "2")
            assertThat(it).containsEntry("dpsClosedIncidents", "8")
            assertThat(it).containsEntry("nomisClosedIncidents", "3")
          },
          isNull(),
        )
        verify(telemetryClient, times(1)).trackEvent(
          eq("incidents-reports-reconciliation-mismatch"),
          check {
            assertThat(it).containsEntry("agencyId", "BFI")
            assertThat(it).containsEntry("dpsOpenIncidents", "3")
            assertThat(it).containsEntry("nomisOpenIncidents", "1")
            assertThat(it).containsEntry("dpsClosedIncidents", "3")
            assertThat(it).containsEntry("nomisClosedIncidents", "4")
          },
          isNull(),
        )
      }

      @Test
      fun `will not invoke detail mismatch`() {
        verify(telemetryClient, times(0)).trackEvent(
          eq("incidents-reports-reconciliation-detail-mismatch"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will complete a report even if some of the checks fail`() = runTest {
        incidentsDpsApi.stubGetIncidentsWithError(HttpStatus.INTERNAL_SERVER_ERROR)

        incidentsReconciliationService.incidentsReconciliation()

        awaitReportFinished()

        verify(telemetryClient, times(3)).trackEvent(
          eq("incidents-reports-reconciliation-mismatch-error"),
          any(),
          isNull(),
        )
      }
    }

    @Nested
    @DisplayName("Incident Detail Unhappy Path")
    inner class DetailUnHappyPath {

      @BeforeEach
      fun setUp() {
        incidentsDpsApi.stubGetIncidentCounts()
        incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts(agencyId = "ASI", open = 2)
        incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts(agencyId = "BFI", open = 1, closed = 4)
        incidentsNomisApi.stubGetReconciliationAgencyIncidentCounts(agencyId = "WWI")
      }

      @Test
      fun `will show mismatch counts in report`() = runTest {
        incidentsReconciliationService.incidentsReconciliation()

        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("incidents-reports-reconciliation-report"),
          check {
            assertThat(it).containsEntry("mismatch-count", "2")
            assertThat(it).containsEntry("success", "true")
            assertThat(it).containsEntry("ASI", "open-dps=3:open-nomis=2; closed-dps=3:closed-nomis=3")
            assertThat(it).containsEntry("BFI", "open-dps=3:open-nomis=1; closed-dps=3:closed-nomis=4")
            assertThat(it).doesNotContainKeys("WWI")
          },
          isNull(),
        )

        verify(telemetryClient, times(2)).trackEvent(
          eq("incidents-reports-reconciliation-mismatch"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will show mismatch differences in report`() = runTest {
        incidentsNomisApi.stubGetIncident(33, offenderParty = "Z4321YX", status = "INREQ", type = "ABSCOND", reportedDateTime = LocalDateTime.parse("2021-07-08T10:35:18"))

        incidentsReconciliationService.incidentsReconciliation()

        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("incidents-reports-reconciliation-report"),
          check {
            assertThat(it).containsEntry("mismatch-count", "2")
            assertThat(it).containsEntry("success", "true")
            assertThat(it).containsEntry("ASI", "open-dps=3:open-nomis=2; closed-dps=3:closed-nomis=3")
            assertThat(it).containsEntry("BFI", "open-dps=3:open-nomis=1; closed-dps=3:closed-nomis=4")
            assertThat(it).doesNotContainKeys("WWI")
          },
          isNull(),
        )

        verify(telemetryClient, times(2)).trackEvent(
          eq("incidents-reports-reconciliation-mismatch"),
          any(),
          isNull(),
        )

        verify(telemetryClient).trackEvent(
          eq("incidents-reports-reconciliation-detail-mismatch"),
          check {
            assertThat(it).containsEntry("nomisId", "33")
            assertThat(it).containsKey("dpsId")
            assertThat(it).containsEntry("verdict", "type mismatch")
            assertThat(it).containsEntry(
              "nomis",
              "IncidentReportDetail(type=ABSCOND, status=INREQ, reportedBy=FSTAFF_GEN, reportedDateTime=2021-07-08T10:35:18, offenderParties=[Z4321YX, A1234BD], totalStaffParties=2, totalQuestions=2, totalRequirements=1, totalResponses=3)",
            )
            assertThat(it).containsEntry(
              "dps",
              "IncidentReportDetail(type=ATT_ESC_E, status=AWAN, reportedBy=FSTAFF_GEN, reportedDateTime=2021-07-07T10:35:17, offenderParties=[A1234BC, A1234BD], totalStaffParties=2, totalQuestions=2, totalRequirements=1, totalResponses=3)",
            )
          },
          isNull(),
        )
      }

      @Test
      fun `will show mismatch reportedDateTime in report`() = runTest {
        incidentsNomisApi.stubGetIncident(33, reportedDateTime = LocalDateTime.parse("2021-07-08T10:35:18"))

        incidentsReconciliationService.incidentsReconciliation()

        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("incidents-reports-reconciliation-report"),
          check {
            assertThat(it).containsEntry("mismatch-count", "2")
            assertThat(it).containsEntry("success", "true")
            assertThat(it).containsEntry("ASI", "open-dps=3:open-nomis=2; closed-dps=3:closed-nomis=3")
            assertThat(it).containsEntry("BFI", "open-dps=3:open-nomis=1; closed-dps=3:closed-nomis=4")
            assertThat(it).doesNotContainKeys("WWI")
          },
          isNull(),
        )

        verify(telemetryClient, times(2)).trackEvent(
          eq("incidents-reports-reconciliation-mismatch"),
          any(),
          isNull(),
        )

        verify(telemetryClient).trackEvent(
          eq("incidents-reports-reconciliation-detail-mismatch"),
          check {
            assertThat(it).containsEntry("nomisId", "33")
            assertThat(it).containsKey("dpsId")
            assertThat(it).containsEntry("verdict", "reported date mismatch")
            assertThat(it).containsEntry(
              "nomis",
              "IncidentReportDetail(type=ATT_ESC_E, status=AWAN, reportedBy=FSTAFF_GEN, reportedDateTime=2021-07-08T10:35:18, offenderParties=[A1234BC, A1234BD], totalStaffParties=2, totalQuestions=2, totalRequirements=1, totalResponses=3)",
            )
            assertThat(it).containsEntry(
              "dps",
              "IncidentReportDetail(type=ATT_ESC_E, status=AWAN, reportedBy=FSTAFF_GEN, reportedDateTime=2021-07-07T10:35:17, offenderParties=[A1234BC, A1234BD], totalStaffParties=2, totalQuestions=2, totalRequirements=1, totalResponses=3)",
            )
          },
          isNull(),
        )
      }

      @Test
      fun `will show status mismatch differences in report`() = runTest {
        incidentsNomisApi.stubGetMismatchIncident()

        incidentsReconciliationService.incidentsReconciliation()

        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("incidents-reports-reconciliation-report"),
          check {
            assertThat(it).containsEntry("mismatch-count", "2")
            assertThat(it).containsEntry("success", "true")
            assertThat(it).containsEntry("ASI", "open-dps=3:open-nomis=2; closed-dps=3:closed-nomis=3")
            assertThat(it).containsEntry("BFI", "open-dps=3:open-nomis=1; closed-dps=3:closed-nomis=4")
            assertThat(it).doesNotContainKeys("WWI")
          },
          isNull(),
        )

        verify(telemetryClient, times(2)).trackEvent(
          eq("incidents-reports-reconciliation-mismatch"),
          any(),
          isNull(),
        )

        verify(telemetryClient).trackEvent(
          eq("incidents-reports-reconciliation-detail-mismatch"),
          check {
            assertThat(it).containsEntry("nomisId", "33")
            assertThat(it).containsKey("dpsId")
            assertThat(it).containsEntry("verdict", "Staff parties mismatch")
            assertThat(it).containsEntry(
              "nomis",
              "IncidentReportDetail(type=ATT_ESC_E, status=INREQ, reportedBy=FSTAFF_GEN, reportedDateTime=2021-07-07T10:35:17, offenderParties=[Z4321YX, A1234BD], totalStaffParties=0, totalQuestions=1, totalRequirements=0, totalResponses=0)",
            )
            assertThat(it).containsEntry(
              "dps",
              "IncidentReportDetail(type=ATT_ESC_E, status=AWAN, reportedBy=FSTAFF_GEN, reportedDateTime=2021-07-07T10:35:17, offenderParties=[A1234BC, A1234BD], totalStaffParties=2, totalQuestions=2, totalRequirements=1, totalResponses=3)",
            )
          },
          isNull(),
        )
      }

      @Test
      fun `will show response mismatch differences in report`() = runTest {
        incidentsNomisApi.stubGetMismatchResponsesForIncident()

        incidentsReconciliationService.incidentsReconciliation()

        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("incidents-reports-reconciliation-report"),
          check {
            assertThat(it).containsEntry("mismatch-count", "2")
            assertThat(it).containsEntry("success", "true")
            assertThat(it).containsEntry("ASI", "open-dps=3:open-nomis=2; closed-dps=3:closed-nomis=3")
            assertThat(it).containsEntry("BFI", "open-dps=3:open-nomis=1; closed-dps=3:closed-nomis=4")
            assertThat(it).doesNotContainKeys("WWI")
          },
          isNull(),
        )

        verify(telemetryClient, times(2)).trackEvent(
          eq("incidents-reports-reconciliation-mismatch"),
          any(),
          isNull(),
        )

        verify(telemetryClient).trackEvent(
          eq("incidents-reports-reconciliation-detail-mismatch"),
          check {
            assertThat(it).containsEntry("nomisId", "33")
            assertThat(it).containsKey("dpsId")
            assertThat(it).containsEntry("verdict", "responses mismatch for question: 1234")
            assertThat(it).containsEntry(
              "nomis",
              "IncidentReportDetail(type=ATT_ESC_E, status=AWAN, reportedBy=FSTAFF_GEN, reportedDateTime=2021-07-07T10:35:17, offenderParties=[A1234BC, A1234BD], totalStaffParties=2, totalQuestions=2, totalRequirements=1, totalResponses=3)",
            )
            assertThat(it).containsEntry(
              "dps",
              "IncidentReportDetail(type=ATT_ESC_E, status=AWAN, reportedBy=FSTAFF_GEN, reportedDateTime=2021-07-07T10:35:17, offenderParties=[A1234BC, A1234BD], totalStaffParties=2, totalQuestions=2, totalRequirements=1, totalResponses=3)",
            )
          },
          isNull(),
        )
      }

      @Test
      fun `will show response mismatch reported date in report`() = runTest {
        incidentsNomisApi.stubGetMismatchResponsesForIncident()

        incidentsReconciliationService.incidentsReconciliation()

        awaitReportFinished()

        verify(telemetryClient).trackEvent(
          eq("incidents-reports-reconciliation-report"),
          check {
            assertThat(it).containsEntry("mismatch-count", "2")
            assertThat(it).containsEntry("success", "true")
            assertThat(it).containsEntry("ASI", "open-dps=3:open-nomis=2; closed-dps=3:closed-nomis=3")
            assertThat(it).containsEntry("BFI", "open-dps=3:open-nomis=1; closed-dps=3:closed-nomis=4")
            assertThat(it).doesNotContainKeys("WWI")
          },
          isNull(),
        )

        verify(telemetryClient, times(2)).trackEvent(
          eq("incidents-reports-reconciliation-mismatch"),
          any(),
          isNull(),
        )

        verify(telemetryClient).trackEvent(
          eq("incidents-reports-reconciliation-detail-mismatch"),
          check {
            assertThat(it).containsEntry("nomisId", "33")
            assertThat(it).containsKey("dpsId")
            assertThat(it).containsEntry("verdict", "responses mismatch for question: 1234")
            assertThat(it).containsEntry(
              "nomis",
              "IncidentReportDetail(type=ATT_ESC_E, status=AWAN, reportedBy=FSTAFF_GEN, reportedDateTime=2021-07-07T10:35:17, offenderParties=[A1234BC, A1234BD], totalStaffParties=2, totalQuestions=2, totalRequirements=1, totalResponses=3)",
            )
            assertThat(it).containsEntry(
              "dps",
              "IncidentReportDetail(type=ATT_ESC_E, status=AWAN, reportedBy=FSTAFF_GEN, reportedDateTime=2021-07-07T10:35:17, offenderParties=[A1234BC, A1234BD], totalStaffParties=2, totalQuestions=2, totalRequirements=1, totalResponses=3)",
            )
          },
          isNull(),
        )
      }

      @Test
      fun `will complete a report even if some of the checks fail`() = runTest {
        incidentsDpsApi.stubGetIncidentsWithError(HttpStatus.INTERNAL_SERVER_ERROR)

        incidentsReconciliationService.incidentsReconciliation()

        awaitReportFinished()

        verify(telemetryClient, times(3)).trackEvent(
          eq("incidents-reports-reconciliation-mismatch-error"),
          any(),
          isNull(),
        )
      }
    }
  }

  private fun awaitReportFinished() {
    await untilAsserted {
      verify(telemetryClient).trackEvent(
        eq("incidents-reports-reconciliation-report"),
        any(),
        isNull(),
      )
    }
  }

  @DisplayName("GET /incidents/reconciliation/{nomisIncidentId}")
  @Nested
  inner class GenerateReconciliationReportForIncident {
    private val nomisIncidentId = 1234L
    private val mismatchNomisIncidentId = 33

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.get().uri("/incidents/reconciliation/$nomisIncidentId")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.get().uri("/incidents/reconciliation/$nomisIncidentId")
          .headers(setAuthorisation(roles = listOf("BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.get().uri("/incidents/reconciliation/$nomisIncidentId")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class Validation {
      @Test
      fun `return 404 when incident not found`() {
        incidentsNomisApi.stubGetIncident(HttpStatus.NOT_FOUND)
        webTestClient.get().uri("/incidents/reconciliation/99999")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus().isNotFound
          .expectBody()
          .jsonPath("userMessage").isEqualTo("Not Found: Incident not found 99999")
      }
    }

    @Nested
    inner class HappyPath {

      @BeforeEach
      fun setup() {
        incidentsDpsApi.stubGetIncidentByNomisId()
      }

      @Test
      fun `will return no differences`() {
        incidentsNomisApi.stubGetIncident()

        webTestClient.get().uri("/incidents/reconciliation/$nomisIncidentId")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody().isEmpty

        verifyNoInteractions(telemetryClient)
      }

      @Test
      fun `will return Dps mismatch with Nomis`() {
        incidentsNomisApi.stubGetMismatchResponsesForIncident()
        incidentsDpsApi.stubGetIncidentByNomisId(33)

        val mismatch = webTestClient.get().uri("/incidents/reconciliation/$mismatchNomisIncidentId")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody(MismatchIncident::class.java)
          .returnResult()
          .responseBody!!

        assertThat(mismatch.nomisIncident!!.totalResponses).isEqualTo(3)
        assertThat(mismatch.dpsIncident!!.totalResponses).isEqualTo(3)
        assertThat(mismatch.verdict).isEqualTo("responses mismatch for question: 1234")

        verify(telemetryClient).trackEvent(
          eq("incidents-reports-reconciliation-detail-mismatch"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will pass reconciliation if invalid Nomis data - multipleAnswers for a single answer question`() {
        incidentsNomisApi.stubGetIncident(
          incidentResponse().copy(
            questions = listOf(question1WithInvalidAnswerCount, question2With2Answers),
          ),
        )

        webTestClient.get().uri("/incidents/reconciliation/$nomisIncidentId")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody().isEmpty

        verify(telemetryClient).trackEvent(
          eq("incidents-reports-reconciliation-mismatch-multiple-answers-ignored"),
          check {
            assertThat(it).containsEntry("nomisIncidentId", "$nomisIncidentId")
            assertThat(it).containsKey("dpsIncidentId")
            assertThat(it).containsEntry("questionId", "1234")
            assertThat(it).containsEntry("hasMultipleAnswers", "false")
            assertThat(it).containsEntry("totalResponses", "2")
          },
          isNull(),
        )
      }

      @Test
      fun `will pass reconciliation if questions with no answers for both Nomis and Dps`() {
        val dpsResponse = dpsIncident()
        incidentsDpsApi.stubGetIncidentByNomisId(
          response = dpsResponse.copy(
            questions = dpsResponse.questions + dpsQuestionWithNoAnswers,
          ),
        )

        val nomisResponse = incidentResponse()
        incidentsNomisApi.stubGetIncident(
          nomisResponse.copy(
            questions = nomisResponse.questions + nomisQuestionWithNoAnswers,
          ),
        )

        webTestClient.get().uri("/incidents/reconciliation/$nomisIncidentId")
          .headers(setAuthorisation(roles = listOf("PRISONER_TO_NOMIS__UPDATE__RW")))
          .exchange()
          .expectStatus()
          .isOk
          .expectBody().isEmpty

        verifyNoInteractions(telemetryClient)
      }
    }
  }
}
