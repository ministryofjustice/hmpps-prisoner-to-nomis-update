package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OfficialVisitMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OfficialVisitResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncOfficialVisit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsDpsApiMockServer.Companion.syncOfficialVisitor
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsNomisApiMockServer.Companion.officialVisitResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.OfficialVisitsNomisApiMockServer.Companion.officialVisitor
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.AttendanceType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.SyncOfficialVisit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.VisitCompletionType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits.model.VisitStatusType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.time.LocalDate
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(
  OfficialVisitsAllReconciliationService::class,
  OfficialVisitsNomisApiService::class,
  OfficialVisitsDpsApiService::class,
  OfficialVisitsConfiguration::class,
  OfficialVisitsMappingService::class,
  RetryApiService::class,
  OfficialVisitsNomisApiMockServer::class,
  OfficialVisitsMappingApiMockServer::class,
)
class OfficialVisitsAllReconciliationServiceTest {
  @MockitoBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  private lateinit var nomisApi: OfficialVisitsNomisApiMockServer

  @Autowired
  private lateinit var mappingService: OfficialVisitsMappingApiMockServer

  private val dpsApi = OfficialVisitsDpsApiExtension.dpsOfficialVisitsServer

  @Autowired
  private lateinit var service: OfficialVisitsAllReconciliationService

  @Nested
  inner class CheckVisitsMatch {
    val nomisVisitId = 1234L
    val dpsVisitId = 4321L
    val offenderNo = "A1234KT"

    @Nested
    inner class WhenVisitsMatch {
      @BeforeEach
      fun setUp() {
        stubVisits(officialVisitResponse(), syncOfficialVisit())
      }

      @Test
      fun `will return null`() = runTest {
        assertThat(service.checkVisitsMatch(nomisVisitId)).isNull()
      }
    }

    @Nested
    inner class WhenVisitDateDoesNotMatch {
      @BeforeEach
      fun setUp() {
        stubVisits(
          nomisVisit = officialVisitResponse().copy(
            offenderNo = offenderNo,
            startDateTime = "2026-03-29T10:00".dateTime(),
          ),
          dpsVisit = syncOfficialVisit().copy(
            prisonerNumber = offenderNo,
            visitDate = "2026-03-30".date(),
            startTime = "10:00",
          ),
        )
      }

      @Test
      fun `will report and return mismatch`() = runTest {
        assertThat(service.checkVisitsMatch(nomisVisitId)).isNotNull()
        verify(telemetryClient).trackEvent(
          eq("official-visits-all-reconciliation-mismatch"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["dpsVisitId"]).isEqualTo(dpsVisitId.toString())
            assertThat(it["reason"]).isEqualTo("different-visit-details")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenVisitTimeDoesNotMatch {
      @BeforeEach
      fun setUp() {
        stubVisits(
          nomisVisit = officialVisitResponse().copy(
            offenderNo = offenderNo,
            startDateTime = "2026-03-30T10:15".dateTime(),
          ),
          dpsVisit = syncOfficialVisit().copy(
            prisonerNumber = offenderNo,
            visitDate = "2026-03-30".date(),
            startTime = "10:00",
          ),
        )
      }

      @Test
      fun `will report and return mismatch`() = runTest {
        assertThat(service.checkVisitsMatch(nomisVisitId)).isNotNull()
        verify(telemetryClient).trackEvent(
          eq("official-visits-all-reconciliation-mismatch"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["dpsVisitId"]).isEqualTo(dpsVisitId.toString())
            assertThat(it["reason"]).isEqualTo("different-visit-details")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenVisitEndTimeDoesNotMatch {
      @BeforeEach
      fun setUp() {
        stubVisits(
          nomisVisit = officialVisitResponse().copy(
            offenderNo = offenderNo,
            startDateTime = "2026-03-30T10:00".dateTime(),
            endDateTime = "2026-03-30T11:15".dateTime(),
          ),
          dpsVisit = syncOfficialVisit().copy(
            prisonerNumber = offenderNo,
            visitDate = "2026-03-30".date(),
            startTime = "10:00",
            endTime = "11:30",
          ),
        )
      }

      @Test
      fun `will report and return mismatch`() = runTest {
        assertThat(service.checkVisitsMatch(nomisVisitId)).isNotNull()
        verify(telemetryClient).trackEvent(
          eq("official-visits-all-reconciliation-mismatch"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["dpsVisitId"]).isEqualTo(dpsVisitId.toString())
            assertThat(it["reason"]).isEqualTo("different-visit-details")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenPrisonDoesNotMatch {
      @BeforeEach
      fun setUp() {
        stubVisits(
          nomisVisit = officialVisitResponse().copy(
            offenderNo = offenderNo,
            prisonId = "MDI",
          ),
          dpsVisit = syncOfficialVisit().copy(
            prisonerNumber = offenderNo,
            prisonCode = "WWI",
          ),
        )
      }

      @Test
      fun `will report and return mismatch`() = runTest {
        assertThat(service.checkVisitsMatch(nomisVisitId)).isNotNull()
        verify(telemetryClient).trackEvent(
          eq("official-visits-all-reconciliation-mismatch"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["dpsVisitId"]).isEqualTo(dpsVisitId.toString())
            assertThat(it["reason"]).isEqualTo("different-visit-details")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenPrisonNumberDoesNotMatch {
      @BeforeEach
      fun setUp() {
        stubVisits(
          nomisVisit = officialVisitResponse().copy(
            offenderNo = offenderNo,
          ),
          dpsVisit = syncOfficialVisit().copy(
            prisonerNumber = "A9999KT",
          ),
        )
      }

      @Test
      fun `will report and return mismatch`() = runTest {
        assertThat(service.checkVisitsMatch(nomisVisitId)).isNotNull()
        verify(telemetryClient).trackEvent(
          eq("official-visits-all-reconciliation-mismatch"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["dpsVisitId"]).isEqualTo(dpsVisitId.toString())
            assertThat(it["reason"]).isEqualTo("different-visit-details")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenStatusDoesNotMatch {
      @BeforeEach
      fun setUp() {
        stubVisits(
          nomisVisit = officialVisitResponse().copy(
            offenderNo = offenderNo,
            visitStatus = CodeDescription("CANC", "Cancelled"),
          ),
          dpsVisit = syncOfficialVisit().copy(
            prisonerNumber = offenderNo,
            statusCode = VisitStatusType.COMPLETED,
          ),
        )
      }

      @Test
      fun `will report and return mismatch`() = runTest {
        assertThat(service.checkVisitsMatch(nomisVisitId)).isNotNull()
        verify(telemetryClient).trackEvent(
          eq("official-visits-all-reconciliation-mismatch"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["dpsVisitId"]).isEqualTo(dpsVisitId.toString())
            assertThat(it["reason"]).isEqualTo("different-visit-details")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenOutcomeDoesNotMatch {
      @BeforeEach
      fun setUp() {
        stubVisits(
          nomisVisit = officialVisitResponse().copy(
            offenderNo = offenderNo,
            visitStatus = CodeDescription("CANC", "Cancelled"),
            cancellationReason = CodeDescription("OFFCANC", "Offender Cancelled"),
          ),
          dpsVisit = syncOfficialVisit().copy(
            prisonerNumber = offenderNo,
            statusCode = VisitStatusType.CANCELLED,
            completionCode = VisitCompletionType.VISITOR_CANCELLED,
          ),
        )
      }

      @Test
      fun `will report and return mismatch`() = runTest {
        assertThat(service.checkVisitsMatch(nomisVisitId)).isNotNull()
        verify(telemetryClient).trackEvent(
          eq("official-visits-all-reconciliation-mismatch"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["dpsVisitId"]).isEqualTo(dpsVisitId.toString())
            assertThat(it["reason"]).isEqualTo("different-visit-details")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCancellationReasonNotPresentInNOMISButCompletedNormally {
      @BeforeEach
      fun setUp() {
        stubVisits(
          nomisVisit = officialVisitResponse().copy(
            offenderNo = offenderNo,
            visitStatus = CodeDescription("NORM", "Normal Completion"),
            cancellationReason = null,
          ),
          dpsVisit = syncOfficialVisit().copy(
            prisonerNumber = offenderNo,
            statusCode = VisitStatusType.COMPLETED,
            completionCode = VisitCompletionType.NORMAL,
          ),
        )
      }

      @Test
      fun `will not report or return mismatch`() = runTest {
        assertThat(service.checkVisitsMatch(nomisVisitId)).isNull()
      }
    }

    @Nested
    inner class WhenVisitorIdsDoNotMatch {
      @BeforeEach
      fun setUp() {
        stubVisits(
          nomisVisit = officialVisitResponse().copy(
            offenderNo = offenderNo,
            visitors = listOf(officialVisitor().copy(personId = 9876)),
          ),
          dpsVisit = syncOfficialVisit().copy(
            prisonerNumber = offenderNo,
            visitors = listOf(syncOfficialVisitor().copy(contactId = 9877)),
          ),
        )
      }

      @Test
      fun `will report and return mismatch`() = runTest {
        assertThat(service.checkVisitsMatch(nomisVisitId)).isNotNull()
        verify(telemetryClient).trackEvent(
          eq("official-visits-all-reconciliation-mismatch"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["dpsVisitId"]).isEqualTo(dpsVisitId.toString())
            assertThat(it["reason"]).isEqualTo("different-visit-details")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenVisitorAttendanceDoNotMatch {
      @BeforeEach
      fun setUp() {
        stubVisits(
          nomisVisit = officialVisitResponse().copy(
            offenderNo = offenderNo,
            visitors = listOf(officialVisitor().copy(visitorAttendanceOutcome = CodeDescription("ATT", "Attended"))),
          ),
          dpsVisit = syncOfficialVisit().copy(
            prisonerNumber = offenderNo,
            visitors = listOf(syncOfficialVisitor().copy(attendanceCode = AttendanceType.ABSENT)),
          ),
        )
      }

      @Test
      fun `will report and return mismatch`() = runTest {
        assertThat(service.checkVisitsMatch(nomisVisitId)).isNotNull()
        verify(telemetryClient).trackEvent(
          eq("official-visits-all-reconciliation-mismatch"),
          check {
            assertThat(it["offenderNo"]).isEqualTo(offenderNo)
            assertThat(it["nomisVisitId"]).isEqualTo(nomisVisitId.toString())
            assertThat(it["dpsVisitId"]).isEqualTo(dpsVisitId.toString())
            assertThat(it["reason"]).isEqualTo("different-visit-details")
          },
          isNull(),
        )
      }
    }

    fun stubVisits(nomisVisit: OfficialVisitResponse, dpsVisit: SyncOfficialVisit) {
      nomisApi.stubGetOfficialVisit(nomisVisitId, response = nomisVisit.copy(visitId = nomisVisitId))
      dpsApi.stubGetOfficialVisit(dpsVisitId, response = dpsVisit.copy(officialVisitId = dpsVisitId))
      mappingService.stubGetVisitByNomisIdOrNull(
        nomisVisitId = nomisVisitId,
        mapping = OfficialVisitMappingDto(
          dpsId = dpsVisitId.toString(),
          nomisId = nomisVisitId,
          mappingType = OfficialVisitMappingDto.MappingType.MIGRATED,
        ),
      )
    }
  }
}

private fun String.date() = LocalDate.parse(this)
private fun String.dateTime() = LocalDateTime.parse(this)
