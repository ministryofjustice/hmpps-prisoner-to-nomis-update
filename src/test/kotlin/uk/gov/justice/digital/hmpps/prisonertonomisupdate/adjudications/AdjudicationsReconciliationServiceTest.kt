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
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.AdjudicationsApiExtension.Companion.adjudicationsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.CombinedOutcomeDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.HearingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.IncidentDetailsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.IncidentRoleDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.IncidentStatementDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OffenceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OffenceRuleDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OutcomeDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OutcomeHistoryDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.PunishmentDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.PunishmentDto.Type.ADDITIONAL_DAYS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.PunishmentDto.Type.PROSPECTIVE_DAYS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.PunishmentScheduleDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedAdjudicationDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ADASummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AdjudicationADAAwardSummaryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.MergeDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@SpringAPIServiceTest
@Import(
  AdjudicationsReconciliationService::class,
  NomisApiService::class,
  AdjudicationsMappingService::class,
  AdjudicationsApiService::class,
  AdjudicationsConfiguration::class,
  RetryApiService::class,
)
internal class AdjudicationsReconciliationServiceTest {
  @MockitoBean
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
            adaSummaries = emptyList(),
          ),
        )
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkADAPunishmentsMatch(
            PrisonerIds(
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
            adaSummaries = listOf(nomisSummary(days = 10)),
          ),
        )
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkADAPunishmentsMatch(
            PrisonerIds(
              bookingId = 123456L,
              offenderNo = "A1234AA",
            ),
          ),
        ).isNull()
      }
    }

    @Nested
    inner class WhenDPSHasCorruptDataAnADAWithNoOutcome {
      @BeforeEach
      fun beforeEach() {
        // also has an adjudication with an ADA but the outcome contains no hearing
        // therefore the punishment can not be valid
        adjudicationsApiServer.stubGetAdjudicationsByBookingId(
          123456,
          listOf(
            aDPSAdjudication().copy(punishments = listOf(adaPunishment(days = 10))),
            aDPSAdjudication().copy(
              outcomes = listOf(aDPSAdjudication().outcomes.lastOrNull()!!.copy(hearing = null)),
              punishments = listOf(adaPunishment(days = 10)),
            ),
          ),
        )
        nomisApi.stubGetAdaAwardSummary(
          bookingId = 123456,
          adjudicationADAAwardSummaryResponse = AdjudicationADAAwardSummaryResponse(
            bookingId = 123456,
            offenderNo = "A1234AA",
            adaSummaries = listOf(nomisSummary(days = 10)),
          ),
        )
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkADAPunishmentsMatch(
            PrisonerIds(
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
            adaSummaries = listOf(nomisSummary(days = 10), nomisSummary(days = 3), nomisSummary(days = 9)),
          ),
        )
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkADAPunishmentsMatch(
            PrisonerIds(
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
            PrisonerIds(
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
            adaSummaries = listOf(nomisSummary(days = 10)),
          ),
        )
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkADAPunishmentsMatch(
            PrisonerIds(
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
            adaSummaries = listOf(nomisSummary(days = 10).copy(sanctionStatus = CodeDescription("QUASHED", "Quashed"))),
          ),
        )
      }

      @Test
      fun `will not report a mismatch`() = runTest {
        assertThat(
          service.checkADAPunishmentsMatch(
            PrisonerIds(
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
        adjudicationsApiServer.stubGetAdjudicationsByBookingId(123456, listOf(aDPSAdjudication().copy(chargeNumber = "MDI-00010", punishments = listOf(adaPunishment(days = 10)))))
        nomisApi.stubGetAdaAwardSummary(
          bookingId = 123456,
          adjudicationADAAwardSummaryResponse = AdjudicationADAAwardSummaryResponse(
            bookingId = 123456,
            offenderNo = "A1234AA",
            adaSummaries = listOf(nomisSummary(adjudicationNumber = 10234567, days = 12)),
          ),
        )
      }

      @Nested
      inner class NoMergesSinceMigration {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetMergesFromDate(offenderNo = "A1234AA", merges = emptyList())
        }

        @Test
        fun `will retrieve merges since the NOMIS migration`() = runTest {
          service.checkADAPunishmentsMatch(
            PrisonerIds(
              bookingId = 123456L,
              offenderNo = "A1234AA",
            ),
          )
          nomisApi.verify(getRequestedFor(urlEqualTo("/prisoners/A1234AA/merges?fromDate=2024-01-28")))
        }

        @Test
        fun `will report a mismatch`() = runTest {
          val mismatch = service.checkADAPunishmentsMatch(
            PrisonerIds(
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

      @Nested
      inner class MergeSinceMigrationButNoMissingAdjudications {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetMergesFromDate(
            offenderNo = "A1234AA",
            merges = listOf(
              MergeDetail(deletedOffenderNo = "A1234AB", retainedOffenderNo = "A1234AA", previousBookingId = 1234, activeBookingId = 1235, requestDateTime = LocalDateTime.parse("2024-02-02T12:34:56")),
            ),
          )
          mappingServer.stubGetByChargeNumber("MDI-00010", 10234567)
        }

        @Test
        fun `will report a mismatch`() = runTest {
          val mismatch = service.checkADAPunishmentsMatch(
            PrisonerIds(
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
    inner class WhenDPSHasAMissingAdjudicationWithADA {
      @BeforeEach
      fun beforeEach() {
        adjudicationsApiServer.stubGetAdjudicationsByBookingId(
          123456,
          listOf(
            aDPSAdjudication().copy(chargeNumber = "MDI-00001", punishments = listOf(adaPunishment(days = 3))),
            aDPSAdjudication().copy(chargeNumber = "MDI-00002", punishments = listOf(adaPunishment(days = 9))),
          ),
        )
        nomisApi.stubGetAdaAwardSummary(
          bookingId = 123456,
          adjudicationADAAwardSummaryResponse = AdjudicationADAAwardSummaryResponse(
            bookingId = 123456,
            offenderNo = "A1234AA",
            adaSummaries = listOf(
              nomisSummary(adjudicationNumber = 1000001, days = 3),
              nomisSummary(adjudicationNumber = 1000002, days = 9),
              nomisSummary(adjudicationNumber = 1000003, days = 10),
            ),
          ),
        )
      }

      @Nested
      inner class NoMergesSinceMigration {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetMergesFromDate(offenderNo = "A1234AA", merges = emptyList())
        }

        @Test
        fun `will report a mismatch`() = runTest {
          val mismatch = service.checkADAPunishmentsMatch(
            PrisonerIds(
              bookingId = 123456L,
              offenderNo = "A1234AA",
            ),
          )
          assertThat(mismatch).isNotNull
        }
      }

      @Nested
      inner class MergeSinceMigrationWithMissingAdjudicationsAdaOnPreviousBooking {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetMergesFromDate(
            offenderNo = "A1234AA",
            merges = listOf(
              MergeDetail(deletedOffenderNo = "A1234AB", retainedOffenderNo = "A1234AA", previousBookingId = 1234, activeBookingId = 1235, requestDateTime = LocalDateTime.parse("2024-02-02T12:34:56")),
            ),
          )
          mappingServer.stubGetByChargeNumber("MDI-00001", 1000001)
          mappingServer.stubGetByChargeNumber("MDI-00002", 1000002)
          adjudicationsApiServer.stubGetAdjudicationsByBookingId(
            1234,
            listOf(
              aDPSAdjudication().copy(chargeNumber = "MDI-00003", punishments = listOf(adaPunishment(days = 10))),
            ),
          )
        }

        @Test
        fun `will not report a mismatch assume it is due to the merge`() = runTest {
          val mismatch = service.checkADAPunishmentsMatch(
            PrisonerIds(
              bookingId = 123456L,
              offenderNo = "A1234AA",
            ),
          )
          assertThat(mismatch).isNull()
        }

        @Test
        fun `will track telemetry with mismatch resolved`() = runTest {
          service.checkADAPunishmentsMatch(
            PrisonerIds(
              bookingId = 123456L,
              offenderNo = "A1234AA",
            ),
          )

          verify(telemetryClient).trackEvent(
            eq("adjudication-reports-reconciliation-merge-mismatch-resolved"),
            any(),
            isNull(),
          )
        }
      }

      @Nested
      inner class MergeSinceMigrationWithMissingAdjudicationsAndAdaNotOnPreviousBooking {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetMergesFromDate(
            offenderNo = "A1234AA",
            merges = listOf(
              MergeDetail(deletedOffenderNo = "A1234AB", retainedOffenderNo = "A1234AA", previousBookingId = 1234, activeBookingId = 1235, requestDateTime = LocalDateTime.parse("2024-02-02T12:34:56")),
            ),
          )
          mappingServer.stubGetByChargeNumber("MDI-00001", 1000001)
          mappingServer.stubGetByChargeNumber("MDI-00002", 1000002)
          adjudicationsApiServer.stubGetAdjudicationsByBookingId(
            1234,
            listOf(),
          )
        }

        @Test
        fun `will report a mismatch even though there has been a merge`() = runTest {
          val mismatch = service.checkADAPunishmentsMatch(
            PrisonerIds(
              bookingId = 123456L,
              offenderNo = "A1234AA",
            ),
          )
          assertThat(mismatch).isNotNull()
        }

        @Test
        fun `will track telemetry with mismatch not resolved`() = runTest {
          service.checkADAPunishmentsMatch(
            PrisonerIds(
              bookingId = 123456L,
              offenderNo = "A1234AA",
            ),
          )

          verify(telemetryClient).trackEvent(
            eq("adjudication-reports-reconciliation-merge-mismatch"),
            any(),
            isNull(),
          )
        }
      }
    }

    @Nested
    inner class WhenDPSHasNoMissingAdjudicationsButMissingDays {
      @BeforeEach
      fun beforeEach() {
        adjudicationsApiServer.stubGetAdjudicationsByBookingId(
          123456,
          listOf(
            aDPSAdjudication().copy(chargeNumber = "MDI-00001", punishments = listOf(adaPunishment(days = 3))),
            aDPSAdjudication().copy(chargeNumber = "MDI-00002", punishments = listOf(adaPunishment(days = 9))),
            aDPSAdjudication().copy(chargeNumber = "MDI-00003", punishments = listOf(adaPunishment(days = 1))),
          ),
        )
        nomisApi.stubGetAdaAwardSummary(
          bookingId = 123456,
          adjudicationADAAwardSummaryResponse = AdjudicationADAAwardSummaryResponse(
            bookingId = 123456,
            offenderNo = "A1234AA",
            adaSummaries = listOf(
              nomisSummary(adjudicationNumber = 1000001, days = 3),
              nomisSummary(adjudicationNumber = 1000002, days = 9),
              nomisSummary(adjudicationNumber = 1000003, days = 10),
            ),
          ),
        )
      }

      @Nested
      inner class NoMergesSinceMigration {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetMergesFromDate(offenderNo = "A1234AA", merges = emptyList())
        }

        @Test
        fun `will report a mismatch`() = runTest {
          val mismatch = service.checkADAPunishmentsMatch(
            PrisonerIds(
              bookingId = 123456L,
              offenderNo = "A1234AA",
            ),
          )
          assertThat(mismatch).isNotNull
        }
      }

      @Nested
      inner class MergeSinceMigrationWithMissingAdjudications {
        @BeforeEach
        fun setUp() {
          nomisApi.stubGetMergesFromDate(
            offenderNo = "A1234AA",
            merges = listOf(
              MergeDetail(deletedOffenderNo = "A1234AB", retainedOffenderNo = "A1234AA", previousBookingId = 1234, activeBookingId = 1235, requestDateTime = LocalDateTime.parse("2024-02-02T12:34:56")),
            ),
          )
          mappingServer.stubGetByChargeNumber("MDI-00001", 1000001)
          mappingServer.stubGetByChargeNumber("MDI-00002", 1000002)
          mappingServer.stubGetByChargeNumber("MDI-00003", 1000003)
        }

        @Test
        fun `will report a mismatch`() = runTest {
          val mismatch = service.checkADAPunishmentsMatch(
            PrisonerIds(
              bookingId = 123456L,
              offenderNo = "A1234AA",
            ),
          )
          assertThat(mismatch).isNotNull
        }
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
        adjudicationADAAwardSummaryResponse = AdjudicationADAAwardSummaryResponse(bookingId = 1, offenderNo = "A0001TZ", adaSummaries = emptyList()),
      )
      nomisApi.stubGetAdaAwardSummary(
        bookingId = 2,
        adjudicationADAAwardSummaryResponse = AdjudicationADAAwardSummaryResponse(
          bookingId = 2,
          offenderNo = "A0002TZ",
          adaSummaries = listOf(nomisSummary(days = 12)),
        ),
      )
      nomisApi.stubGetMergesFromDate(offenderNo = "A0002TZ", merges = emptyList())
    }

    @Test
    fun `will call DPS for each bookingId`() = runTest {
      service.generateReconciliationReport(2)
      adjudicationsApiServer.verify(getRequestedFor(urlPathEqualTo("/reported-adjudications/all-by-booking/1")))
      adjudicationsApiServer.verify(getRequestedFor(urlPathEqualTo("/reported-adjudications/all-by-booking/2")))
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
  incidentDetails = IncidentDetailsDto(
    locationUuid = UUID.randomUUID(),
    dateTimeOfIncident = LocalDateTime.parse("2023-01-01T10:00"),
    dateTimeOfDiscovery = LocalDateTime.parse("2023-01-01T10:00"),
    handoverDeadline = LocalDateTime.parse("2023-01-01T10:00"),
  ),
  isYouthOffender = false,
  incidentRole = IncidentRoleDto(),
  offenceDetails = OffenceDto(offenceCode = 1, offenceRule = OffenceRuleDto("51A", paragraphDescription = ""), protectedCharacteristics = emptyList()),
  incidentStatement = IncidentStatementDto("Fight", completed = true),
  createdByUserId = "JANE",
  createdDateTime = LocalDateTime.parse("2023-01-01T10:00"),
  status = ReportedAdjudicationDto.Status.CHARGE_PROVED,
  damages = emptyList(),
  evidence = emptyList(),
  witnesses = emptyList(),
  hearings = listOf(),
  disIssueHistory = emptyList(),
  outcomes = listOf(
    OutcomeHistoryDto(
      hearing = HearingDto(
        locationUuid = UUID.randomUUID(),
        dateTimeOfHearing = LocalDateTime.parse("2023-02-01T10:00"),
        oicHearingType = HearingDto.OicHearingType.GOV_ADULT,
        agencyId = "MDI",
      ),
      outcome = CombinedOutcomeDto(
        outcome = OutcomeDto(
          code = OutcomeDto.Code.CHARGE_PROVED,
          canRemove = false,
        ),
        referralOutcome = null,
      ),
    ),
  ),
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
  canActionFromHistory = false,
)

internal fun adaPunishment(days: Int, startDate: LocalDate = LocalDate.now(), type: PunishmentDto.Type = ADDITIONAL_DAYS) = PunishmentDto(type = type, schedule = PunishmentScheduleDto(days = days, duration = days, startDate = startDate, measurement = PunishmentScheduleDto.Measurement.DAYS), canRemove = true, canEdit = false, rehabilitativeActivities = emptyList())

internal fun nomisSummary(days: Int, effectiveDate: LocalDate = LocalDate.now(), adjudicationNumber: Long = 4000001): ADASummary = ADASummary(
  adjudicationNumber = adjudicationNumber,
  sanctionSequence = 1,
  days = days,
  effectiveDate = effectiveDate,
  sanctionStatus = CodeDescription("IMMEDIATE", "Immediate"),
)
