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
        NonAssociationReportDetail(type = "LANDING", createdDate = "2022-01-01T10:00:00"),
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
        NonAssociationReportDetail(type = "LAND", createdDate = "2022-01-01"),
        null,
      ),
    )
  }

  private fun nomisResponse(typeSequence: Int, expiryDate: LocalDate?) = NonAssociationResponse(
    OFFENDER1,
    OFFENDER2,
    typeSequence,
    "reason",
    "recipReason",
    "LAND",
    effectiveDate = LocalDate.parse("2022-01-01"),
    expiryDate = expiryDate,
  )

  private fun dpsResponse(id: Long) = NonAssociation(
    id,
    OFFENDER1,
    NonAssociation.FirstPrisonerRole.NOT_RELEVANT,
    "roledesc",
    OFFENDER2,
    NonAssociation.SecondPrisonerRole.NOT_RELEVANT,
    "roledesc",
    NonAssociation.Reason.BULLYING,
    "reasondesc",
    NonAssociation.RestrictionType.LANDING,
    "typedesc",
    comment = "comment",
    whenCreated = "2022-01-01T10:00:00",
    whenUpdated = "2022-01-01T10:00:00",
    updatedBy = "me",
    true,
    false,
  )
}
