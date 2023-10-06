package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.HearingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.HearingOutcomeDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.IncidentDetailsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.IncidentRoleDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.IncidentStatementDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OffenceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OffenceRuleDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OutcomeHistoryDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedAdjudicationDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedAdjudicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateHearingResultResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService

private const val dpsHearingId = "123"
private const val nomisHearingId = 456L
private const val dpsChargeNumber = "123"
private const val adjudicationNumber = 123456L
private const val prisonerNumber = "A12345DF"

@OptIn(ExperimentalCoroutinesApi::class)
internal class AdjudicationsServiceTest {

  private val adjudicationsApiService: AdjudicationsApiService = mock()
  private val nomisApiService: NomisApiService = mock()
  private val adjudicationsMappingService: AdjudicationsMappingService = mock()
  private val hearingMappingService: HearingsMappingService = mock()
  private val adjudicationsRetryQueueService: AdjudicationsRetryQueueService = mock()
  private val telemetryClient: TelemetryClient = mock()
  private val objectMapper: ObjectMapper = mock()

  private val adjudicationsService = AdjudicationsService(
    telemetryClient,
    adjudicationsRetryQueueService,
    adjudicationsMappingService,
    hearingMappingService,
    adjudicationsApiService,
    nomisApiService,
    objectMapper,
  )

  @Test
  fun `External adjudicator name is not sent to nomis for hearing completed`() = runTest {
    callService(hearingType = HearingDto.OicHearingType.INAD_ADULT)
    verify(nomisApiService).createHearingResult(
      any(),
      any(),
      any(),
      check { assertThat(it.adjudicatorUsername).isNull() },
    )
  }

  @Test
  fun `Internal adjudicator name is sent to nomis for hearing completed`() = runTest {
    callService(hearingType = HearingDto.OicHearingType.GOV_ADULT)
    verify(nomisApiService).createHearingResult(
      any(),
      any(),
      any(),
      check { assertThat(it.adjudicatorUsername).isEqualTo("an adjudicator") },
    )
  }

  private suspend fun callService(hearingType: HearingDto.OicHearingType = HearingDto.OicHearingType.GOV_ADULT) {
    whenever(adjudicationsApiService.getCharge(any(), any())).thenReturn(
      getReportedAdjudicationResponse(oicHearingType = hearingType),
    )
    whenever(adjudicationsMappingService.getMappingGivenChargeNumber(any())).thenReturn(
      AdjudicationMappingDto(
        chargeSequence = 1,
        chargeNumber = dpsChargeNumber,
        adjudicationNumber = adjudicationNumber,
      ),
    )

    whenever(hearingMappingService.getMappingGivenDpsHearingIdOrNull(any())).thenReturn(
      AdjudicationHearingMappingDto(nomisHearingId = nomisHearingId, dpsHearingId = dpsHearingId),
    )
    whenever(nomisApiService.createHearingResult(any(), any(), any(), any())).thenReturn(
      CreateHearingResultResponse(
        hearingId = nomisHearingId,
        2,
      ),
    )

    adjudicationsService.createHearingCompleted(
      HearingEvent(
        HearingAdditionalInformation(
          chargeNumber = dpsChargeNumber,
          prisonId = "MDI",
          prisonerNumber = prisonerNumber,
          hearingId = dpsHearingId,
        ),
      ),
    )
  }

  private fun getReportedAdjudicationResponse(oicHearingType: HearingDto.OicHearingType = HearingDto.OicHearingType.GOV_ADULT) =
    ReportedAdjudicationResponse(
      ReportedAdjudicationDto(
        chargeNumber = dpsChargeNumber,
        prisonerNumber = prisonerNumber,
        gender = ReportedAdjudicationDto.Gender.MALE,
        incidentDetails = IncidentDetailsDto(
          locationId = 2,
          dateTimeOfDiscovery = "2023-09-26T08:15:00",
          dateTimeOfIncident = "2023-09-26T08:15:00",
          handoverDeadline = "2023-09-26T08:15:00",
        ),
        isYouthOffender = false,
        incidentRole = IncidentRoleDto(),
        offenceDetails = OffenceDto(
          offenceCode = 7003,
          offenceRule = OffenceRuleDto(paragraphDescription = "7", paragraphNumber = "8"),
        ),
        incidentStatement = IncidentStatementDto(statement = "s"),
        createdByUserId = "me",
        createdDateTime = "2023-09-26T08:15:00",
        status = ReportedAdjudicationDto.Status.CHARGE_PROVED,
        damages = emptyList(),
        evidence = emptyList(),
        witnesses = emptyList(),
        hearings = listOf(
          HearingDto(
            id = dpsHearingId.toLong(),
            locationId = 123,
            dateTimeOfHearing = "",
            oicHearingType = oicHearingType,
            agencyId = "MDI",
            outcome = HearingOutcomeDto(
              adjudicator = "an adjudicator",
              code = HearingOutcomeDto.Code.COMPLETE,
              plea = HearingOutcomeDto.Plea.GUILTY,
            ),
          ),
        ),
        outcomes = listOf(
          OutcomeHistoryDto(
            hearing = HearingDto(
              id = dpsHearingId.toLong(),
              locationId = 123,
              dateTimeOfHearing = "",
              oicHearingType = oicHearingType,
              agencyId = "MDI",
              outcome = HearingOutcomeDto(
                adjudicator = "an adjudicator",
                code = HearingOutcomeDto.Code.COMPLETE,
                plea = HearingOutcomeDto.Plea.GUILTY,
              ),
            ),
          ),
        ),
        disIssueHistory = emptyList(),
        punishments = emptyList(),
        punishmentComments = emptyList(),
        outcomeEnteredInNomis = false,
        originatingAgencyId = "MDI",
      ),
    )
}
