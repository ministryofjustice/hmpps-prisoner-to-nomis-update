package uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment

import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.serverError
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment.BedAssignmentDpsApiExtension.Companion.bedAssignmentApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment.model.BedAssignment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.PrisonerSelection
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.PrisonerSelection.ALL_PRISONERS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BedAssignmentHistoryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerBedAssignmentHistoryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.util.UUID

@SpringAPIServiceTest
@Import(
  BedAssignmentReconciliationService::class,
  BedAssignmentNomisApiService::class,
  BedAssignmentDpsApiService::class,
  BedAssignmentMappingApiService::class,
  BedAssignmentNomisApiMockServer::class,
  BedAssignmentMappingApiMockServer::class,
  BedAssignmentConfiguration::class,
  NomisApiService::class,
  RetryApiService::class,
)
class BedAssignmentReconciliationServiceTest {
  @MockitoBean
  private lateinit var telemetryClient: TelemetryClient

  @Autowired
  private lateinit var service: BedAssignmentReconciliationService

  @Autowired
  private lateinit var bedAssignmentNomisApi: BedAssignmentNomisApiMockServer

  @Autowired
  private lateinit var mappingApi: BedAssignmentMappingApiMockServer

  private val offenderNo = "A0001AA"
  private val nomis = nomisBedAssignment()
  private val dps = dpsBedAssignment()

  private fun stubAssignments(
    nomisAssignments: List<BedAssignmentHistoryResponse> = listOf(nomis),
    dpsAssignments: List<BedAssignment> = listOf(dps),
  ) {
    bedAssignmentNomisApi.stubGetBedAssignmentsForPrisoner(offenderNo, PrisonerBedAssignmentHistoryResponse(nomisAssignments))
    bedAssignmentApi.stubGetBedAssignments(offenderNo, dpsAssignments)
    mappingApi.stubGetByNomisId(nomis.bookingId, nomis.sequence, bedAssignmentMapping(dps.id.toString()))
  }

  private fun difference(property: String, dpsValue: Any?, nomisValue: Any?) = BedAssignmentDifference(
    "bed-assignments[12345-1].$property",
    dpsValue,
    nomisValue,
    dps.id.toString(),
    "12345-1",
  )

  @Nested
  inner class CheckMatch {
    @Test
    fun `matching assignments return no mismatch`() = runTest {
      stubAssignments()

      assertThat(service.manualCheckBedAssignments(offenderNo)).isNull()
      verifyNoInteractions(telemetryClient)
    }

    @Test
    fun `empty histories return no mismatch`() = runTest {
      stubAssignments(emptyList(), emptyList())

      assertThat(service.checkBedAssignments(offenderNo)).isNull()
      verifyNoInteractions(telemetryClient)
    }

    @Test
    fun `checks assignments across bookings regardless of order`() = runTest {
      val previousNomis = nomis.copy(bookingId = 9876, assignmentEndDateTime = nomis.assignmentDateTime.plusDays(1))
      val previousDps = dps.copy(id = UUID.randomUUID(), endAt = previousNomis.assignmentEndDateTime)
      stubAssignments(listOf(previousNomis, nomis), listOf(dps, previousDps))
      mappingApi.stubGetByNomisId(9876, 1, bedAssignmentMapping(previousDps.id.toString(), bookingId = 9876))

      assertThat(service.checkBedAssignments(offenderNo)).isNull()
    }

    @Test
    fun `reports all differing fields and emits telemetry`() = runTest {
      val changed = dps.copy(locationKey = "MDI-B-1-001", startAt = dps.startAt.plusHours(1), endAt = dps.startAt.plusDays(1))
      stubAssignments(dpsAssignments = listOf(changed))

      assertThat(service.checkBedAssignments(offenderNo)?.differences).containsExactly(
        difference("living-unit", changed.locationKey, nomis.livingUnitDescription),
        difference("start-date", changed.startAt, nomis.assignmentDateTime),
        difference("end-date", changed.endAt, null),
      )
      verify(telemetryClient).trackEvent(
        eq("bed-assignment-reports-reconciliation-mismatch"),
        check {
          assertThat(it).containsEntry("prisoner", offenderNo)
          assertThat(it).containsKeys("bed-assignments[12345-1].living-unit", "bed-assignments[12345-1].start-date", "bed-assignments[12345-1].end-date")
        },
        isNull(),
      )
    }

    @Test
    fun `reports a missing DPS end date`() = runTest {
      val endDate = nomis.assignmentDateTime.plusDays(1)
      stubAssignments(nomisAssignments = listOf(nomis.copy(assignmentEndDateTime = endDate)))

      assertThat(service.checkBedAssignments(offenderNo)?.differences).containsExactly(difference("end-date", null, endDate))
    }

    @Test
    fun `reports a different non-null end date`() = runTest {
      val endDate = nomis.assignmentDateTime.plusDays(1)
      stubAssignments(listOf(nomis.copy(assignmentEndDateTime = endDate)), listOf(dps.copy(endAt = endDate.plusSeconds(1))))

      assertThat(service.checkBedAssignments(offenderNo)?.differences).containsExactly(difference("end-date", endDate.plusSeconds(1), endDate))
    }

    @Test
    fun `reports a missing DPS assignment`() = runTest {
      stubAssignments(dpsAssignments = emptyList())

      assertThat(service.checkBedAssignments(offenderNo)?.differences).containsExactly(
        BedAssignmentDifference("bed-assignments[12345-1]", 0, 1, nomisId = "12345-1"),
      )
    }

    @Test
    fun `reports an extra DPS assignment`() = runTest {
      stubAssignments(nomisAssignments = emptyList())

      assertThat(service.checkBedAssignments(offenderNo)?.differences).containsExactly(
        BedAssignmentDifference("bed-assignments[${dps.id}]", 1, 0, dpsId = dps.id.toString()),
      )
    }

    @Test
    fun `missing mapping reports both unmatched records`() = runTest {
      stubAssignments()
      mappingApi.stubGetByNomisId(HttpStatus.NOT_FOUND)

      assertThat(service.checkBedAssignments(offenderNo)?.differences).containsExactly(
        BedAssignmentDifference("bed-assignments[12345-1]", 0, 1, nomisId = "12345-1"),
        BedAssignmentDifference("bed-assignments[${dps.id}]", 1, 0, dpsId = dps.id.toString()),
      )
    }

    @Test
    fun `errors retrieving assignments are thrown`() = runTest {
      stubAssignments()
      bedAssignmentApi.stubFor(get(urlPathEqualTo("/nomis-sync/bed-assignments/$offenderNo")).willReturn(serverError()))

      assertThrows<WebClientResponseException.InternalServerError> { service.checkBedAssignments(offenderNo) }
    }
  }

  @Nested
  inner class BatchReport {
    @BeforeEach
    fun setUp() {
      nomisApi.stubGetAllPrisoners(prisoners = listOf(offenderNo), offenderId = 0, pageSize = 1000)
      nomisApi.stubGetAllPrisoners(prisoners = emptyList(), offenderId = 1, pageSize = 1000)
      nomisApi.stubGetAllLatestBookings(
        bookingId = 0,
        response = BookingIdsWithLast(lastBookingId = 1, prisonerIds = listOf(PrisonerIds(bookingId = 1, offenderNo = offenderNo))),
      )
      nomisApi.stubGetAllLatestBookings(bookingId = 1)
      stubAssignments()
    }

    @ParameterizedTest
    @EnumSource(PrisonerSelection::class)
    fun `reports totals for the selection`(selection: PrisonerSelection) = runTest {
      service.generateReconciliationReport(selection)

      verify(telemetryClient).trackEvent(
        eq("bed-assignment-reports-reconciliation-requested"),
        eq(mapOf("TYPE" to selection.name)),
        isNull(),
      )
      verify(telemetryClient).trackEvent(
        eq("bed-assignment-reports-reconciliation-report"),
        check {
          assertThat(it).containsEntry("TYPE", selection.name).containsEntry("prisoners-count", "1")
            .containsEntry("mismatch-count", "0").containsEntry("success", "true")
        },
        isNull(),
      )
    }

    @Test
    fun `counts mismatched prisoners`() = runTest {
      stubAssignments(dpsAssignments = emptyList())

      service.generateReconciliationReport(ALL_PRISONERS)

      verify(telemetryClient).trackEvent(
        eq("bed-assignment-reports-reconciliation-report"),
        check { assertThat(it).containsEntry("mismatch-count", "1").containsEntry("offenderNos", offenderNo) },
        isNull(),
      )
    }

    @Test
    fun `reports errors checking a prisoner`() = runTest {
      bedAssignmentApi.stubFor(get(urlPathEqualTo("/nomis-sync/bed-assignments/$offenderNo")).willReturn(serverError()))

      service.generateReconciliationReport(ALL_PRISONERS)

      verify(telemetryClient).trackEvent(
        eq("bed-assignment-reports-reconciliation-mismatch-error"),
        check { assertThat(it).containsEntry("offenderNo", offenderNo).containsKey("reason") },
        isNull(),
      )
    }
  }
}

fun dpsBedAssignment() = BedAssignment(
  id = UUID.fromString("de91dfa7-821f-4552-a427-bf2f32eafeb0"),
  prisonerNumber = "A0001AA",
  locationKey = "MDI-A-1-001",
  startAt = nomisBedAssignment().assignmentDateTime,
  source = BedAssignment.Source.NOMIS,
  recordedBy = "ME",
)
