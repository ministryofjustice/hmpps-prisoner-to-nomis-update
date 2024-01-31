package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.verify
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.AdjudicationsApiExtension.Companion.adjudicationsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

private const val DP_CHARGE_NUMBER = "12345-1"
private const val CHARGE_SEQ = 1
private const val ADJUDICATION_NUMBER = 12345L
private const val PRISON_ID = "MDI"
private const val OFFENDER_NO = "A1234AA"

class AdjudicationsDataRepairResourceIntTest : IntegrationTestBase() {

  @DisplayName("POST /prisons/{prisonId}/prisoners/{offenderNo}/adjudication/dps-charge-number/{chargeNumber}/punishments/repair")
  @Nested
  inner class RepairPunishments {

    @Nested
    inner class Security {
      @Test
      fun `access forbidden when no role`() {
        webTestClient.post().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DP_CHARGE_NUMBER/punishments/repair")
          .headers(setAuthorisation(roles = listOf()))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access forbidden with wrong role`() {
        webTestClient.post().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DP_CHARGE_NUMBER/punishments/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_BANANAS")))
          .exchange()
          .expectStatus().isForbidden
      }

      @Test
      fun `access unauthorised with no auth token`() {
        webTestClient.post().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DP_CHARGE_NUMBER/punishments/repair")
          .exchange()
          .expectStatus().isUnauthorized
      }
    }

    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByChargeNumber(DP_CHARGE_NUMBER, ADJUDICATION_NUMBER)
        adjudicationsApiServer.stubChargeGet(
          DP_CHARGE_NUMBER,
          offenderNo = OFFENDER_NO,
          // language=json
          punishments = """
          [
            {
                "id": 634,
                "type": "CONFINEMENT",
                "schedule": {
                    "days": 3,
                    "startDate": "2023-10-04",
                    "endDate": "2023-10-06"
                }
            },
            {
                "id": 667,
                "type": "EXTRA_WORK",
                "schedule": {
                    "days": 12,
                    "suspendedUntil": "2023-10-18"
                }
            }
        ]
          """.trimIndent(),
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

        webTestClient.post().uri("/prisons/$PRISON_ID/prisoners/$OFFENDER_NO/adjudication/dps-charge-number/$DP_CHARGE_NUMBER/punishments/repair")
          .headers(setAuthorisation(roles = listOf("ROLE_NOMIS_ADJUDICATIONS")))
          .exchange()
          .expectStatus().isOk
      }

      @Test
      fun `will callback back to adjudication service to get more details`() {
        adjudicationsApiServer.verify(getRequestedFor(urlEqualTo("/reported-adjudications/$DP_CHARGE_NUMBER/v2")))
      }

      @Test
      fun `will create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("punishment-update-success"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["chargeNumber"]).isEqualTo(DP_CHARGE_NUMBER)
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
            Assertions.assertThat(it["adjudicationNumber"]).isEqualTo(ADJUDICATION_NUMBER.toString())
            Assertions.assertThat(it["chargeSequence"]).isEqualTo(CHARGE_SEQ.toString())
            Assertions.assertThat(it["punishmentsCreatedCount"]).isEqualTo("1")
            Assertions.assertThat(it["punishmentsUpdatedCount"]).isEqualTo("1")
            Assertions.assertThat(it["punishmentsDeletedCount"]).isEqualTo("2")
          },
          isNull(),
        )
      }

      @Test
      fun `will create audit telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("adjudication-punishment-repair"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["chargeNumber"]).isEqualTo(DP_CHARGE_NUMBER)
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
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
}
