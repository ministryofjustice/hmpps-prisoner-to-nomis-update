package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.CombinedOutcomeDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.HearingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.HearingOutcomeDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OutcomeDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OutcomeHistoryDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.PunishmentDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.PunishmentScheduleDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.AdjudicationsApiExtension.Companion.adjudicationsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.Duration
import java.time.LocalDate

private const val DPS_CHARGE_NUMBER = "12345-1"
private const val DPS_HEARING_ID = "654321"
private const val CHARGE_SEQ = 1
private const val ADJUDICATION_NUMBER = 12345L
private const val PRISON_ID = "MDI"
private const val OFFENDER_NO = "A1234AA"
private const val NOMIS_HEARING_ID = 2345L

class AdjudicationsDataRepairResourceIntTest : IntegrationTestBase() {

  @DisplayName("POST /prisons/{prisonId}/prisoners/{offenderNo}/adjudication/dps-charge-number/{chargeNumber}/repair")
  @Nested
  inner class RepairAdjudication {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DPS_CHARGE_NUMBER/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DPS_CHARGE_NUMBER/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DPS_CHARGE_NUMBER/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingServer.stubDeleteMappingsForAdjudication()
        adjudicationsApiServer.stubChargeGet(
          DPS_CHARGE_NUMBER,
          offenderNo = OFFENDER_NO,
          hearings = listOf(
            HearingDto(
              locationId = 188489,
              dateTimeOfHearing = "2013-09-09T10:00:00",
              oicHearingType = HearingDto.OicHearingType.GOV_ADULT,
              agencyId = PRISON_ID,
              id = 634775,
              outcome = HearingOutcomeDto(
                adjudicator = "",
                code = HearingOutcomeDto.Code.ADJOURN,
                id = 596147,
                reason = HearingOutcomeDto.Reason.OTHER,
                details = "created via migration ",
                plea = HearingOutcomeDto.Plea.NOT_ASKED,
              ),
            ),
            HearingDto(
              locationId = 138711,
              dateTimeOfHearing = "2013-12-03T09:00:00",
              oicHearingType = HearingDto.OicHearingType.GOV_ADULT,
              agencyId = PRISON_ID,
              id = 634798,
              outcome = HearingOutcomeDto(
                adjudicator = "",
                code = HearingOutcomeDto.Code.COMPLETE,
                id = 596147,
                details = "Proven",
                plea = HearingOutcomeDto.Plea.ABSTAIN,
              ),
            ),
          ),
          punishments = listOf(
            PunishmentDto(
              type = PunishmentDto.Type.ADDITIONAL_DAYS,
              schedule = PunishmentScheduleDto(
                days = 28,
                measurement = PunishmentScheduleDto.Measurement.DAYS,
                duration = 28,
              ),
              canRemove = true,
              canEdit = true,
              rehabilitativeActivities = listOf(),
              id = 838308,
            ),
          ),
          outcomes = listOf(
            OutcomeHistoryDto(
              hearing = HearingDto(
                locationId = 188489,
                dateTimeOfHearing = "2013-09-09T10:00:00",
                oicHearingType = HearingDto.OicHearingType.GOV_ADULT,
                agencyId = PRISON_ID,
                id = 634775,
                outcome = HearingOutcomeDto(
                  adjudicator = "",
                  code = HearingOutcomeDto.Code.ADJOURN,
                  id = 596147,
                  reason = HearingOutcomeDto.Reason.OTHER,
                  details = "created via migration ",
                  plea = HearingOutcomeDto.Plea.NOT_ASKED,
                ),
              ),
            ),
            OutcomeHistoryDto(
              hearing = HearingDto(
                locationId = 138711,
                dateTimeOfHearing = "2013-12-03T09:00:00",
                oicHearingType = HearingDto.OicHearingType.GOV_ADULT,
                agencyId = PRISON_ID,
                id = 634798,
                outcome = HearingOutcomeDto(
                  adjudicator = "",
                  code = HearingOutcomeDto.Code.COMPLETE,
                  id = 596147,
                  details = "Proven",
                  plea = HearingOutcomeDto.Plea.ABSTAIN,
                ),
              ),
              outcome = CombinedOutcomeDto(
                outcome = OutcomeDto(
                  code = OutcomeDto.Code.CHARGE_PROVED,
                  canRemove = true,
                  id = 456945,
                  details = "Proven",
                ),
              ),
            ),
          ),
        )

        mappingServer.stubDeleteMappingsForAdjudication()
        mappingServer.stubGetByChargeNumberNotFoundFollowedByFound(DPS_CHARGE_NUMBER, ADJUDICATION_NUMBER)
        nomisApi.stubAdjudicationCreate(OFFENDER_NO, ADJUDICATION_NUMBER)
        mappingServer.stubCreateAdjudication()
        nomisApi.stubHearingsCreate(ADJUDICATION_NUMBER, 1634775, 1634798)
        mappingServer.stubGetByDpsHearingIdNotFoundFollowedByFound(dpsHearingId = "634775", nomisHearingId = 1634775)
        mappingServer.stubGetByDpsHearingIdNotFoundFollowedByFound(dpsHearingId = "634798", nomisHearingId = 1634798)
        mappingServer.stubCreateHearing()
        nomisApi.stubHearingResultUpsert(ADJUDICATION_NUMBER, nomisHearingId = 1634775)
        nomisApi.stubHearingResultUpsert(ADJUDICATION_NUMBER, nomisHearingId = 1634798)
        nomisApi.stubAdjudicationAwardsCreate(ADJUDICATION_NUMBER)
        mappingServer.stubCreatePunishments()

        webTestClient.mutate()
          .responseTimeout(Duration.ofMillis(300000000)).build().post().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DPS_CHARGE_NUMBER/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_ADJUDICATIONS")))
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will callback back to DPS adjudication service to get more details`() {
        adjudicationsApiServer.verify(getRequestedFor(urlEqualTo("/reported-adjudications/$DPS_CHARGE_NUMBER/v2")))
      }

      @Test
      fun `will delete any existing mappings by DPS Ids`() {
        mappingServer.verify(
          postRequestedFor(urlEqualTo("/mapping/adjudications/delete-mappings"))
            .withRequestBody(matchingJsonPath("dpsChargeNumber", equalTo(DPS_CHARGE_NUMBER)))
            .withRequestBody(matchingJsonPath("dpsHearingIds[0]", equalTo("634775")))
            .withRequestBody(matchingJsonPath("dpsHearingIds[1]", equalTo("634798")))
            .withRequestBody(matchingJsonPath("dpsPunishmentIds[0]", equalTo("838308"))),
        )
      }

      @Test
      fun `will call nomis api to create the adjudication`() {
        nomisApi.verify(postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/adjudications")))
      }

      @Test
      fun `will create a mapping between the adjudication and DPS charge`() {
        mappingServer.verify(
          postRequestedFor(urlEqualTo("/mapping/adjudications"))
            .withRequestBody(matchingJsonPath("adjudicationNumber", equalTo("$ADJUDICATION_NUMBER")))
            .withRequestBody(matchingJsonPath("chargeNumber", equalTo(DPS_CHARGE_NUMBER))),
        )
      }

      @Test
      fun `will create adjudication success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("adjudication-create-success"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create both hearings`() {
        nomisApi.verify(2, postRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings")))
      }

      @Test
      fun `will create a mapping between the NOMIS hearings and DPS hearings`() {
        mappingServer.verify(
          postRequestedFor(urlEqualTo("/mapping/hearings"))
            .withRequestBody(matchingJsonPath("nomisHearingId", equalTo("1634775")))
            .withRequestBody(matchingJsonPath("dpsHearingId", equalTo("634775"))),
        )
        mappingServer.verify(
          postRequestedFor(urlEqualTo("/mapping/hearings"))
            .withRequestBody(matchingJsonPath("nomisHearingId", equalTo("1634798")))
            .withRequestBody(matchingJsonPath("dpsHearingId", equalTo("634798"))),
        )
      }

      @Test
      fun `will create hearings success telemetry`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("hearing-create-success"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create both hearing outcomes`() {
        nomisApi.verify(
          postRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings/1634775/charge/1/result"))
            .withRequestBody(matchingJsonPath("findingCode", equalTo("ADJOURNED"))),
        )

        nomisApi.verify(
          postRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings/1634798/charge/1/result"))
            .withRequestBody(matchingJsonPath("findingCode", equalTo("PROVED"))),
        )
      }

      @Test
      fun `will create hearing outcomes success telemetry`() {
        verify(telemetryClient, times(2)).trackEvent(
          eq("hearing-result-upserted-success"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create punishment award`() {
        nomisApi.verify(
          postRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/charge/1/awards"))
            .withRequestBody(matchingJsonPath("awards[0].sanctionType", equalTo("ADA"))),
        )
      }

      @Test
      fun `will create a mapping between the NOMIS award and DPS punishment`() {
        mappingServer.verify(
          postRequestedFor(urlEqualTo("/mapping/punishments"))
            .withRequestBody(matchingJsonPath("punishments[0].dpsPunishmentId", equalTo("838308"))),
        )
      }

      @Test
      fun `will create award punishment success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("punishment-create-success"),
          any(),
          isNull(),
        )
      }

      @Test
      fun `will create repair success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("adjudication-adjudication-repair"),
          check {
            assertThat(it["chargeNumber"]).isEqualTo(DPS_CHARGE_NUMBER)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
            assertThat(it["adjudicationNumber"]).isEqualTo(ADJUDICATION_NUMBER.toString())
          },
          isNull(),
        )
      }
    }
  }

  @DisplayName("POST /prisons/{prisonId}/prisoners/{offenderNo}/adjudication/dps-charge-number/{chargeNumber}/punishments/repair")
  @Nested
  inner class RepairPunishments {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DPS_CHARGE_NUMBER/punishments/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DPS_CHARGE_NUMBER/punishments/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DPS_CHARGE_NUMBER/punishments/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByChargeNumber(DPS_CHARGE_NUMBER, ADJUDICATION_NUMBER)
        adjudicationsApiServer.stubChargeGet(
          DPS_CHARGE_NUMBER,
          offenderNo = OFFENDER_NO,
          // language=json
          punishments = listOf(
            PunishmentDto(
              id = 634,
              type = PunishmentDto.Type.CONFINEMENT,
              rehabilitativeActivities = emptyList(),
              schedule = PunishmentScheduleDto(
                days = 3,
                duration = 3,
                startDate = LocalDate.parse("2023-10-04"),
                endDate = LocalDate.parse("2023-10-06"),
                measurement = PunishmentScheduleDto.Measurement.DAYS,
              ),
              canRemove = true,
              canEdit = true,
            ),
            PunishmentDto(
              id = 667,
              type = PunishmentDto.Type.EXTRA_WORK,
              rehabilitativeActivities = emptyList(),
              schedule = PunishmentScheduleDto(
                days = 12,
                duration = 12,
                suspendedUntil = LocalDate.parse("2023-10-18"),
                startDate = null,
                endDate = null,
                measurement = PunishmentScheduleDto.Measurement.DAYS,
              ),
              canRemove = true,
              canEdit = true,
            ),
          ),
        )

        mappingServer.stubGetPunishments(dpsPunishmentId = "634", nomisBookingId = 12345, nomisSanctionSequence = 10)
        mappingServer.stubGetPunishmentsWithError(dpsPunishmentId = "667", status = 404)

        nomisApi.stubAdjudicationAwardsUpdate(
          ADJUDICATION_NUMBER,
          CHARGE_SEQ,
          createdAwardIds = listOf(12345L to 11),
          deletedAwardIds = listOf(12345L to 9, 12345L to 8),
        )
        mappingServer.stubUpdatePunishments()

        webTestClient.post().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DPS_CHARGE_NUMBER/punishments/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_ADJUDICATIONS")))
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will callback back to adjudication service to get more details`() {
        adjudicationsApiServer.verify(getRequestedFor(urlEqualTo("/reported-adjudications/$DPS_CHARGE_NUMBER/v2")))
      }

      @Test
      fun `will create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("punishment-update-success"),
          check {
            assertThat(it["chargeNumber"]).isEqualTo(DPS_CHARGE_NUMBER)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
            assertThat(it["adjudicationNumber"]).isEqualTo(ADJUDICATION_NUMBER.toString())
            assertThat(it["chargeSequence"]).isEqualTo(CHARGE_SEQ.toString())
            assertThat(it["punishmentsCreatedCount"]).isEqualTo("1")
            assertThat(it["punishmentsUpdatedCount"]).isEqualTo("1")
            assertThat(it["punishmentsDeletedCount"]).isEqualTo("2")
          },
          isNull(),
        )
      }

      @Test
      fun `will create audit telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("adjudication-punishment-repair"),
          check {
            assertThat(it["chargeNumber"]).isEqualTo(DPS_CHARGE_NUMBER)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to update the awards`() {
        nomisApi.verify(putRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/charge/$CHARGE_SEQ/awards")))
      }

      @Test
      fun `will map between DPS punishments to NOMIS awards dividing between new and existing awards`() {
        nomisApi.verify(
          putRequestedFor(anyUrl())
            .withRequestBody(matchingJsonPath("awardsToCreate[0].sanctionType", equalTo("EXTW")))
            .withRequestBody(matchingJsonPath("awardsToCreate[0].sanctionStatus", equalTo("SUSPENDED")))
            .withRequestBody(matchingJsonPath("awardsToCreate[0].effectiveDate", equalTo("2023-10-18")))
            .withRequestBody(matchingJsonPath("awardsToCreate[0].sanctionDays", equalTo("12")))
            .withRequestBody(matchingJsonPath("awardsToUpdate[0].sanctionSequence", equalTo("10")))
            .withRequestBody(matchingJsonPath("awardsToUpdate[0].award.sanctionType", equalTo("CC")))
            .withRequestBody(matchingJsonPath("awardsToUpdate[0].award.sanctionStatus", equalTo("IMMEDIATE")))
            .withRequestBody(matchingJsonPath("awardsToUpdate[0].award.effectiveDate", equalTo("2023-10-04")))
            .withRequestBody(matchingJsonPath("awardsToUpdate[0].award.sanctionDays", equalTo("3"))),
        )
      }

      @Test
      fun `will create a mapping between the set of new punishments and awards and delete ones not referenced`() {
        mappingServer.verify(
          putRequestedFor(urlEqualTo("/mapping/punishments"))
            .withRequestBody(matchingJsonPath("punishmentsToCreate[0].nomisBookingId", equalTo("12345")))
            .withRequestBody(matchingJsonPath("punishmentsToCreate[0].nomisSanctionSequence", equalTo("11")))
            .withRequestBody(matchingJsonPath("punishmentsToCreate[0].dpsPunishmentId", equalTo("667")))
            .withRequestBody(matchingJsonPath("punishmentsToDelete[0].nomisBookingId", equalTo("12345")))
            .withRequestBody(matchingJsonPath("punishmentsToDelete[0].nomisSanctionSequence", equalTo("9")))
            .withRequestBody(matchingJsonPath("punishmentsToDelete[1].nomisBookingId", equalTo("12345")))
            .withRequestBody(matchingJsonPath("punishmentsToDelete[1].nomisSanctionSequence", equalTo("8"))),
        )
      }
    }
  }

  @DisplayName("DELETE /prisons/{prisonId}/prisoners/{offenderNo}/adjudication/dps-charge-number/{chargeNumber}/hearing/dps-hearing-id/{hearingId}/result")
  @Nested
  inner class RepairDeleteHearingResult {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.delete().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DPS_CHARGE_NUMBER/hearing/dps-hearing-id/$DPS_HEARING_ID/result")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.delete().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DPS_CHARGE_NUMBER/hearing/dps-hearing-id/$DPS_HEARING_ID/result")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.delete().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DPS_CHARGE_NUMBER/hearing/dps-hearing-id/$DPS_HEARING_ID/result")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByChargeNumber(DPS_CHARGE_NUMBER, ADJUDICATION_NUMBER)
        mappingServer.stubGetByDpsHearingId(DPS_HEARING_ID, NOMIS_HEARING_ID)
        adjudicationsApiServer.stubChargeGet(
          chargeNumber = DPS_CHARGE_NUMBER,
          offenderNo = OFFENDER_NO,
        )
        nomisApi.stubHearingResultDelete(
          ADJUDICATION_NUMBER,
          NOMIS_HEARING_ID,
          CHARGE_SEQ,
          listOf(12345L to 10, 12345L to 11),
        )
        mappingServer.stubUpdatePunishments()

        webTestClient.delete().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DPS_CHARGE_NUMBER/hearing/dps-hearing-id/$DPS_HEARING_ID/result")
          .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_ADJUDICATIONS")))
          .exchange()
          .expectStatus().isNoContent
      }

      @Test
      fun `will create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("hearing-result-deleted-success"),
          check {
            assertThat(it["chargeNumber"]).isEqualTo(DPS_CHARGE_NUMBER)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
            assertThat(it["dpsHearingId"]).isEqualTo(DPS_HEARING_ID)
            assertThat(it["nomisHearingId"]).isEqualTo(NOMIS_HEARING_ID.toString())
            assertThat(it["punishmentsDeletedCount"]).isEqualTo("2")
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to delete the hearing result`() {
        nomisApi.verify(WireMock.deleteRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings/$NOMIS_HEARING_ID/charge/$CHARGE_SEQ/result")))
      }

      @Test
      fun `will call the mapping service to remove the punishment mappings`() {
        mappingServer.verify(
          putRequestedFor(urlEqualTo("/mapping/punishments"))
            .withRequestBody(
              matchingJsonPath(
                "punishmentsToDelete[0].nomisBookingId",
                equalTo("12345"),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "punishmentsToDelete[0].nomisSanctionSequence",
                equalTo("10"),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "punishmentsToDelete[1].nomisBookingId",
                equalTo("12345"),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "punishmentsToDelete[1].nomisSanctionSequence",
                equalTo("11"),
              ),
            ),
        )
      }

      @Test
      fun `will create audit telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("adjudication-hearing-delete-result-repair"),
          check {
            assertThat(it["chargeNumber"]).isEqualTo(DPS_CHARGE_NUMBER)
            assertThat(it["hearingId"]).isEqualTo(DPS_HEARING_ID)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
          },
          isNull(),
        )
      }
    }
  }

  @DisplayName("POST /prisons/{prisonId}/prisoners/{offenderNo}/adjudication/dps-charge-number/{chargeNumber}/hearing/dps-hearing-id/{hearingId}/outcome")
  @Nested
  inner class RepairOutcome {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DPS_CHARGE_NUMBER/hearing/dps-hearing-id/$DPS_HEARING_ID/outcome")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DPS_CHARGE_NUMBER/hearing/dps-hearing-id/$DPS_HEARING_ID/outcome")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DPS_CHARGE_NUMBER/hearing/dps-hearing-id/$DPS_HEARING_ID/outcome\")")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByChargeNumber(DPS_CHARGE_NUMBER, ADJUDICATION_NUMBER)
        adjudicationsApiServer.stubChargeGetWithHearingAndNotProceedOutcome(
          hearingId = DPS_HEARING_ID.toLong(),
          chargeNumber = DPS_CHARGE_NUMBER,
          offenderNo = OFFENDER_NO,
        )
        nomisApi.stubHearingResultUpsert(ADJUDICATION_NUMBER, NOMIS_HEARING_ID)
        mappingServer.stubGetByDpsHearingId(DPS_HEARING_ID, NOMIS_HEARING_ID)

        webTestClient.post().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DPS_CHARGE_NUMBER/hearing/dps-hearing-id/$DPS_HEARING_ID/outcome")
          .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_ADJUDICATIONS")))
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will callback back to adjudication service to get more details`() {
        adjudicationsApiServer.verify(getRequestedFor(urlEqualTo("/reported-adjudications/$DPS_CHARGE_NUMBER/v2")))
      }

      @Test
      fun `will create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("hearing-result-upserted-success"),
          check {
            assertThat(it["chargeNumber"]).isEqualTo(DPS_CHARGE_NUMBER)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
            assertThat(it["adjudicationNumber"]).isEqualTo(ADJUDICATION_NUMBER.toString())
            assertThat(it["chargeSequence"]).isEqualTo(CHARGE_SEQ.toString())
            assertThat(it["dpsHearingId"]).isEqualTo("654321")
            assertThat(it["nomisHearingId"]).isEqualTo("2345")
            assertThat(it["findingCode"]).isEqualTo("NOT_PROCEED")
            assertThat(it["plea"]).isEqualTo("NOT_ASKED")
          },
          isNull(),
        )
      }

      @Test
      fun `will create audit telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("adjudication-outcome-repair"),
          check {
            assertThat(it["chargeNumber"]).isEqualTo(DPS_CHARGE_NUMBER)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
            assertThat(it["hearingId"]).isEqualTo(DPS_HEARING_ID)
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to update the outcome`() {
        nomisApi.verify(postRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings/$NOMIS_HEARING_ID/charge/$CHARGE_SEQ/result")))
      }
    }
  }

  @DisplayName("POST /prisons/{prisonId}/prisoners/{offenderNo}/adjudication/dps-charge-number/{chargeNumber}/hearing/dps-hearing-id/{hearingId}")
  @Nested
  inner class RepairHearing {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DPS_CHARGE_NUMBER/hearing/dps-hearing-id/$DPS_HEARING_ID")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DPS_CHARGE_NUMBER/hearing/dps-hearing-id/$DPS_HEARING_ID")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DPS_CHARGE_NUMBER/hearing/dps-hearing-id/$DPS_HEARING_ID")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByChargeNumber(DPS_CHARGE_NUMBER, ADJUDICATION_NUMBER)
        adjudicationsApiServer.stubChargeGet(DPS_CHARGE_NUMBER, offenderNo = OFFENDER_NO, hearingId = DPS_HEARING_ID.toLong())
        nomisApi.stubHearingCreate(ADJUDICATION_NUMBER, NOMIS_HEARING_ID)
        mappingServer.stubGetByDpsHearingIdWithError(DPS_HEARING_ID, 404)
        mappingServer.stubCreateHearing()

        webTestClient.post().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DPS_CHARGE_NUMBER/hearing/dps-hearing-id/$DPS_HEARING_ID")
          .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_ADJUDICATIONS")))
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will callback back to adjudication service to get more details`() {
        adjudicationsApiServer.verify(getRequestedFor(urlEqualTo("/reported-adjudications/$DPS_CHARGE_NUMBER/v2")))
      }

      @Test
      fun `will create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("hearing-create-success"),
          check {
            assertThat(it["chargeNumber"]).isEqualTo(DPS_CHARGE_NUMBER)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
            assertThat(it["dpsHearingId"]).isEqualTo(DPS_HEARING_ID)
            assertThat(it["nomisHearingId"]).isEqualTo(NOMIS_HEARING_ID.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the hearing`() {
        nomisApi.verify(postRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings")))
      }

      @Test
      fun `will create a mapping between the two hearings`() {
        mappingServer.verify(
          postRequestedFor(urlEqualTo("/mapping/hearings"))
            .withRequestBody(matchingJsonPath("dpsHearingId", equalTo(DPS_HEARING_ID)))
            .withRequestBody(matchingJsonPath("nomisHearingId", equalTo(NOMIS_HEARING_ID.toString()))),
        )
      }
    }
  }
}
