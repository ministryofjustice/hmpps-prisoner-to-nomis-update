package uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.serverError
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment.BedAssignmentDpsApiExtension.Companion.bedAssignmentApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment.model.BedAssignment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BedAssignmentHistoryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerBedAssignmentHistoryResponse
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
    fun `reports errors retrieving assignments`() = runTest {
      stubAssignments()
      bedAssignmentApi.stubFor(get(urlPathEqualTo("/nomis-sync/bed-assignments/$offenderNo")).willReturn(serverError()))

      assertThat(service.checkBedAssignments(offenderNo)).isNull()
      verify(telemetryClient).trackEvent(
        eq("bed-assignment-reports-reconciliation-mismatch-error"),
        check { assertThat(it).containsEntry("prisoner", offenderNo).containsKey("error") },
        isNull(),
      )
    }
  }

  @Nested
  inner class BatchReport {
    @BeforeEach
    fun setUp() {
      nomisApi.stubGetAllPrisonersIdRanges(pageSize = 1, totalElements = 1)
      nomisApi.stubGetAllPrisonersInRange(fromRootOffenderId = 0, toRootOffenderId = 1, firstOffenderNo = offenderNo)
      stubAssignments()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `passes activeOnly to both range APIs and reports totals`(activeOnly: Boolean) = runTest {
      service.generateReconciliationReportBatch(activeOnly)

      nomisApi.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/id-ranges"))
          .withQueryParam("active", equalTo(activeOnly.toString()))
          .withQueryParam("size", equalTo("1000")),
      )
      nomisApi.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/ids-in-range"))
          .withQueryParam("active", equalTo(activeOnly.toString())),
      )
      verify(telemetryClient).trackEvent(
        eq("bed-assignment-reports-reconciliation-requested"),
        eq(mapOf("activeOnly" to activeOnly.toString())),
        isNull(),
      )
      verify(telemetryClient).trackEvent(
        eq("bed-assignment-reports-reconciliation-report"),
        eq(mapOf("activeOnly" to activeOnly.toString(), "prisoners-count" to "1", "page-count" to "1", "mismatch-count" to "0", "success" to "true")),
        isNull(),
      )
    }

    @Test
    fun `counts mismatched prisoners`() = runTest {
      stubAssignments(dpsAssignments = emptyList())

      service.generateReconciliationReportBatch(false)

      verify(telemetryClient).trackEvent(
        eq("bed-assignment-reports-reconciliation-report"),
        check { assertThat(it).containsEntry("mismatch-count", "1") },
        isNull(),
      )
    }

    @Test
    fun `reports failure to retrieve ranges`() = runTest {
      nomisApi.stubFor(get(urlPathEqualTo("/prisoners/id-ranges")).willReturn(serverError()))

      service.generateReconciliationReportBatch(false)

      verify(telemetryClient).trackEvent(
        eq("bed-assignment-reports-reconciliation-report"),
        check { assertThat(it).containsEntry("success", "false").containsKey("error") },
        isNull(),
      )
    }

    @Test
    fun `reports failure to retrieve a page`() = runTest {
      nomisApi.stubFor(get(urlPathEqualTo("/prisoners/ids-in-range")).willReturn(serverError()))

      assertThat(service.getOffenderNosInRange(0, 1, true)).isInstanceOf(ReconciliationErrorPageResult::class.java)
      verify(telemetryClient).trackEvent(
        eq("bed-assignment-reports-reconciliation-mismatch-page-error"),
        check { assertThat(it).containsEntry("fromRootOffenderId", "0").containsEntry("toRootOffenderId", "1").containsKey("error") },
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
