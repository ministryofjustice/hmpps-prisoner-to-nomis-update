package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.model.CsraEstablishment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.model.CsraLegacyDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.model.CsraReviewHistory
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.model.CsraReviewHistorySummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.model.CsraReviewSummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AssessmentLevel
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AssessmentStatusType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AssessmentType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CsraGetDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerCsrasResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

const val CSRA_OFFENDER_NO = "A5678BZ"

@SpringAPIServiceTest
@Import(
  CsraReconciliationService::class,
  CsraNomisApiService::class,
  CsraDpsApiService::class,
  CsraNomisApiMockServer::class,
  NomisApiService::class,
  RetryApiService::class,
  CsraConfiguration::class,
)
class CsraReconciliationServiceTest {

  @MockitoBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  private lateinit var csraNomisApi: CsraNomisApiMockServer

  private val dpsApi = CsraDpsApiExtension.csraApi

  private val nomisApi = NomisApiExtension.nomisApi

  @Autowired
  private lateinit var service: CsraReconciliationService

  @BeforeEach
  fun setUp() {
    reset(telemetryClient)
  }

  @Nested
  inner class CheckMatch {
    private fun stubCsraReconciliation(nomisCsras: PrisonerCsrasResponse, dpsCsras: CsraReviewHistory) {
      csraNomisApi.stubGetCsrasForPrisoner(CSRA_OFFENDER_NO, nomisCsras)
      dpsApi.stubGetCsraHistory(CSRA_OFFENDER_NO, dpsCsras)
    }

    @Test
    fun `will not report a mismatch when no differences found`() = runTest {
      stubCsraReconciliation(
        nomisCsras(),
        dpsCsras(),
      )
      assertThat(service.checkCsra(CSRA_OFFENDER_NO)).isNull()
    }

    @Test
    fun `will report an extra DPS csra`() = runTest {
      stubCsraReconciliation(
        nomisCsras().copy(csras = listOf()),
        dpsCsras(),
      )
      assertThat(service.checkCsra(CSRA_OFFENDER_NO)?.differences).isEqualTo(
        listOf(
          Difference(property = "prisoner-csras.csras", dps = 1, nomis = 0),
        ),
      )
    }

    @Test
    fun `will report an extra Nomis csra`() = runTest {
      stubCsraReconciliation(
        nomisCsras().copy(csras = listOf(nomisCsra(), nomisCsra(assessmentDate = LocalDate.parse("2024-02-01")))),
        dpsCsras(),
      )
      assertThat(service.checkCsra(CSRA_OFFENDER_NO)?.differences).isEqualTo(
        listOf(
          Difference(property = "prisoner-csras.csras", dps = 1, nomis = 2),
        ),
      )
    }

    @Test
    fun `will not report a mismatch when csras are in a different order`() = runTest {
      stubCsraReconciliation(
        nomisCsras().copy(
          csras = listOf(
            nomisCsra(assessmentDate = LocalDate.parse("2024-02-01")),
            nomisCsra(assessmentDate = LocalDate.parse("2024-01-01")),
          ),
        ),
        dpsCsras().copy(
          content = listOf(
            dpsCsra(recordedDate = LocalDate.parse("2024-01-01")),
            dpsCsra(recordedDate = LocalDate.parse("2024-02-01")),
          ),
        ),
      )
      assertThat(service.checkCsra(CSRA_OFFENDER_NO)).isNull()
    }

    @Test
    fun `will report a level mismatch`() = runTest {
      stubCsraReconciliation(
        nomisCsras(),
        dpsCsras().copy(content = listOf(dpsCsra(rating = CsraReviewSummary.Rating.HIGH))),
      )
      assertThat(service.checkCsra(CSRA_OFFENDER_NO)?.differences).isEqualTo(
        listOf(
          Difference(property = "prisoner-csras.csras[0].level: assessmentDate 2024-01-01", dps = "HI", nomis = "STANDARD"),
        ),
      )
    }

    @Test
    fun `will report an assessment date mismatch`() = runTest {
      stubCsraReconciliation(
        nomisCsras(),
        dpsCsras().copy(content = listOf(dpsCsra(recordedDate = LocalDate.parse("2024-06-01")))),
      )
      assertThat(service.checkCsra(CSRA_OFFENDER_NO)?.differences).isEqualTo(
        listOf(
          Difference(property = "prisoner-csras.csras[0].assessmentDate", dps = LocalDate.parse("2024-06-01"), nomis = LocalDate.parse("2024-01-01")),
        ),
      )
    }

    @Test
    fun `will use the legacy assessment date and level when present`() = runTest {
      stubCsraReconciliation(
        nomisCsras(assessmentDate = LocalDate.parse("2023-05-01"), calculatedLevel = AssessmentLevel.MED),
        dpsCsras().copy(
          content = listOf(
            dpsCsra(
              recordedDate = LocalDate.parse("2024-01-01"),
              rating = CsraReviewSummary.Rating.HIGH,
              legacy = CsraLegacyDetail(
                assessmentDate = LocalDate.parse("2023-05-01"),
                level = CsraLegacyDetail.Level.MED,
              ),
            ),
          ),
        ),
      )
      assertThat(service.checkCsra(CSRA_OFFENDER_NO)).isNull()
    }
  }

  @Nested
  inner class GetOffenderNosForPage {
    @Test
    fun `will return offenderNo list`() = runTest {
      nomisApi.stubGetAllPrisonersInRange(
        fromRootOffenderId = 4,
        toRootOffenderId = 8,
        firstOffenderNo = "A0001NN",
      )
      val actual = service.getOffenderNosInRange(4, 8, activeOnly = false)

      assertThat(actual).isInstanceOf(ReconciliationSuccessPageResult::class.java)
      actual as ReconciliationSuccessPageResult
      assertThat(actual.ids).isEqualTo(listOf("A0005NN", "A0006NN", "A0007NN", "A0008NN"))
    }

    @Test
    fun `will report telemetry on error`() = runTest {
      nomisApi.stubGetAllPrisonersInRangeWithError(500)

      val actual = service.getOffenderNosInRange(4, 8, activeOnly = false)

      assertThat(actual).isInstanceOf(ReconciliationErrorPageResult::class.java)
      actual as ReconciliationErrorPageResult
      assertThat(actual.error).isInstanceOf(WebClientResponseException.InternalServerError::class.java)

      verify(telemetryClient).trackEvent(
        eq("csra-reports-reconciliation-mismatch-page-error"),
        check {
          assertThat(it).containsEntry("fromRootOffenderId", "4")
          assertThat(it).containsEntry("toRootOffenderId", "8")
          assertThat(it).containsKey("error")
          assertThat(it["error"]).startsWith("500 Internal Server Error from GET")
        },
        isNull(),
      )
    }
  }
}

fun nomisCsra(
  assessmentDate: LocalDate = LocalDate.parse("2024-01-01"),
  calculatedLevel: AssessmentLevel = AssessmentLevel.STANDARD,
) = CsraGetDto(
  bookingId = 1,
  sequence = 1,
  assessmentDate = assessmentDate,
  type = AssessmentType.CSRREV,
  status = AssessmentStatusType.A,
  assessmentStaffId = 1,
  createdDateTime = LocalDateTime.now(),
  createdBy = "T.SMITH",
  sections = emptyList(),
  calculatedLevel = calculatedLevel,
)

fun nomisCsras(
  assessmentDate: LocalDate = LocalDate.parse("2024-01-01"),
  calculatedLevel: AssessmentLevel = AssessmentLevel.STANDARD,
) = PrisonerCsrasResponse(
  csras = listOf(nomisCsra(assessmentDate = assessmentDate, calculatedLevel = calculatedLevel)),
)

fun dpsCsra(
  id: UUID = UUID.randomUUID(),
  recordedDate: LocalDate = LocalDate.parse("2024-01-01"),
  rating: CsraReviewSummary.Rating = CsraReviewSummary.Rating.STANDARD,
  legacy: CsraLegacyDetail? = null,
) = CsraReviewSummary(
  id = id,
  type = CsraReviewSummary.Type.CSRA_REVIEW,
  rating = rating,
  recordedDate = recordedDate,
  legacy = legacy,
)

fun dpsCsras(
  recordedDate: LocalDate = LocalDate.parse("2024-01-01"),
  rating: CsraReviewSummary.Rating = CsraReviewSummary.Rating.STANDARD,
) = CsraReviewHistory(
  summary = CsraReviewHistorySummary(
    totalCsras = 1,
    highCount = 0,
    standardCount = 1,
    establishments = emptyList<CsraEstablishment>(),
  ),
  content = listOf(dpsCsra(recordedDate = recordedDate, rating = rating)),
  page = 0,
  propertySize = 1000,
  totalElements = 1,
  totalPages = 1,
)
