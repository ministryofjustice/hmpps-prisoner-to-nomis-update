package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.reactive.function.client.WebClientResponseException
import software.amazon.awssdk.http.HttpStatusCode
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.model.CsraDetailedRating
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AssessmentLevel
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AssessmentStatusType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AssessmentType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CsraGetDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

const val CSRA_OFFENDER_NO = "A5678BZ"
const val CSRA_DPS_ID = "57718979-573c-433a-abcd-000011112222"
const val CSRA_BOOKING_ID = 12345L
const val CSRA_SEQUENCE = 1

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
    // WireMock.reset()
  }

  @Nested
  inner class CheckMatch {
    private fun stubCsraReconciliation(nomisCsra: CsraGetDto?, dpsCsra: CsraDetailedRating?) {
      nomisCsra?.let { csraNomisApi.stubGetCurrentCsraForPrisoner(CSRA_OFFENDER_NO, it) }
        ?: csraNomisApi.stubGetCurrentCsraForPrisonerError(CSRA_OFFENDER_NO, HttpStatusCode.NOT_FOUND)
      dpsCsra?.let { dpsApi.stubGetCurrentCsra(CSRA_OFFENDER_NO, it) }
        ?: dpsApi.stubGetCurrentCsraError(CSRA_OFFENDER_NO, HttpStatusCode.NOT_FOUND)
    }

    @Test
    fun `will not report a mismatch when no differences found`() = runTest {
      stubCsraReconciliation(
        nomisCsra(),
        dpsCsra(CSRA_DPS_ID),
      )
      assertThat(service.checkCsra(CSRA_OFFENDER_NO)).isNull()
    }

    @Test
    fun `will not report a mismatch when there is no rating`() = runTest {
      stubCsraReconciliation(
        null,
        dpsCsra(CSRA_DPS_ID, status = CsraDetailedRating.Status.NO_RATING),
      )
      assertThat(service.checkCsra(CSRA_OFFENDER_NO)).isNull()
    }

    @Test
    fun `will report an extra DPS csra`() = runTest {
      stubCsraReconciliation(
        null,
        dpsCsra(CSRA_DPS_ID),
      )
      assertThat(service.checkCsra(CSRA_OFFENDER_NO)?.differences).isEqualTo(
        listOf(
          Difference(property = "current-csra", dps = 1, nomis = 0, dpsId = CSRA_DPS_ID, nomisId = null),
        ),
      )
    }

    @Test
    fun `will report an extra Nomis csra`() = runTest {
      stubCsraReconciliation(
        nomisCsra(),
        null,
      )
      assertThat(service.checkCsra(CSRA_OFFENDER_NO)?.differences).isEqualTo(
        listOf(
          Difference(property = "current-csra", dps = 0, nomis = 1, dpsId = null, nomisId = "1-1"),
        ),
      )
    }

    @Test
    fun `will report a level mismatch`() = runTest {
      stubCsraReconciliation(
        nomisCsra(),
        dpsCsra(CSRA_DPS_ID, rating = CsraDetailedRating.Rating.HIGH),
      )
      assertThat(service.checkCsra(CSRA_OFFENDER_NO)?.differences).isEqualTo(
        listOf(
          Difference(property = "level", dps = "HIGH", nomis = "STANDARD", dpsId = CSRA_DPS_ID, nomisId = "1-1"),
        ),
      )
    }

    @Test
    fun `a HI level trumps a STD level`() = runTest {
      stubCsraReconciliation(
        nomisCsra(calculatedLevel = AssessmentLevel.HI, overrideLevel = AssessmentLevel.STANDARD),
        dpsCsra(CSRA_DPS_ID, rating = CsraDetailedRating.Rating.STANDARD),
      )
      assertThat(service.checkCsra(CSRA_OFFENDER_NO)?.differences).isEqualTo(
        listOf(
          Difference(property = "level", dps = "STANDARD", nomis = "HIGH", dpsId = CSRA_DPS_ID, nomisId = "1-1"),
        ),
      )
    }

    @Test
    fun `High General and High Specific match NOMIS HI when NOMIS does not hold the new codes`() = runTest {
      listOf(CsraDetailedRating.Rating.HIGH_GENERAL, CsraDetailedRating.Rating.HIGH_SPECIFIC).forEach { rating ->
        stubCsraReconciliation(
          nomisCsra(calculatedLevel = AssessmentLevel.HI, reviewLevel = AssessmentLevel.HI),
          dpsCsra(CSRA_DPS_ID, rating = rating),
        )
        assertThat(service.checkCsra(CSRA_OFFENDER_NO)).isNull()
      }
    }

    @Test
    fun `a Nomis PEND level is ignored`() = runTest {
      stubCsraReconciliation(
        nomisCsra(calculatedLevel = AssessmentLevel.PEND, overrideLevel = null, reviewLevel = null),
        dpsCsra(CSRA_DPS_ID, status = CsraDetailedRating.Status.NO_RATING),
      )
      assertThat(service.checkCsra(CSRA_OFFENDER_NO)?.differences).isNull()
    }

    @Test
    @Disabled
    fun `will report a date mismatch`() = runTest {
      stubCsraReconciliation(
        nomisCsra(evaluationDate = LocalDate.parse("2024-06-01")),
        dpsCsra(CSRA_DPS_ID),
      )
      assertThat(service.checkCsra(CSRA_OFFENDER_NO)?.differences).isEqualTo(
        listOf(
          Difference(property = "date", nomis = LocalDate.parse("2024-06-01"), dps = LocalDate.parse("2024-01-01"), dpsId = CSRA_DPS_ID, nomisId = "1-1"),
        ),
      )
    }

    @Test
    @Disabled
    fun `will not report a missing evaluation date, defaulting to assessment date`() = runTest {
      stubCsraReconciliation(
        nomisCsra(evaluationDate = null, assessmentDate = LocalDate.parse("2024-01-01")),
        dpsCsra(CSRA_DPS_ID),
      )
      assertThat(service.checkCsra(CSRA_OFFENDER_NO)?.differences).isNull()
    }
  }

  @Nested
  @TestPropertySource(properties = ["feature.csra.nomis-high-general-specific=true"])
  inner class CheckMatchWhenNomisHoldsHighGeneralAndSpecific {
    private fun stubCsraReconciliation(nomisCsra: CsraGetDto, dpsCsra: CsraDetailedRating) {
      csraNomisApi.stubGetCurrentCsraForPrisoner(CSRA_OFFENDER_NO, nomisCsra)
      dpsApi.stubGetCurrentCsra(CSRA_OFFENDER_NO, dpsCsra)
    }

    @Test
    fun `will match High General with HI_GEN and High Specific with HI_SPFC`() = runTest {
      stubCsraReconciliation(
        nomisCsra(calculatedLevel = AssessmentLevel.HI_GEN, reviewLevel = AssessmentLevel.HI_GEN),
        dpsCsra(CSRA_DPS_ID, rating = CsraDetailedRating.Rating.HIGH_GENERAL),
      )
      assertThat(service.checkCsra(CSRA_OFFENDER_NO)).isNull()

      stubCsraReconciliation(
        nomisCsra(calculatedLevel = AssessmentLevel.HI_SPFC, reviewLevel = AssessmentLevel.HI_SPFC),
        dpsCsra(CSRA_DPS_ID, rating = CsraDetailedRating.Rating.HIGH_SPECIFIC),
      )
      assertThat(service.checkCsra(CSRA_OFFENDER_NO)).isNull()
    }

    @Test
    fun `will still match legacy High with HI`() = runTest {
      stubCsraReconciliation(
        nomisCsra(calculatedLevel = AssessmentLevel.HI, reviewLevel = AssessmentLevel.HI),
        dpsCsra(CSRA_DPS_ID, rating = CsraDetailedRating.Rating.HIGH),
      )
      assertThat(service.checkCsra(CSRA_OFFENDER_NO)).isNull()
    }

    @Test
    fun `will report High Specific in DPS against HI in NOMIS`() = runTest {
      stubCsraReconciliation(
        nomisCsra(calculatedLevel = AssessmentLevel.HI, reviewLevel = AssessmentLevel.HI),
        dpsCsra(CSRA_DPS_ID, rating = CsraDetailedRating.Rating.HIGH_SPECIFIC),
      )
      assertThat(service.checkCsra(CSRA_OFFENDER_NO)?.differences).isEqualTo(
        listOf(
          Difference(property = "level", dps = "HIGH_SPECIFIC", nomis = "HIGH", dpsId = CSRA_DPS_ID, nomisId = "1-1"),
        ),
      )
    }

    @Test
    fun `will report High General in DPS against HI_SPFC in NOMIS`() = runTest {
      stubCsraReconciliation(
        nomisCsra(calculatedLevel = AssessmentLevel.HI_SPFC, reviewLevel = AssessmentLevel.HI_SPFC),
        dpsCsra(CSRA_DPS_ID, rating = CsraDetailedRating.Rating.HIGH_GENERAL),
      )
      assertThat(service.checkCsra(CSRA_OFFENDER_NO)?.differences).isEqualTo(
        listOf(
          Difference(property = "level", dps = "HIGH_GENERAL", nomis = "HIGH_SPECIFIC", dpsId = CSRA_DPS_ID, nomisId = "1-1"),
        ),
      )
    }

    @Test
    fun `an override of HI_GEN trumps a calculated HI`() = runTest {
      stubCsraReconciliation(
        nomisCsra(calculatedLevel = AssessmentLevel.HI, overrideLevel = AssessmentLevel.HI_GEN),
        dpsCsra(CSRA_DPS_ID, rating = CsraDetailedRating.Rating.HIGH_GENERAL),
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
  assessmentDate: LocalDate = LocalDate.parse("2020-01-01"),
  evaluationDate: LocalDate? = LocalDate.parse("2024-01-01"),
  calculatedLevel: AssessmentLevel = AssessmentLevel.STANDARD,
  overrideLevel: AssessmentLevel? = null,
  reviewLevel: AssessmentLevel? = null,
) = CsraGetDto(
  bookingId = 1,
  sequence = 1,
  assessmentDate = assessmentDate,
  evaluationDate = evaluationDate,
  type = AssessmentType.CSRREV,
  status = AssessmentStatusType.A,
  assessmentStaffId = 1,
  createdDateTime = LocalDateTime.now(),
  createdBy = "T.SMITH",
  sections = emptyList(),
  calculatedLevel = calculatedLevel,
  overrideLevel = overrideLevel,
  reviewLevel = reviewLevel,
)

fun dpsCsra(
  uuid: String,
  rating: CsraDetailedRating.Rating = CsraDetailedRating.Rating.STANDARD,
  status: CsraDetailedRating.Status = CsraDetailedRating.Status.COMPLETE,
) = CsraDetailedRating(
  reviewId = UUID.fromString(uuid),
  type = CsraDetailedRating.Type.CSRA_REVIEW,
  rating = rating,
  ratingDescription = rating.name,
  ratingShortDescription = rating.name,
  prisonerNumber = CSRA_OFFENDER_NO,
  status = status,
  provisional = false,
  inheritedAfterTransfer = false,
  riskTo = emptyList(),
  vulnerabilities = emptyList(),
  finalDate = LocalDate.parse("2024-01-01"),
//  reviewId = TODO(),
//  prisonId = TODO(),
//  assessmentComment = TODO(),
//  provisionalAssessmentComment = TODO(),
//  provisionalDate = TODO(),
//  finalDate = TODO(),
//  nextReviewDate = TODO(),
//  startedBy = TODO(),
//  startedAt = TODO(),
//  inProgress = TODO(),
)
