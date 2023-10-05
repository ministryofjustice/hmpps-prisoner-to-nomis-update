@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.NonAssociationIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.NonAssociationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations.model.NonAssociation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDate

private const val OFFENDER1 = "A0001AA"
private const val OFFENDER2 = "B0001BB"
private val EXP = LocalDate.parse("2022-01-01")

class NonAssociationsReconciliationServiceTest {

  private val nonAssociationsApiService: NonAssociationsApiService = mock()
  private val nomisApiService: NomisApiService = mock()
  private val telemetryClient: TelemetryClient = mock()

  private val nonAssociationsReconciliationService =
    NonAssociationsReconciliationService(telemetryClient, nomisApiService, nonAssociationsApiService, 10L)

  @Test
  fun `will report mismatch where more dps than nomis`() = runTest {
    whenever(nomisApiService.getNonAssociationDetails(OFFENDER1, OFFENDER2)).thenReturn(
      listOf(nomisResponse(1, null)),
    )

    whenever(nonAssociationsApiService.getNonAssociationsBetween(OFFENDER1, OFFENDER2)).thenReturn(
      listOf(dpsResponse(1L), dpsResponse(2L)),
    )

    val mismatch = nonAssociationsReconciliationService.checkMatch(NonAssociationIdResponse(OFFENDER1, OFFENDER2))

    assertThat(mismatch).asList().containsExactly(
      MismatchNonAssociation(
        NonAssociationIdResponse(OFFENDER1, OFFENDER2),
        null,
        NonAssociationReportDetail(
          type = "LANDING",
          createdDate = "2022-01-01T10:00:00",
          closed = true,
          roleReason = "VICTIM",
          roleReason2 = "PERPETRATOR",
          dpsReason = "BULLYING",
          comment = "comment",
        ),
      ),
    )
  }

  @Test
  fun `will report mismatch where more nomis than dps`() = runTest {
    whenever(nomisApiService.getNonAssociationDetails(OFFENDER1, OFFENDER2)).thenReturn(
      listOf(nomisResponse(1, EXP), nomisResponse(2, null)),
    )

    whenever(nonAssociationsApiService.getNonAssociationsBetween(OFFENDER1, OFFENDER2)).thenReturn(
      listOf(dpsResponse(1L)),
    )

    val mismatch = nonAssociationsReconciliationService.checkMatch(NonAssociationIdResponse(OFFENDER1, OFFENDER2))

    assertThat(mismatch).asList().containsExactly(
      MismatchNonAssociation(
        NonAssociationIdResponse(OFFENDER1, OFFENDER2),
        NonAssociationReportDetail(
          type = "LAND",
          createdDate = "2022-01-01",
          roleReason = "VIC",
          roleReason2 = "PER",
        ),
        null,
      ),
    )
  }

  @Test
  fun `will not report mismatch where NA details match`() = runTest {
    whenever(nomisApiService.getNonAssociationDetails(OFFENDER1, OFFENDER2)).thenReturn(
      listOf(nomisResponse(1, EXP, "comment1")),
    )

    whenever(nonAssociationsApiService.getNonAssociationsBetween(OFFENDER1, OFFENDER2)).thenReturn(
      listOf(dpsResponse(1L, "comment1")),
    )

    assertThat(nonAssociationsReconciliationService.checkMatch(NonAssociationIdResponse(OFFENDER1, OFFENDER2)))
      .asList()
      .isEmpty()
  }

  @Test
  fun `will not report mismatch where 2 NA details are swapped`() = runTest {
    whenever(nomisApiService.getNonAssociationDetails(OFFENDER1, OFFENDER2)).thenReturn(
      listOf(
        nomisResponse(1, EXP, "comment1"),
        nomisResponse(2, EXP, "comment2"),
      ),
    )

    whenever(nonAssociationsApiService.getNonAssociationsBetween(OFFENDER1, OFFENDER2)).thenReturn(
      listOf(
        dpsResponse(1L, "comment2"),
        dpsResponse(2L, "comment1"),
      ),
    )

    assertThat(nonAssociationsReconciliationService.checkMatch(NonAssociationIdResponse(OFFENDER1, OFFENDER2)))
      .asList()
      .isEmpty()
  }

  @Test
  fun `will report mismatch where 2 NA details have a difference`() = runTest {
    whenever(nomisApiService.getNonAssociationDetails(OFFENDER1, OFFENDER2)).thenReturn(
      listOf(
        nomisResponse(1, EXP, "comment1"),
        nomisResponse(2, EXP, "comment2"),
      ),
    )

    whenever(nonAssociationsApiService.getNonAssociationsBetween(OFFENDER1, OFFENDER2)).thenReturn(
      listOf(
        dpsResponse(1L, "comment1"),
        dpsResponse(2L, "comment3"),
      ),
    )

    assertThat(nonAssociationsReconciliationService.checkMatch(NonAssociationIdResponse(OFFENDER1, OFFENDER2)))
      .asList()
      .containsExactly(
        MismatchNonAssociation(
          NonAssociationIdResponse(OFFENDER1, OFFENDER2),
          NonAssociationReportDetail(
            type = "LAND",
            createdDate = "2022-01-01",
            expiryDate = "2022-01-01",
            roleReason = "VIC",
            roleReason2 = "PER",
            comment = "comment2",
          ),
          NonAssociationReportDetail(
            type = "LANDING",
            createdDate = "2022-01-01T10:00:00",
            closed = true,
            roleReason = "VICTIM",
            roleReason2 = "PERPETRATOR",
            dpsReason = "BULLYING",
            comment = "comment3",
          ),
        ),
      )
  }

  @Test
  fun `correctly identifies whether nomis is closed`() = runTest {
    val today = LocalDate.now()
    assertThat(nonAssociationsReconciliationService.closedInNomis(nomisResponse(1, null), today)).isFalse()
    assertThat(nonAssociationsReconciliationService.closedInNomis(nomisResponse(1, today), today)).isTrue()
    assertThat(nonAssociationsReconciliationService.closedInNomis(nomisResponse(1, today.minusDays(1)), today)).isTrue()
    assertThat(nonAssociationsReconciliationService.closedInNomis(nomisResponse(1, today.plusDays(1)), today)).isFalse()
  }

  private fun nomisResponse(typeSequence: Int, expiryDate: LocalDate?, comment: String? = null) = NonAssociationResponse(
    OFFENDER1,
    OFFENDER2,
    typeSequence,
    "VIC",
    "PER",
    "LAND",
    effectiveDate = LocalDate.parse("2022-01-01"),
    expiryDate = expiryDate,
    comment = comment,
    updatedBy = "M_HALMA",
  )

  private fun dpsResponse(id: Long, comment: String = "comment") = NonAssociation(
    id,
    OFFENDER1,
    NonAssociation.FirstPrisonerRole.VICTIM,
    "roledesc",
    OFFENDER2,
    NonAssociation.SecondPrisonerRole.PERPETRATOR,
    "roledesc",
    NonAssociation.Reason.BULLYING,
    "reasondesc",
    NonAssociation.RestrictionType.LANDING,
    "typedesc",
    comment = comment,
    whenCreated = "2022-01-01T10:00:00",
    whenUpdated = "2022-01-01T10:00:00",
    updatedBy = "me",
    true,
    false,
  )
}
