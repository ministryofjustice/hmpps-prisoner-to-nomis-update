package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.AdjudicationsApiExtension.Companion.adjudicationsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val CHARGE_NUMBER_FOR_CREATION = "12345-1"
private const val CHARGE_SEQ = 1
private const val ADJUDICATION_NUMBER = 12345L
private const val CONSECUTIVE_CHARGE_NUMBER =
  // language=text
  "987654-2"
private const val CONSECUTIVE_CHARGE_SEQ = 2
private const val CONSECUTIVE_ADJUDICATION_NUMBER = 12345L
private const val PRISON_ID = "MDI"
private const val OFFENDER_NO = "A1234AA"

class PunishmentsToNomisIntTest : SqsIntegrationTestBase() {

  @Nested
  inner class CreatePunishments {
    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_CREATION, ADJUDICATION_NUMBER)
        adjudicationsApiServer.stubChargeGet(
          CHARGE_NUMBER_FOR_CREATION,
          offenderNo = OFFENDER_NO,
          // language=json
          punishments = """
          [
            {
                "id": 634,
                "type": "CONFINEMENT",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 3,
                    "startDate": "2023-10-04",
                    "endDate": "2023-10-06",
                    "measurement": "DAYS"
                }
            },
            {
                "id": 667,
                "type": "EXTRA_WORK",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 12,
                    "suspendedUntil": "2023-10-18",
                    "measurement": "DAYS"
                }
            }
        ]
          """.trimIndent(),
        )

        nomisApi.stubAdjudicationAwardsCreate(
          ADJUDICATION_NUMBER,
          CHARGE_SEQ,
          awardIds = listOf(12345L to 10, 12345L to 11),
        )
        mappingServer.stubCreatePunishments()
        publishCreatePunishmentsDomainEvent()
      }

      @Test
      fun `will callback back to adjudication service to get more details`() {
        waitForCreatePunishmentProcessingToBeComplete()

        adjudicationsApiServer.verify(getRequestedFor(urlEqualTo("/reported-adjudications/$CHARGE_NUMBER_FOR_CREATION/v2")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForCreatePunishmentProcessingToBeComplete()

        verify(telemetryClient).trackEvent(
          eq("punishment-create-success"),
          org.mockito.kotlin.check {
            assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_CREATION)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
            assertThat(it["adjudicationNumber"]).isEqualTo(ADJUDICATION_NUMBER.toString())
            assertThat(it["chargeSequence"]).isEqualTo(CHARGE_SEQ.toString())
            assertThat(it["punishmentsCount"]).isEqualTo("2")
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the awards`() {
        waitForCreatePunishmentProcessingToBeComplete()

        nomisApi.verify(postRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/charge/$CHARGE_SEQ/awards")))
      }

      @Test
      fun `will map DPS punishments to NOMIS awards`() {
        waitForCreatePunishmentProcessingToBeComplete()

        nomisApi.verify(
          postRequestedFor(anyUrl())
            .withRequestBody(matchingJsonPath("awards[0].sanctionType", equalTo("CC")))
            .withRequestBody(matchingJsonPath("awards[0].sanctionStatus", equalTo("IMMEDIATE")))
            .withRequestBody(matchingJsonPath("awards[0].effectiveDate", equalTo("2023-10-04")))
            .withRequestBody(matchingJsonPath("awards[0].sanctionDays", equalTo("3")))
            .withRequestBody(matchingJsonPath("awards[1].sanctionType", equalTo("EXTW")))
            .withRequestBody(matchingJsonPath("awards[1].sanctionStatus", equalTo("SUSPENDED")))
            .withRequestBody(matchingJsonPath("awards[1].effectiveDate", equalTo("2023-10-18")))
            .withRequestBody(matchingJsonPath("awards[1].sanctionDays", equalTo("12"))),
        )
      }

      @Test
      fun `will create a mapping between the set of punishments and awards`() {
        waitForCreatePunishmentProcessingToBeComplete()

        await untilAsserted {
          mappingServer.verify(
            postRequestedFor(urlEqualTo("/mapping/punishments"))
              .withRequestBody(matchingJsonPath("punishments[0].nomisBookingId", equalTo("12345")))
              .withRequestBody(matchingJsonPath("punishments[0].nomisSanctionSequence", equalTo("10")))
              .withRequestBody(matchingJsonPath("punishments[0].dpsPunishmentId", equalTo("634")))
              .withRequestBody(matchingJsonPath("punishments[1].nomisBookingId", equalTo("12345")))
              .withRequestBody(matchingJsonPath("punishments[1].nomisSanctionSequence", equalTo("11")))
              .withRequestBody(matchingJsonPath("punishments[1].dpsPunishmentId", equalTo("667"))),
          )
        }
      }

      @Nested
      inner class ConsecutivePunishments {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByChargeNumber(
            chargeNumber = CHARGE_NUMBER_FOR_CREATION,
            adjudicationNumber = ADJUDICATION_NUMBER,
          )
          mappingServer.stubGetByChargeNumber(
            chargeNumber = CONSECUTIVE_CHARGE_NUMBER,
            adjudicationNumber = CONSECUTIVE_ADJUDICATION_NUMBER,
            chargeSequence = CONSECUTIVE_CHARGE_SEQ,
          )
          adjudicationsApiServer.stubChargeGet(
            CHARGE_NUMBER_FOR_CREATION,
            offenderNo = OFFENDER_NO,
            // language=json
            punishments = """
          [
            {
                "id": 634,
                "type": "CONFINEMENT",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 3,
                    "startDate": "2023-10-04",
                    "endDate": "2023-10-06",
                    "measurement": "DAYS"
                }
            },
            {
                "id": 689,
                "type": "ADDITIONAL_DAYS",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 2,
                    "measurement": "DAYS"
                },
                "consecutiveChargeNumber": "$CONSECUTIVE_CHARGE_NUMBER",
                "consecutiveReportAvailable": true
            }        
          ]
            """.trimIndent(),
          )
        }

        @Test
        fun `will find mapping for both charges`() {
          waitForCreatePunishmentProcessingToBeComplete()
          mappingServer.verify(
            getRequestedFor(urlEqualTo("/mapping/adjudications/charge-number/$CHARGE_NUMBER_FOR_CREATION")),
          )
          mappingServer.verify(
            getRequestedFor(urlEqualTo("/mapping/adjudications/charge-number/$CONSECUTIVE_CHARGE_NUMBER")),
          )
        }

        @Test
        fun `will map DPS punishments to NOMIS awards`() {
          waitForCreatePunishmentProcessingToBeComplete()

          nomisApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBody(matchingJsonPath("awards[0].sanctionType", equalTo("CC")))
              .withRequestBody(matchingJsonPath("awards[0].sanctionStatus", equalTo("IMMEDIATE")))
              .withRequestBody(matchingJsonPath("awards[0].effectiveDate", equalTo("2023-10-04")))
              .withRequestBody(matchingJsonPath("awards[0].sanctionDays", equalTo("3")))
              .withRequestBody(matchingJsonPath("awards[1].sanctionType", equalTo("ADA")))
              .withRequestBody(matchingJsonPath("awards[1].sanctionStatus", equalTo("IMMEDIATE")))
              .withRequestBody(
                matchingJsonPath(
                  "awards[1].effectiveDate",
                  equalTo(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)),
                ),
              )
              .withRequestBody(matchingJsonPath("awards[1].sanctionDays", equalTo("2")))
              .withRequestBody(
                matchingJsonPath(
                  "awards[1].consecutiveCharge.adjudicationNumber",
                  equalTo("$CONSECUTIVE_ADJUDICATION_NUMBER"),
                ),
              )
              .withRequestBody(
                matchingJsonPath(
                  "awards[1].consecutiveCharge.chargeSequence",
                  equalTo("$CONSECUTIVE_CHARGE_SEQ"),
                ),
              ),
          )
        }
      }

      @Nested
      inner class CommentForAward {
        @BeforeEach
        fun setUp() {
          adjudicationsApiServer.stubChargeGet(
            CHARGE_NUMBER_FOR_CREATION,
            offenderNo = OFFENDER_NO,
            // language=json
            punishments = """
          [
            {
                "id": 1,
                "type": "PRIVILEGE",
                "privilegeType": "ASSOCIATION",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 3,
                    "startDate": "2023-10-04",
                    "endDate": "2023-10-06",
                    "measurement": "DAYS"
                }
            },
            {
                "id": 2,
                "type": "PRIVILEGE",
                "privilegeType": "OTHER",
                "otherPrivilege": "Daily walk",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 3,
                    "startDate": "2023-10-04",
                    "endDate": "2023-10-06",
                    "measurement": "DAYS"
                }
            },
            {
                "id": 3,
                "type": "CONFINEMENT",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 3,
                    "startDate": "2023-10-04",
                    "endDate": "2023-10-06",
                    "measurement": "DAYS"
                }
            },
            {
                "id": 4,
                "type": "DAMAGES_OWED",
                "damagesOwedAmount": 45.1,
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 3,
                    "startDate": "2023-10-04",
                    "endDate": "2023-10-06",
                    "measurement": "DAYS"
                }
            }
          ]
            """.trimIndent(),
          )
        }

        @Test
        fun `comment will represent the punishment`() {
          waitForCreatePunishmentProcessingToBeComplete()

          nomisApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBody(matchingJsonPath("awards[0].commentText", equalTo("Added by DPS: Loss of ASSOCIATION")))
              .withRequestBody(matchingJsonPath("awards[1].commentText", equalTo("Added by DPS: Loss of Daily walk")))
              .withRequestBody(matchingJsonPath("awards[2].commentText", equalTo("Added by DPS")))
              .withRequestBody(
                matchingJsonPath(
                  "awards[3].commentText",
                  equalTo("Added by DPS: OTHER - Damages owed £45.10"),
                ),
              ),

          )
        }
      }

      @Nested
      inner class StatusForAward {
        @BeforeEach
        fun setUp() {
          adjudicationsApiServer.stubChargeGet(
            CHARGE_NUMBER_FOR_CREATION,
            offenderNo = OFFENDER_NO,
            // language=json
            punishments = """
          [
            {
                "id": 1,
                "type": "PROSPECTIVE_DAYS",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 3,
                    "suspendedUntil": "2023-10-06",
                    "measurement": "DAYS"
                }
            },
            {
                "id": 2,
                "type": "PROSPECTIVE_DAYS",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 3,
                    "measurement": "DAYS"
                }
            },
            {
                "id": 3,
                "type": "PROSPECTIVE_DAYS",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 3,
                    "startDate": "2023-10-06",
                    "measurement": "DAYS"
                }
            },
            {
                "id": 4,
                "type": "ADDITIONAL_DAYS",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 3,
                    "suspendedUntil": "2023-10-06",
                    "measurement": "DAYS"
                }
            },
            {
                "id": 5,
                "type": "ADDITIONAL_DAYS",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 3,
                    "measurement": "DAYS"
                }
            },
            {
                "id": 6,
                "type": "ADDITIONAL_DAYS",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 3,
                    "startDate": "2023-10-06",
                    "measurement": "DAYS"
                }
            }
          ]
            """.trimIndent(),
          )
        }

        @Test
        fun `for additional days the status is determined for the DPS type along with the schedule`() {
          waitForCreatePunishmentProcessingToBeComplete()

          nomisApi.verify(
            postRequestedFor(anyUrl())
              .withRequestBody(matchingJsonPath("awards[0].sanctionStatus", equalTo("SUSP_PROSP")))
              .withRequestBody(matchingJsonPath("awards[1].sanctionStatus", equalTo("PROSPECTIVE")))
              .withRequestBody(matchingJsonPath("awards[2].sanctionStatus", equalTo("IMMEDIATE")))
              .withRequestBody(matchingJsonPath("awards[3].sanctionStatus", equalTo("SUSPENDED")))
              .withRequestBody(matchingJsonPath("awards[4].sanctionStatus", equalTo("IMMEDIATE")))
              .withRequestBody(matchingJsonPath("awards[5].sanctionStatus", equalTo("IMMEDIATE"))),

          )
        }
      }
    }

    @Nested
    inner class ErrorScenarios {
      @Nested
      inner class WhenAdjudicationMappingNotFound {

        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByChargeNumberWithError(CHARGE_NUMBER_FOR_CREATION, 404)
          publishCreatePunishmentsDomainEvent()
        }

        @Test
        fun `an error will lead to message being added to DLQ`() {
          await untilCallTo {
            awsSqsAdjudicationDlqClient!!.countAllMessagesOnQueue(adjudicationDlqUrl!!).get()
          } matches { it == 1 }
        }

        @Test
        fun `will track failure telemetry for each retry`() {
          await untilAsserted {
            verify(telemetryClient, Mockito.times(3)).trackEvent(
              Mockito.eq("punishment-create-failed"),
              org.mockito.kotlin.check {
                assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_CREATION)
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
                assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenMappingServiceFailsOnce {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_CREATION, ADJUDICATION_NUMBER)
          adjudicationsApiServer.stubChargeGet(
            CHARGE_NUMBER_FOR_CREATION,
            offenderNo = OFFENDER_NO,
            // language=json
            punishments = """
          [
            {
                "id": 634,
                "type": "CONFINEMENT",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 3,
                    "startDate": "2023-10-04",
                    "endDate": "2023-10-06",
                    "measurement": "DAYS"
                }
            },
            {
                "id": 667,
                "type": "EXTRA_WORK",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 12,
                    "suspendedUntil": "2023-10-18",
                    "measurement": "DAYS"
                }
            }
        ]
            """.trimIndent(),
          )

          nomisApi.stubAdjudicationAwardsCreate(
            ADJUDICATION_NUMBER,
            CHARGE_SEQ,
            awardIds = listOf(12345L to 10, 12345L to 11),
          )
          mappingServer.stubCreatePunishmentsWithErrorFollowedBySuccess()
          publishCreatePunishmentsDomainEvent()

          mappingServer.stubCreateAdjudicationWithErrorFollowedBySlowSuccess()
        }

        @Test
        fun `should only create the NOMIS punishments once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("punishment-create-mapping-retry-success"),
              any(),
              isNull(),
            )
          }

          nomisApi.verify(
            1,
            postRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/charge/$CHARGE_SEQ/awards")),
          )
        }

        @Test
        fun `will eventually create a mapping after NOMIS adjudication is created`() {
          await untilAsserted {
            mappingServer.verify(
              2,
              postRequestedFor(urlEqualTo("/mapping/punishments")),
            )
          }
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("punishment-create-mapping-retry-success"),
              any(),
              isNull(),
            )
          }
        }
      }
    }

    private fun waitForCreatePunishmentProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  @Nested
  inner class UpdatePunishments {
    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_CREATION, ADJUDICATION_NUMBER)
        adjudicationsApiServer.stubChargeGet(
          CHARGE_NUMBER_FOR_CREATION,
          offenderNo = OFFENDER_NO,
          // language=json
          punishments = """
          [
            {
                "id": 634,
                "type": "CONFINEMENT",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 3,
                    "startDate": "2023-10-04",
                    "endDate": "2023-10-06",
                    "measurement": "DAYS"
                }
            },
            {
                "id": 667,
                "type": "EXTRA_WORK",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 12,
                    "suspendedUntil": "2023-10-18",
                    "measurement": "DAYS"
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
        publishUpdatePunishmentsDomainEvent()
        waitForUpdatePunishmentProcessingToBeComplete()
      }

      @Test
      fun `will callback back to adjudication service to get more details`() {
        adjudicationsApiServer.verify(getRequestedFor(urlEqualTo("/reported-adjudications/$CHARGE_NUMBER_FOR_CREATION/v2")))
      }

      @Test
      fun `will create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("punishment-update-success"),
          org.mockito.kotlin.check {
            assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_CREATION)
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
        await untilAsserted {
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

    @Nested
    inner class ErrorScenarios {
      @Nested
      inner class WhenAdjudicationMappingNotFound {

        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByChargeNumberWithError(CHARGE_NUMBER_FOR_CREATION, 404)
          publishUpdatePunishmentsDomainEvent()
        }

        @Test
        fun `an error will lead to message being added to DLQ`() {
          await untilCallTo {
            awsSqsAdjudicationDlqClient!!.countAllMessagesOnQueue(adjudicationDlqUrl!!).get()
          } matches { it == 1 }
        }

        @Test
        fun `will track failure telemetry for each retry`() {
          await untilAsserted {
            verify(telemetryClient, Mockito.times(3)).trackEvent(
              Mockito.eq("punishment-update-failed"),
              org.mockito.kotlin.check {
                assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_CREATION)
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
                assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenMappingServiceFailsOnce {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_CREATION, ADJUDICATION_NUMBER)
          adjudicationsApiServer.stubChargeGet(
            CHARGE_NUMBER_FOR_CREATION,
            offenderNo = OFFENDER_NO,
            // language=json
            punishments = """
          [
            {
                "id": 634,
                "type": "CONFINEMENT",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 3,
                    "startDate": "2023-10-04",
                    "endDate": "2023-10-06",
                    "measurement": "DAYS"
                }
            },
            {
                "id": 667,
                "type": "EXTRA_WORK",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 12,
                    "suspendedUntil": "2023-10-18",
                    "measurement": "DAYS"
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
          mappingServer.stubUpdatePunishmentsWithErrorFollowedBySuccess()
          publishUpdatePunishmentsDomainEvent()
        }

        @Test
        fun `should only create the NOMIS punishments once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("punishment-update-mapping-retry-success"),
              any(),
              isNull(),
            )
          }

          nomisApi.verify(
            1,
            putRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/charge/$CHARGE_SEQ/awards")),
          )
        }

        @Test
        fun `will eventually update mappings after NOMIS punishments are updated`() {
          await untilAsserted {
            mappingServer.verify(
              2,
              putRequestedFor(urlEqualTo("/mapping/punishments")),
            )
          }
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("punishment-update-mapping-retry-success"),
              any(),
              isNull(),
            )
          }
        }
      }
    }

    private fun waitForUpdatePunishmentProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  @Nested
  inner class DeletePunishments {
    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_CREATION, ADJUDICATION_NUMBER)
        adjudicationsApiServer.stubChargeGet(
          CHARGE_NUMBER_FOR_CREATION,
          offenderNo = OFFENDER_NO,
        )

        mappingServer.stubGetPunishments(dpsPunishmentId = "634", nomisBookingId = 12345, nomisSanctionSequence = 10)
        mappingServer.stubGetPunishmentsWithError(dpsPunishmentId = "667", status = 404)

        nomisApi.stubAdjudicationAwardsDelete(
          ADJUDICATION_NUMBER,
          CHARGE_SEQ,
          deletedAwardIds = listOf(12345L to 9, 12345L to 8),
        )
        mappingServer.stubUpdatePunishments()
        publishDeletePunishmentsDomainEvent()
        waitForProcessingToBeComplete()
      }

      @Test
      fun `will callback back to adjudication service to get more details`() {
        adjudicationsApiServer.verify(getRequestedFor(urlEqualTo("/reported-adjudications/$CHARGE_NUMBER_FOR_CREATION/v2")))
      }

      @Test
      fun `will create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("punishment-delete-success"),
          org.mockito.kotlin.check {
            assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_CREATION)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
            assertThat(it["adjudicationNumber"]).isEqualTo(ADJUDICATION_NUMBER.toString())
            assertThat(it["chargeSequence"]).isEqualTo(CHARGE_SEQ.toString())
            assertThat(it["punishmentsDeletedCount"]).isEqualTo("2")
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to delete the awards`() {
        nomisApi.verify(deleteRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/charge/$CHARGE_SEQ/awards")))
      }

      @Test
      fun `will create a mapping between the set of new punishments and awards and delete ones not referenced`() {
        await untilAsserted {
          mappingServer.verify(
            putRequestedFor(urlEqualTo("/mapping/punishments"))
              .withRequestBody(matchingJsonPath("punishmentsToDelete[0].nomisBookingId", equalTo("12345")))
              .withRequestBody(matchingJsonPath("punishmentsToDelete[0].nomisSanctionSequence", equalTo("9")))
              .withRequestBody(matchingJsonPath("punishmentsToDelete[1].nomisBookingId", equalTo("12345")))
              .withRequestBody(matchingJsonPath("punishmentsToDelete[1].nomisSanctionSequence", equalTo("8"))),
          )
        }
      }
    }

    @Nested
    inner class NoPunishmentsFoundInNomisToDelete {
      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_CREATION, ADJUDICATION_NUMBER)
        adjudicationsApiServer.stubChargeGet(
          CHARGE_NUMBER_FOR_CREATION,
          offenderNo = OFFENDER_NO,
        )

        mappingServer.stubGetPunishments(dpsPunishmentId = "634", nomisBookingId = 12345, nomisSanctionSequence = 10)
        mappingServer.stubGetPunishmentsWithError(dpsPunishmentId = "667", status = 404)

        nomisApi.stubAdjudicationAwardsDelete(
          ADJUDICATION_NUMBER,
          CHARGE_SEQ,
          deletedAwardIds = emptyList(),
        )
        mappingServer.stubUpdatePunishments()
        publishDeletePunishmentsDomainEvent()
        waitForProcessingToBeComplete()
      }

      @Test
      fun `will callback back to adjudication service to get more details`() {
        adjudicationsApiServer.verify(getRequestedFor(urlEqualTo("/reported-adjudications/$CHARGE_NUMBER_FOR_CREATION/v2")))
      }

      @Test
      fun `will create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("punishment-delete-success"),
          org.mockito.kotlin.check {
            assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_CREATION)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
            assertThat(it["adjudicationNumber"]).isEqualTo(ADJUDICATION_NUMBER.toString())
            assertThat(it["chargeSequence"]).isEqualTo(CHARGE_SEQ.toString())
            assertThat(it["punishmentsDeletedCount"]).isEqualTo("0")
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to delete the awards`() {
        nomisApi.verify(deleteRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/charge/$CHARGE_SEQ/awards")))
      }

      @Test
      fun `will not need to delete any punishment mappings`() {
        await untilAsserted {
          mappingServer.verify(
            0,
            putRequestedFor(urlEqualTo("/mapping/punishments")),
          )
        }
      }
    }

    @Nested
    inner class ErrorScenarios {
      @Nested
      inner class WhenAdjudicationMappingNotFound {

        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByChargeNumberWithError(CHARGE_NUMBER_FOR_CREATION, 404)
          publishDeletePunishmentsDomainEvent()
        }

        @Test
        fun `an error will lead to message being added to DLQ`() {
          await untilCallTo {
            awsSqsAdjudicationDlqClient!!.countAllMessagesOnQueue(adjudicationDlqUrl!!).get()
          } matches { it == 1 }
        }

        @Test
        fun `will track failure telemetry for each retry`() {
          await untilAsserted {
            verify(telemetryClient, Mockito.times(3)).trackEvent(
              Mockito.eq("punishment-delete-failed"),
              org.mockito.kotlin.check {
                assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_CREATION)
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
                assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenPunishmentsExistInDPS {

        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_CREATION, ADJUDICATION_NUMBER)
          adjudicationsApiServer.stubChargeGet(
            CHARGE_NUMBER_FOR_CREATION,
            offenderNo = OFFENDER_NO,
            // language=json
            punishments = """
          [
            {
                "id": 634,
                "type": "CONFINEMENT",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 3,
                    "startDate": "2023-10-04",
                    "endDate": "2023-10-06"
                }
            },
            {
                "id": 667,
                "type": "EXTRA_WORK",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 12,
                    "suspendedUntil": "2023-10-18"
                }
            }
        ]
            """.trimIndent(),
          )
          publishDeletePunishmentsDomainEvent()
        }

        @Test
        fun `an error will lead to message being added to DLQ`() {
          await untilCallTo {
            awsSqsAdjudicationDlqClient!!.countAllMessagesOnQueue(adjudicationDlqUrl!!).get()
          } matches { it == 1 }
        }

        @Test
        fun `will track failure telemetry for each retry`() {
          await untilAsserted {
            verify(telemetryClient, Mockito.times(3)).trackEvent(
              Mockito.eq("punishment-delete-failed"),
              org.mockito.kotlin.check {
                assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_CREATION)
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
                assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
              },
              isNull(),
            )
          }
          nomisApi.verify(
            0,
            deleteRequestedFor(anyUrl()),
          )
        }
      }

      @Nested
      inner class WhenMappingServiceFailsOnce {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_CREATION, ADJUDICATION_NUMBER)
          adjudicationsApiServer.stubChargeGet(
            CHARGE_NUMBER_FOR_CREATION,
            offenderNo = OFFENDER_NO,
          )
          nomisApi.stubAdjudicationAwardsDelete(
            ADJUDICATION_NUMBER,
            CHARGE_SEQ,
            deletedAwardIds = listOf(12345L to 9, 12345L to 8),
          )
          mappingServer.stubUpdatePunishmentsWithErrorFollowedBySuccess()
          publishDeletePunishmentsDomainEvent()
        }

        @Test
        fun `should only create the NOMIS punishments once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("punishment-delete-mapping-retry-success"),
              any(),
              isNull(),
            )
          }

          nomisApi.verify(
            1,
            deleteRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/charge/$CHARGE_SEQ/awards")),
          )
        }

        @Test
        fun `will eventually update mappings after NOMIS punishments are updated`() {
          await untilAsserted {
            mappingServer.verify(
              2,
              putRequestedFor(urlEqualTo("/mapping/punishments")),
            )
          }
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("punishment-delete-mapping-retry-success"),
              any(),
              isNull(),
            )
          }
        }
      }
    }
  }

  @Nested
  inner class QuashPunishments {
    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_CREATION, ADJUDICATION_NUMBER)
        adjudicationsApiServer.stubChargeGet(
          CHARGE_NUMBER_FOR_CREATION,
          offenderNo = OFFENDER_NO,
          // language=json
          punishments = """
          [
            {
                "id": 667,
                "type": "EXTRA_WORK",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 12,
                    "suspendedUntil": "2023-10-18",
                    "measurement": "DAYS"
                }
            }
          ]
          """.trimIndent(),
          // language=json
          outcomes = """
          [
            {
                "hearing": {
                    "id": 812,
                    "locationId": 27187,
                    "dateTimeOfHearing": "2023-10-26T15:30:00",
                    "oicHearingType": "INAD_ADULT",
                    "outcome": {
                        "id": 1144,
                        "adjudicator": "bobbie",
                        "code": "COMPLETE",
                        "plea": "GUILTY"
                    },
                    "agencyId": "MDI"
                },
                "outcome": {
                    "outcome": {
                        "id": 1516,
                        "code": "CHARGE_PROVED",
                        "canRemove": true
                    }
                }
            },
            {
                "outcome": {
                    "outcome": {
                        "id": 1556,
                        "code": "QUASHED",
                        "details": "Due to lack of evidence",
                        "quashedReason": "JUDICIAL_REVIEW",
                        "canRemove": true
                    }
                }
            }
          ] 
          """.trimIndent(),
        )
        nomisApi.stubAdjudicationSquashAwards(ADJUDICATION_NUMBER, CHARGE_SEQ)
        publishQuashPunishmentsDomainEvent()
        waitForQuashPunishmentProcessingToBeComplete()
      }

      @Test
      fun `will callback back to adjudication service to get more details`() {
        adjudicationsApiServer.verify(getRequestedFor(urlEqualTo("/reported-adjudications/$CHARGE_NUMBER_FOR_CREATION/v2")))
      }

      @Test
      fun `will create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("punishment-quash-success"),
          org.mockito.kotlin.check {
            assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_CREATION)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
            assertThat(it["adjudicationNumber"]).isEqualTo(ADJUDICATION_NUMBER.toString())
            assertThat(it["chargeSequence"]).isEqualTo(CHARGE_SEQ.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to quash the awards`() {
        nomisApi.verify(putRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/charge/$CHARGE_SEQ/quash")))
      }
    }

    @Nested
    inner class ErrorScenarios {

      @Nested
      inner class OutcomeNoLongerQuashed {

        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_CREATION, ADJUDICATION_NUMBER)
          adjudicationsApiServer.stubChargeGet(
            CHARGE_NUMBER_FOR_CREATION,
            offenderNo = OFFENDER_NO,
            // language=json
            punishments = """
          [
            {
                "id": 667,
                "type": "EXTRA_WORK",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 12,
                    "suspendedUntil": "2023-10-18",
                    "measurement": "DAYS"
                }
            }
          ]
            """.trimIndent(),
            // language=json
            outcomes = """
          [
            {
                "hearing": {
                    "id": 812,
                    "locationId": 27187,
                    "dateTimeOfHearing": "2023-10-26T15:30:00",
                    "oicHearingType": "INAD_ADULT",
                    "outcome": {
                        "id": 1144,
                        "adjudicator": "bobbie",
                        "code": "COMPLETE",
                        "plea": "GUILTY"
                    },
                    "agencyId": "MDI"
                },
                "outcome": {
                    "outcome": {
                        "id": 1516,
                        "code": "CHARGE_PROVED",
                        "canRemove": true
                    }
                }
            }
          ] 
            """.trimIndent(),
          )
          nomisApi.stubAdjudicationSquashAwards(ADJUDICATION_NUMBER, CHARGE_SEQ)
          publishQuashPunishmentsDomainEvent()
        }

        @Test
        fun `will record failure success telemetry and not update NOMIS`() {
          await untilAsserted {
            verify(telemetryClient, times(3)).trackEvent(
              eq("punishment-quash-failed"),
              org.mockito.kotlin.check {
                assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_CREATION)
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
                assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
                assertThat(it["adjudicationNumber"]).isEqualTo(ADJUDICATION_NUMBER.toString())
                assertThat(it["chargeSequence"]).isEqualTo(CHARGE_SEQ.toString())
                assertThat(it["reason"]).isEqualTo("Outcome is CHARGE_PROVED")
              },
              isNull(),
            )
          }
          nomisApi.verify(0, putRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/charge/$CHARGE_SEQ/quash")))
        }

        @Test
        fun `an error will lead to message being added to DLQ`() {
          await untilCallTo {
            awsSqsAdjudicationDlqClient!!.countAllMessagesOnQueue(adjudicationDlqUrl!!).get()
          } matches { it == 1 }
        }
      }
    }

    private fun waitForQuashPunishmentProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  @Nested
  inner class UnquashPunishments {
    @Nested
    inner class HappyPath {
      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_CREATION, ADJUDICATION_NUMBER)
        adjudicationsApiServer.stubChargeGet(
          CHARGE_NUMBER_FOR_CREATION,
          offenderNo = OFFENDER_NO,
          // language=json
          outcomes = """
          [
            {
                "hearing": {
                    "id": 812,
                    "locationId": 27187,
                    "dateTimeOfHearing": "2023-10-26T15:30:00",
                    "oicHearingType": "INAD_ADULT",
                    "outcome": {
                        "id": 1144,
                        "adjudicator": "bobbie",
                        "code": "COMPLETE",
                        "plea": "GUILTY"
                    },
                    "agencyId": "MDI"
                },
                "outcome": {
                    "outcome": {
                        "id": 1516,
                        "code": "CHARGE_PROVED",
                        "canRemove": true
                    }
                }
            }
          ] 
          """.trimIndent(),
          // language=json
          punishments = """
          [
            {
                "id": 634,
                "type": "CONFINEMENT",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 3,
                    "startDate": "2023-10-04",
                    "endDate": "2023-10-06",
                    "measurement": "DAYS"
                }
            },
            {
                "id": 667,
                "type": "EXTRA_WORK",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 12,
                    "suspendedUntil": "2023-10-18",
                    "measurement": "DAYS"
                }
            }
        ]
          """.trimIndent(),
        )

        mappingServer.stubGetPunishments(dpsPunishmentId = "634", nomisBookingId = 12345, nomisSanctionSequence = 10)
        mappingServer.stubGetPunishments(dpsPunishmentId = "667", nomisBookingId = 12345, nomisSanctionSequence = 11)

        nomisApi.stubAdjudicationUnquashAwards(ADJUDICATION_NUMBER, CHARGE_SEQ, createdAwardIds = listOf(), deletedAwardIds = listOf())
        publishUnQuashPunishmentsDomainEvent()
        waitForUnquashPunishmentProcessingToBeComplete()
      }

      @Test
      fun `will callback back to adjudication service to get more details`() {
        adjudicationsApiServer.verify(getRequestedFor(urlEqualTo("/reported-adjudications/$CHARGE_NUMBER_FOR_CREATION/v2")))
      }

      @Test
      fun `will create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("punishment-unquash-success"),
          org.mockito.kotlin.check {
            assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_CREATION)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
            assertThat(it["adjudicationNumber"]).isEqualTo(ADJUDICATION_NUMBER.toString())
            assertThat(it["chargeSequence"]).isEqualTo(CHARGE_SEQ.toString())
            assertThat(it["punishmentsCreatedCount"]).isEqualTo("0")
            assertThat(it["punishmentsUpdatedCount"]).isEqualTo("2")
            assertThat(it["punishmentsDeletedCount"]).isEqualTo("0")
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to update the awards`() {
        nomisApi.verify(putRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/charge/$CHARGE_SEQ/unquash")))
      }

      @Test
      fun `will map between DPS punishments to NOMIS awards dividing between new and existing awards`() {
        nomisApi.verify(
          putRequestedFor(anyUrl())
            .withRequestBody(matchingJsonPath("findingCode", equalTo("PROVED")))
            .withRequestBody(matchingJsonPath("awards.awardsToUpdate[0].sanctionSequence", equalTo("10")))
            .withRequestBody(matchingJsonPath("awards.awardsToUpdate[0].award.sanctionType", equalTo("CC")))
            .withRequestBody(matchingJsonPath("awards.awardsToUpdate[0].award.sanctionStatus", equalTo("IMMEDIATE")))
            .withRequestBody(matchingJsonPath("awards.awardsToUpdate[0].award.effectiveDate", equalTo("2023-10-04")))
            .withRequestBody(matchingJsonPath("awards.awardsToUpdate[0].award.sanctionDays", equalTo("3")))
            .withRequestBody(matchingJsonPath("awards.awardsToUpdate[1].sanctionSequence", equalTo("11")))
            .withRequestBody(matchingJsonPath("awards.awardsToUpdate[1].award.sanctionType", equalTo("EXTW")))
            .withRequestBody(matchingJsonPath("awards.awardsToUpdate[1].award.sanctionStatus", equalTo("SUSPENDED")))
            .withRequestBody(matchingJsonPath("awards.awardsToUpdate[1].award.effectiveDate", equalTo("2023-10-18")))
            .withRequestBody(matchingJsonPath("awards.awardsToUpdate[1].award.sanctionDays", equalTo("12"))),
        )
      }
    }

    @Nested
    inner class HappyPathWithPreviousSynchronisationIssue {
      @BeforeEach
      fun setUp() {
        mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_CREATION, ADJUDICATION_NUMBER)
        adjudicationsApiServer.stubChargeGet(
          CHARGE_NUMBER_FOR_CREATION,
          offenderNo = OFFENDER_NO,
          // language=json
          outcomes = """
          [
            {
                "hearing": {
                    "id": 812,
                    "locationId": 27187,
                    "dateTimeOfHearing": "2023-10-26T15:30:00",
                    "oicHearingType": "INAD_ADULT",
                    "outcome": {
                        "id": 1144,
                        "adjudicator": "bobbie",
                        "code": "COMPLETE",
                        "plea": "GUILTY"
                    },
                    "agencyId": "MDI"
                },
                "outcome": {
                    "outcome": {
                        "id": 1516,
                        "code": "CHARGE_PROVED",
                        "canRemove": true
                    }
                }
            }
          ] 
          """.trimIndent(),
          // language=json
          punishments = """
          [
            {
                "id": 634,
                "type": "CONFINEMENT",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 3,
                    "startDate": "2023-10-04",
                    "endDate": "2023-10-06",
                    "measurement": "DAYS"
                }
            },
            {
                "id": 667,
                "type": "EXTRA_WORK",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 12,
                    "suspendedUntil": "2023-10-18",
                    "measurement": "DAYS"
                }
            }
        ]
          """.trimIndent(),
        )

        mappingServer.stubGetPunishments(dpsPunishmentId = "634", nomisBookingId = 12345, nomisSanctionSequence = 10)
        mappingServer.stubGetPunishmentsWithError(dpsPunishmentId = "667", status = 404)

        nomisApi.stubAdjudicationUnquashAwards(
          ADJUDICATION_NUMBER,
          CHARGE_SEQ,
          createdAwardIds = listOf(12345L to 11),
          deletedAwardIds = listOf(12345L to 9, 12345L to 8),
        )
        mappingServer.stubUpdatePunishments()
        publishUnQuashPunishmentsDomainEvent()
        waitForUnquashPunishmentProcessingToBeComplete()
      }

      @Test
      fun `will callback back to adjudication service to get more details`() {
        adjudicationsApiServer.verify(getRequestedFor(urlEqualTo("/reported-adjudications/$CHARGE_NUMBER_FOR_CREATION/v2")))
      }

      @Test
      fun `will create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("punishment-unquash-success"),
          org.mockito.kotlin.check {
            assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_CREATION)
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
      fun `will call nomis api to unquash the awards`() {
        nomisApi.verify(putRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/charge/$CHARGE_SEQ/unquash")))
      }

      @Test
      fun `will map between DPS punishments to NOMIS awards dividing between new and existing awards`() {
        nomisApi.verify(
          putRequestedFor(anyUrl())
            .withRequestBody(matchingJsonPath("findingCode", equalTo("PROVED")))
            .withRequestBody(matchingJsonPath("awards.awardsToCreate[0].sanctionType", equalTo("EXTW")))
            .withRequestBody(matchingJsonPath("awards.awardsToCreate[0].sanctionStatus", equalTo("SUSPENDED")))
            .withRequestBody(matchingJsonPath("awards.awardsToCreate[0].effectiveDate", equalTo("2023-10-18")))
            .withRequestBody(matchingJsonPath("awards.awardsToCreate[0].sanctionDays", equalTo("12")))
            .withRequestBody(matchingJsonPath("awards.awardsToUpdate[0].sanctionSequence", equalTo("10")))
            .withRequestBody(matchingJsonPath("awards.awardsToUpdate[0].award.sanctionType", equalTo("CC")))
            .withRequestBody(matchingJsonPath("awards.awardsToUpdate[0].award.sanctionStatus", equalTo("IMMEDIATE")))
            .withRequestBody(matchingJsonPath("awards.awardsToUpdate[0].award.effectiveDate", equalTo("2023-10-04")))
            .withRequestBody(matchingJsonPath("awards.awardsToUpdate[0].award.sanctionDays", equalTo("3"))),
        )
      }

      @Test
      fun `will create a mapping between the set of new punishments and awards and delete ones not referenced`() {
        await untilAsserted {
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

    @Nested
    inner class ErrorScenarios {
      @Nested
      inner class WhenAdjudicationMappingNotFound {

        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByChargeNumberWithError(CHARGE_NUMBER_FOR_CREATION, 404)
          publishUnQuashPunishmentsDomainEvent()
        }

        @Test
        fun `an error will lead to message being added to DLQ`() {
          await untilCallTo {
            awsSqsAdjudicationDlqClient!!.countAllMessagesOnQueue(adjudicationDlqUrl!!).get()
          } matches { it == 1 }
        }

        @Test
        fun `will track failure telemetry for each retry`() {
          await untilAsserted {
            verify(telemetryClient, Mockito.times(3)).trackEvent(
              Mockito.eq("punishment-unquash-failed"),
              org.mockito.kotlin.check {
                assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER_FOR_CREATION)
                assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
                assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
              },
              isNull(),
            )
          }
        }
      }

      @Nested
      inner class WhenMappingServiceFailsOnceForAwardOutOfSync {
        @BeforeEach
        fun setUp() {
          mappingServer.stubGetByChargeNumber(CHARGE_NUMBER_FOR_CREATION, ADJUDICATION_NUMBER)
          adjudicationsApiServer.stubChargeGet(
            CHARGE_NUMBER_FOR_CREATION,
            offenderNo = OFFENDER_NO,
            // language=json
            outcomes = """
          [
            {
                "hearing": {
                    "id": 812,
                    "locationId": 27187,
                    "dateTimeOfHearing": "2023-10-26T15:30:00",
                    "oicHearingType": "INAD_ADULT",
                    "outcome": {
                        "id": 1144,
                        "adjudicator": "bobbie",
                        "code": "COMPLETE",
                        "plea": "GUILTY"
                    },
                    "agencyId": "MDI"
                },
                "outcome": {
                    "outcome": {
                        "id": 1516,
                        "code": "CHARGE_PROVED",
                        "canRemove": true
                    }
                }
            }
          ] 
            """.trimIndent(),
            // language=json
            punishments = """
          [
            {
                "id": 634,
                "type": "CONFINEMENT",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 3,
                    "startDate": "2023-10-04",
                    "endDate": "2023-10-06",
                    "measurement": "DAYS"
                }
            },
            {
                "id": 667,
                "type": "EXTRA_WORK",
                "rehabilitativeActivities": [],
                "schedule": {
                    "days": 12,
                    "suspendedUntil": "2023-10-18",
                    "measurement": "DAYS"
                }
            }
        ]
            """.trimIndent(),
          )

          mappingServer.stubGetPunishments(dpsPunishmentId = "634", nomisBookingId = 12345, nomisSanctionSequence = 10)
          mappingServer.stubGetPunishmentsWithError(dpsPunishmentId = "667", status = 404)

          nomisApi.stubAdjudicationUnquashAwards(
            ADJUDICATION_NUMBER,
            CHARGE_SEQ,
            createdAwardIds = listOf(12345L to 11),
            deletedAwardIds = listOf(12345L to 9, 12345L to 8),
          )
          mappingServer.stubUpdatePunishmentsWithErrorFollowedBySuccess()
          publishUnQuashPunishmentsDomainEvent()
        }

        @Test
        fun `should only unquash the NOMIS punishments once`() {
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("punishment-unquash-mapping-retry-success"),
              any(),
              isNull(),
            )
          }

          nomisApi.verify(
            1,
            putRequestedFor(urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/charge/$CHARGE_SEQ/unquash")),
          )
        }

        @Test
        fun `will eventually update mappings after NOMIS punishments are updated`() {
          await untilAsserted {
            mappingServer.verify(
              2,
              putRequestedFor(urlEqualTo("/mapping/punishments")),
            )
          }
          await untilAsserted {
            verify(telemetryClient).trackEvent(
              eq("punishment-unquash-mapping-retry-success"),
              any(),
              isNull(),
            )
          }
        }
      }
    }

    private fun waitForUnquashPunishmentProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  private fun waitForProcessingToBeComplete() {
    await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
  }

  private fun publishCreatePunishmentsDomainEvent() {
    val eventType = "adjudication.punishments.created"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(adjudicationPunishmentMessagePayload(CHARGE_NUMBER_FOR_CREATION, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishUpdatePunishmentsDomainEvent() {
    val eventType = "adjudication.punishments.updated"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(adjudicationPunishmentMessagePayload(CHARGE_NUMBER_FOR_CREATION, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishDeletePunishmentsDomainEvent() {
    val eventType = "adjudication.punishments.deleted"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(adjudicationPunishmentMessagePayload(CHARGE_NUMBER_FOR_CREATION, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishQuashPunishmentsDomainEvent() {
    val eventType = "adjudication.outcome.quashed"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          adjudicationPunishmentMessagePayload(
            CHARGE_NUMBER_FOR_CREATION,
            PRISON_ID,
            OFFENDER_NO,
            eventType,
            status = "QUASHED",
          ),
        )
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishUnQuashPunishmentsDomainEvent() {
    val eventType = "adjudication.outcome.unquashed"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          adjudicationPunishmentMessagePayload(
            CHARGE_NUMBER_FOR_CREATION,
            PRISON_ID,
            OFFENDER_NO,
            eventType,
            status = "CHARGE_PROVED",
          ),
        )
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  fun adjudicationPunishmentMessagePayload(
    chargeNumber: String,
    prisonId: String,
    prisonerNumber: String,
    eventType: String,
    status: String = "CHARGE_PROVED",
  ) =
    // language=json
    """{"eventType":"$eventType", "additionalInformation": {"chargeNumber":"$chargeNumber", "prisonId": "$prisonId", "prisonerNumber": "$prisonerNumber", "status": "$status"}}"""
}
