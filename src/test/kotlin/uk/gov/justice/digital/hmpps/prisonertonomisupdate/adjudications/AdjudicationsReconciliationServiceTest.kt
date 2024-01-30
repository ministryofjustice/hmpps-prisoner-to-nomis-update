@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.IncidentDetailsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.IncidentRoleDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.IncidentStatementDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OffenceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OffenceRuleDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.PunishmentDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.PunishmentDto.Type.ADDITIONAL_DAYS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.PunishmentDto.Type.PROSPECTIVE_DAYS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.PunishmentScheduleDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedAdjudicationDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.ADASummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.AdjudicationADAAwardSummaryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.ActivePrisonerId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.AdjudicationsApiExtension.Companion.adjudicationsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate

@SpringAPIServiceTest
@Import(
  AdjudicationsReconciliationService::class,
  NomisApiService::class,
  AdjudicationsApiService::class,
  AdjudicationsConfiguration::class,
)
internal class AdjudicationsReconciliationServiceTest {
  @MockBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  private lateinit var service: AdjudicationsReconciliationService

  @Nested
  inner class CheckBookingAdaPunishmentsMatch {

    @Nested
    inner class WhenBothSystemsHaveNoAdas {
      @BeforeEach
      fun beforeEach() {
        adjudicationsApiServer.stubGetAdjudicationsByBookingId(123456, emptyList())
        nomisApi.stubGetAdaAwardSummary(
          bookingId = 123456,
          adjudicationADAAwardSummaryResponse = AdjudicationADAAwardSummaryResponse(
            bookingId = 123456,
            offenderNo = "A1234AA",
            prisonIds = listOf("MDI"),
            adaSummaries = emptyList(),
          ),
        )
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkADAPunishmentsMatch(
            ActivePrisonerId(
              bookingId = 123456L,
              offenderNo = "A1234AA",
            ),
          ),
        ).isNull()
      }
    }

    @Nested
    inner class WhenBothSystemsHaveASingleIdenticalAda {
      @BeforeEach
      fun beforeEach() {
        adjudicationsApiServer.stubGetAdjudicationsByBookingId(123456, listOf(aDPSAdjudication().copy(punishments = listOf(adaPunishment(days = 10)))))
        nomisApi.stubGetAdaAwardSummary(
          bookingId = 123456,
          adjudicationADAAwardSummaryResponse = AdjudicationADAAwardSummaryResponse(
            bookingId = 123456,
            offenderNo = "A1234AA",
            prisonIds = listOf("MDI"),
            adaSummaries = listOf(nomisSummary(days = 10)),
          ),
        )
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkADAPunishmentsMatch(
            ActivePrisonerId(
              bookingId = 123456L,
              offenderNo = "A1234AA",
            ),
          ),
        ).isNull()
      }
    }

    @Nested
    inner class WhenBothSystemsHaveAMultipleIdenticalAdas {
      @BeforeEach
      fun beforeEach() {
        adjudicationsApiServer.stubGetAdjudicationsByBookingId(
          123456,
          listOf(
            aDPSAdjudication().copy(punishments = listOf(adaPunishment(days = 3))),
            aDPSAdjudication().copy(punishments = listOf(adaPunishment(days = 9))),
            aDPSAdjudication().copy(punishments = listOf(adaPunishment(days = 10))),
          ),
        )
        nomisApi.stubGetAdaAwardSummary(
          bookingId = 123456,
          adjudicationADAAwardSummaryResponse = AdjudicationADAAwardSummaryResponse(
            bookingId = 123456,
            offenderNo = "A1234AA",
            prisonIds = listOf("MDI"),
            adaSummaries = listOf(nomisSummary(days = 10), nomisSummary(days = 3), nomisSummary(days = 9)),
          ),
        )
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkADAPunishmentsMatch(
            ActivePrisonerId(
              bookingId = 123456L,
              offenderNo = "A1234AA",
            ),
          ),
        ).isNull()
      }
    }

    @Nested
    inner class WhenBothSystemsHaveAMultipleAdasWithSameLengthsButDifferentDates {
      @BeforeEach
      fun beforeEach() {
        adjudicationsApiServer.stubGetAdjudicationsByBookingId(
          123456,
          listOf(
            aDPSAdjudication().copy(punishments = listOf(adaPunishment(days = 3, startDate = LocalDate.now().minusDays(1)))),
            aDPSAdjudication().copy(punishments = listOf(adaPunishment(days = 9, startDate = LocalDate.now().minusDays(2)))),
            aDPSAdjudication().copy(punishments = listOf(adaPunishment(days = 10, startDate = LocalDate.now().minusDays(3)))),
          ),
        )
        nomisApi.stubGetAdaAwardSummary(
          bookingId = 123456,
          adjudicationADAAwardSummaryResponse = AdjudicationADAAwardSummaryResponse(
            bookingId = 123456,
            offenderNo = "A1234AA",
            prisonIds = listOf("MDI"),
            adaSummaries = listOf(
              nomisSummary(days = 10, effectiveDate = LocalDate.now().minusWeeks(1)),
              nomisSummary(days = 3, effectiveDate = LocalDate.now().minusWeeks(3)),
              nomisSummary(days = 9, effectiveDate = LocalDate.now().minusWeeks(7)),
            ),
          ),
        )
      }

      @Test
      fun `will not report a mismatch ignoring the ADA dates`() = runTest {
        assertThat(
          service.checkADAPunishmentsMatch(
            ActivePrisonerId(
              bookingId = 123456L,
              offenderNo = "A1234AA",
            ),
          ),
        ).isNull()
      }
    }

    @Nested
    inner class WhenBothSystemsHaveASingleIdenticalAdaButIsProspectiveInDPS {
      @BeforeEach
      fun beforeEach() {
        adjudicationsApiServer.stubGetAdjudicationsByBookingId(123456, listOf(aDPSAdjudication().copy(punishments = listOf(adaPunishment(days = 10, type = PROSPECTIVE_DAYS)))))
        nomisApi.stubGetAdaAwardSummary(
          bookingId = 123456,
          adjudicationADAAwardSummaryResponse = AdjudicationADAAwardSummaryResponse(
            bookingId = 123456,
            offenderNo = "A1234AA",
            prisonIds = listOf("MDI"),
            adaSummaries = listOf(nomisSummary(days = 10)),
          ),
        )
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkADAPunishmentsMatch(
            ActivePrisonerId(
              bookingId = 123456L,
              offenderNo = "A1234AA",
            ),
          ),
        ).isNull()
      }
    }

    @Nested
    inner class WhenBothSystemsHaveASingleIdenticalAdaButWithDifferentStatus {
      @BeforeEach
      fun beforeEach() {
        adjudicationsApiServer.stubGetAdjudicationsByBookingId(
          123456,
          listOf(aDPSAdjudication().copy(status = ReportedAdjudicationDto.Status.INVALID_ADA, punishments = listOf(adaPunishment(days = 10, type = PROSPECTIVE_DAYS)))),
        )
        nomisApi.stubGetAdaAwardSummary(
          bookingId = 123456,
          adjudicationADAAwardSummaryResponse = AdjudicationADAAwardSummaryResponse(
            bookingId = 123456,
            offenderNo = "A1234AA",
            prisonIds = listOf("MDI"),
            adaSummaries = listOf(nomisSummary(days = 10).copy(sanctionStatus = CodeDescription("QUASHED", "Quashed"))),
          ),
        )
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkADAPunishmentsMatch(
            ActivePrisonerId(
              bookingId = 123456L,
              offenderNo = "A1234AA",
            ),
          ),
        ).isNull()
      }
    }

    @Nested
    inner class WhenBothSystemsHaveASingleButTheAdaHasDifferentLengths {
      @BeforeEach
      fun beforeEach() {
        adjudicationsApiServer.stubGetAdjudicationsByBookingId(123456, listOf(aDPSAdjudication().copy(punishments = listOf(adaPunishment(days = 10)))))
        nomisApi.stubGetAdaAwardSummary(
          bookingId = 123456,
          adjudicationADAAwardSummaryResponse = AdjudicationADAAwardSummaryResponse(
            bookingId = 123456,
            offenderNo = "A1234AA",
            prisonIds = listOf("MDI"),
            adaSummaries = listOf(nomisSummary(days = 12)),
          ),
        )
      }

      @Test
      fun `will report a mismatch`() = runTest {
        val mismatch = service.checkADAPunishmentsMatch(
          ActivePrisonerId(
            bookingId = 123456L,
            offenderNo = "A1234AA",
          ),
        )
        assertThat(mismatch).isNotNull
        assertThat(mismatch?.dpsAdas?.count).isEqualTo(1)
        assertThat(mismatch?.nomisAda?.count).isEqualTo(1)
        assertThat(mismatch?.dpsAdas?.days).isEqualTo(10)
        assertThat(mismatch?.nomisAda?.days).isEqualTo(12)
      }
    }
  }

  @Nested
  inner class GenerateReconciliationReport {
    @BeforeEach
    fun setUp() {
      nomisApi.stubGetActivePrisonersInitialCount(2)
      nomisApi.stubGetActivePrisonersPage(2, 0, 2)
      adjudicationsApiServer.stubGetAdjudicationsByBookingId(1, emptyList())
      adjudicationsApiServer.stubGetAdjudicationsByBookingId(2, listOf(aDPSAdjudication().copy(punishments = listOf(adaPunishment(days = 10)))))
      nomisApi.stubGetAdaAwardSummary(
        bookingId = 1,
        adjudicationADAAwardSummaryResponse = AdjudicationADAAwardSummaryResponse(bookingId = 1, offenderNo = "A1234AA", prisonIds = listOf("MDI"), adaSummaries = emptyList()),
      )
      nomisApi.stubGetAdaAwardSummary(
        bookingId = 2,
        adjudicationADAAwardSummaryResponse = AdjudicationADAAwardSummaryResponse(
          bookingId = 2,
          offenderNo = "A1234AA",
          prisonIds = listOf("MDI"),
          adaSummaries = listOf(nomisSummary(days = 12)),
        ),
      )
    }

    @Test
    fun `will call DPS for each bookingId`() = runTest {
      service.generateReconciliationReport(2)
      adjudicationsApiServer.verify(getRequestedFor(urlPathEqualTo("/reported-adjudications/booking/1")))
      adjudicationsApiServer.verify(getRequestedFor(urlPathEqualTo("/reported-adjudications/booking/2")))
    }

    @Test
    fun `will call NOMIS for each bookingId`() = runTest {
      service.generateReconciliationReport(2)
      nomisApi.verify(getRequestedFor(urlEqualTo("/prisoners/booking-id/1/awards/ada/summary")))
      nomisApi.verify(getRequestedFor(urlEqualTo("/prisoners/booking-id/2/awards/ada/summary")))
    }

    @Test
    fun `will return list of only the mismatches`() = runTest {
      val mismatches = service.generateReconciliationReport(2)

      assertThat(mismatches).hasSize(1)
      assertThat(mismatches[0].prisonerId.bookingId).isEqualTo(2L)
    }
  }
}

internal fun aDPSAdjudication(chargeNumber: String = "4000001", prisonerNumber: String = "A1234AA"): ReportedAdjudicationDto = ReportedAdjudicationDto(
  chargeNumber = chargeNumber,
  prisonerNumber = prisonerNumber,
  gender = ReportedAdjudicationDto.Gender.FEMALE,
  incidentDetails = IncidentDetailsDto(locationId = 1234, dateTimeOfIncident = "2023-01-01T10:00", dateTimeOfDiscovery = "2023-01-01T10:00", handoverDeadline = "2023-01-01T10:00"),
  isYouthOffender = false,
  incidentRole = IncidentRoleDto(),
  offenceDetails = OffenceDto(offenceCode = 1, offenceRule = OffenceRuleDto("51A", paragraphDescription = "")),
  incidentStatement = IncidentStatementDto("Fight", completed = true),
  createdByUserId = "JANE",
  createdDateTime = "2023-01-01T10:00",
  status = ReportedAdjudicationDto.Status.CHARGE_PROVED,
  damages = emptyList(),
  evidence = emptyList(),
  witnesses = emptyList(),
  hearings = listOf(),
  disIssueHistory = emptyList(),
  outcomes = listOf(),
  punishments = emptyList(),
  punishmentComments = emptyList(),
  outcomeEnteredInNomis = false,
  originatingAgencyId = "MDI",
  reviewedByUserId = "JANE",
  statusReason = null,
  statusDetails = null,
  issuingOfficer = null,
  dateTimeOfIssue = null,
  dateTimeOfFirstHearing = null,
  overrideAgencyId = null,
  transferableActionsAllowed = null,
  createdOnBehalfOfOfficer = null,
  createdOnBehalfOfReason = null,
  linkedChargeNumbers = emptyList(),
)

internal fun adaPunishment(days: Int, startDate: LocalDate = LocalDate.now(), type: PunishmentDto.Type = ADDITIONAL_DAYS) =
  PunishmentDto(type = type, schedule = PunishmentScheduleDto(days = days, startDate = startDate), canRemove = true)

internal fun nomisSummary(days: Int, effectiveDate: LocalDate = LocalDate.now()): ADASummary = ADASummary(
  adjudicationNumber = 4000001,
  sanctionSequence = 1,
  days = days,
  effectiveDate = effectiveDate,
  sanctionStatus = CodeDescription("IMMEDIATE", "Immediate"),
)
