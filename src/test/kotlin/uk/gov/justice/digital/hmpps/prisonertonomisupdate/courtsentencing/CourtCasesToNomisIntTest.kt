package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions
import org.awaitility.kotlin.await
import org.awaitility.kotlin.matches
import org.awaitility.kotlin.untilAsserted
import org.awaitility.kotlin.untilCallTo
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CaseReferenceLegacyData
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseAllMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.SentenceMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.SentenceTermMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateSentenceTermResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.COURT_CHARGE_1_OFFENCE_CODE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.COURT_CHARGE_1_OFFENCE_DATE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.COURT_CHARGE_1_OFFENCE_END_DATE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.COURT_CHARGE_1_RESULT_CODE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.CourtSentencingApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private const val COURT_CASE_ID_FOR_CREATION = "12345"
private const val NOMIS_COURT_CASE_ID_FOR_CREATION = 7L
private const val DPS_COURT_APPEARANCE_ID = "9c591b18-642a-484a-a967-2d17b5c9c5a1"
private const val NOMIS_COURT_APPEARANCE_ID = 3L
private const val NOMIS_COURT_CHARGE_ID = 11L
private const val NOMIS_COURT_CHARGE_2_ID = 12L
private const val NOMIS_COURT_CHARGE_3_ID = 13L
private const val NOMIS_COURT_CHARGE_4_ID = 14L
private const val NOMIS_COURT_CHARGE_5_ID = 15L
private const val NOMIS_COURT_CHARGE_6_ID = 16L
private const val DPS_COURT_CHARGE_ID = "8576aa44-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_COURT_CHARGE_2_ID = "9996aa44-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_COURT_CHARGE_3_ID = "4566aa44-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_COURT_CHARGE_4_ID = "1236aa44-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_SENTENCE_ID = "4c111b18-642a-484a-a967-2d17b5c9c5d5"
private const val DPS_SENTENCE_ID_2 = "1e333b18-642a-484a-a967-2d17b5c9c5d5"
private const val DPS_TERM_ID = "9c888b18-642a-484a-a967-2d17b5c9c5d5"
private const val FINE_AMOUNT = "1000.0"
private const val NOMIS_BOOKING_ID = 66L
private const val NOMIS_SENTENCE_SEQ = 54L
private const val NOMIS_SENTENCE_SEQ_2 = 64L
private const val NOMIS_TERM_SEQ = 32L
private const val OFFENDER_NO = "AB12345"
private const val DONCASTER_COURT_CODE = "DRBYYC"
private const val CASE_REFERENCE = "ABC4999"

class CourtCasesToNomisIntTest : SqsIntegrationTestBase() {

  @Nested
  inner class CreateCourtCase {
    @Nested
    inner class WhenCourtCaseHasBeenCreatedInDPS {
      @BeforeEach
      fun setUp() {
        CourtSentencingApiExtension.courtSentencingApi.stubCourtCaseGet(
          COURT_CASE_ID_FOR_CREATION,
          legacyCourtCaseResponse(),
        )
        NomisApiExtension.nomisApi.stubCourtCaseCreate(
          OFFENDER_NO,
          CreateCourtCaseResponse(
            id = NOMIS_COURT_CASE_ID_FOR_CREATION,
            courtAppearanceIds = emptyList(),
          ),
        )
        MappingExtension.mappingServer.stubGetCaseMappingGivenDpsIdWithError(COURT_CASE_ID_FOR_CREATION, 404)
        MappingExtension.mappingServer.stubCreateCourtCase()
        publishCreateCourtCaseDomainEvent()
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete()
        CourtSentencingApiExtension.courtSentencingApi.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/legacy/court-case/${COURT_CASE_ID_FOR_CREATION}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("court-case-create-success"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            Assertions.assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["mappingType"]).isEqualTo(CourtCaseAllMappingDto.MappingType.DPS_CREATED.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the Court Case`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(
          WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases"))
            .withRequestBody(
              WireMock.matchingJsonPath(
                "status",
                WireMock.equalTo("A"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "legalCaseType",
                WireMock.equalTo("NE"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "startDate",
                WireMock.equalTo("2024-01-01"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "caseReference",
                WireMock.equalTo(CASE_REFERENCE),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtId",
                WireMock.equalTo(DONCASTER_COURT_CODE),
              ),
            ),

        )
      }

      @Test
      fun `will create a mapping between the two court cases`() {
        waitForAnyProcessingToComplete()

        await untilAsserted {
          MappingExtension.mappingServer.verify(
            WireMock.postRequestedFor(WireMock.urlEqualTo("/mapping/court-sentencing/court-cases"))
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "dpsCourtCaseId",
                  WireMock.equalTo(COURT_CASE_ID_FOR_CREATION),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "nomisCourtCaseId",
                  WireMock.equalTo(
                    NOMIS_COURT_CASE_ID_FOR_CREATION.toString(),
                  ),
                ),
              ),

          )
        }
        await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
      }
    }

    @Nested
    inner class WhenCourtCaseHasBeenCreatedInNOMIS {
      @BeforeEach
      fun setUp() {
        CourtSentencingApiExtension.courtSentencingApi.stubCourtCaseGet(
          COURT_CASE_ID_FOR_CREATION,
          legacyCourtCaseResponse(),
        )
        publishCreateCourtCaseDomainEvent(source = "NOMIS")
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("court-case-create-ignored"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["reason"]).isEqualTo("Court case created in NOMIS")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingAlreadyCreatedForCourtCase {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          COURT_CASE_ID_FOR_CREATION,
          NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        publishCreateCourtCaseDomainEvent()
      }

      @Test
      fun `will not create an court case in NOMIS`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("court-case-create-duplicate"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          },
          isNull(),
        )

        NomisApiExtension.nomisApi.verify(
          0,
          WireMock.postRequestedFor(WireMock.anyUrl()),
        )
      }
    }

    @Nested
    inner class WhenMappingServiceFailsOnce {
      @BeforeEach
      fun setUp() {
        CourtSentencingApiExtension.courtSentencingApi.stubCourtCaseGet(
          COURT_CASE_ID_FOR_CREATION,
          legacyCourtCaseResponse(),
        )
        NomisApiExtension.nomisApi.stubCourtCaseCreate(
          OFFENDER_NO,
          CreateCourtCaseResponse(
            id = NOMIS_COURT_CASE_ID_FOR_CREATION,
            courtAppearanceIds = emptyList(),
          ),
        )
        MappingExtension.mappingServer.stubGetCaseMappingGivenDpsIdWithError(COURT_CASE_ID_FOR_CREATION, 404)
        MappingExtension.mappingServer.stubCreateCourtCaseWithErrorFollowedBySlowSuccess()
        publishCreateCourtCaseDomainEvent()

        await untilCallTo { CourtSentencingApiExtension.courtSentencingApi.getCountFor("/legacy/court-case/$COURT_CASE_ID_FOR_CREATION") } matches { it == 1 }
        await untilCallTo { NomisApiExtension.nomisApi.postCountFor("/prisoners/$OFFENDER_NO/sentencing/court-cases") } matches { it == 1 }
      }

      @Test
      fun `should only create the NOMIS court case once`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("court-case-create-mapping-retry-success"),
            any(),
            isNull(),
          )
        }
        NomisApiExtension.nomisApi.verify(
          1,
          WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases")),
        )
      }

      @Test
      fun `will eventually create a mapping after NOMIS court case is created`() {
        await untilAsserted {
          MappingExtension.mappingServer.verify(
            2,
            WireMock.postRequestedFor(WireMock.urlEqualTo("/mapping/court-sentencing/court-cases"))
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "dpsCourtCaseId",
                  WireMock.equalTo(COURT_CASE_ID_FOR_CREATION),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "nomisCourtCaseId",
                  WireMock.equalTo(
                    NOMIS_COURT_CASE_ID_FOR_CREATION.toString(),
                  ),
                ),
              ),
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("court-case-create-mapping-retry-success"),
            any(),
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  inner class DeleteCourtCase {
    @Nested
    inner class WhenCourtCaseHasBeenDeletedInDPS {
      @BeforeEach
      fun setUp() {
        NomisApiExtension.nomisApi.stubCourtCaseDelete(
          offenderNo = OFFENDER_NO,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,

        )
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        MappingExtension.mappingServer.stubDeleteCourtCase(id = COURT_CASE_ID_FOR_CREATION)
        publishDeleteCourtCaseDomainEvent()
      }

      @Test
      fun `will create success telemetry`() {
        waitForHearingProcessingToBeComplete()

        verify(telemetryClient).trackEvent(
          org.mockito.kotlin.eq("court-case-deleted-success"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            Assertions.assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to delete the court case`() {
        waitForHearingProcessingToBeComplete()
        NomisApiExtension.nomisApi.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/$NOMIS_COURT_CASE_ID_FOR_CREATION")))
      }

      @Test
      fun `will call the mapping service to delete the mapping`() {
        waitForHearingProcessingToBeComplete()
        MappingExtension.mappingServer.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/mapping/court-sentencing/court-cases/dps-court-case-id/$COURT_CASE_ID_FOR_CREATION")))
      }
    }

    @Nested
    inner class WhenNoMappingExistsForCase {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetCaseMappingGivenDpsIdWithError(COURT_CASE_ID_FOR_CREATION, 404)
        publishDeleteCourtCaseDomainEvent()
      }

      @Test
      fun `will not attempt to delete a court case in NOMIS`() {
        await untilAsserted {
          verify(telemetryClient, times(1)).trackEvent(
            org.mockito.kotlin.eq("court-case-deleted-skipped"),
            org.mockito.kotlin.check {
              Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
              Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            },
            isNull(),
          )
        }

        NomisApiExtension.nomisApi.verify(
          0,
          WireMock.deleteRequestedFor(WireMock.anyUrl()),
        )
      }
    }

    private fun waitForHearingProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  @Nested
  inner class CreateCourtAppearance {

    @Nested
    inner class WhenCourtAppearanceHasBeenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        publishCreateCourtAppearanceDomainEvent(source = "NOMIS")
      }

      @Test
      fun `will create ignore telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("court-appearance-create-ignored"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCourtAppearanceHasBeenCreatedInDPS {
      @BeforeEach
      fun setUp() {
        CourtSentencingApiExtension.courtSentencingApi.stubCourtAppearanceGetWithFourCharges(
          COURT_CASE_ID_FOR_CREATION,
          offenderNo = OFFENDER_NO,
          courtAppearanceId = DPS_COURT_APPEARANCE_ID,
          courtCharge1Id = DPS_COURT_CHARGE_ID,
          courtCharge2Id = DPS_COURT_CHARGE_2_ID,
          courtCharge3Id = DPS_COURT_CHARGE_3_ID,
          courtCharge4Id = DPS_COURT_CHARGE_4_ID,
        )
        NomisApiExtension.nomisApi.stubCourtAppearanceCreate(
          OFFENDER_NO,
          NOMIS_COURT_CASE_ID_FOR_CREATION,
          nomisCourtAppearanceCreateResponse(),
        )
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )

        MappingExtension.mappingServer.stubGetCourtAppearanceMappingGivenDpsIdWithError(DPS_COURT_APPEARANCE_ID, 404)
        MappingExtension.mappingServer.stubCreateCourtAppearance()
        // stub two mappings for charges out of the 4 - which makes 2 creates and 2 updates
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsId(DPS_COURT_CHARGE_ID, NOMIS_COURT_CHARGE_ID)
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsId(
          DPS_COURT_CHARGE_2_ID,
          NOMIS_COURT_CHARGE_2_ID,
        )
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsId(
          DPS_COURT_CHARGE_3_ID,
          NOMIS_COURT_CHARGE_3_ID,
        )
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsId(
          DPS_COURT_CHARGE_4_ID,
          NOMIS_COURT_CHARGE_4_ID,
        )

        publishCreateCourtAppearanceDomainEvent()
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete()
        CourtSentencingApiExtension.courtSentencingApi.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/legacy/court-appearance/${DPS_COURT_APPEARANCE_ID}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("court-appearance-create-success"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            Assertions.assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["mappingType"]).isEqualTo(CourtAppearanceMappingDto.MappingType.DPS_CREATED.toString())
            Assertions.assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            Assertions.assertThat(it["courtEventCharges"])
              .isEqualTo("[$NOMIS_COURT_CHARGE_ID, $NOMIS_COURT_CHARGE_2_ID, $NOMIS_COURT_CHARGE_3_ID, $NOMIS_COURT_CHARGE_4_ID]")
            Assertions.assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the Court Appearance`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(
          WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/court-appearances"))
            .withRequestBody(
              WireMock.matchingJsonPath(
                "eventDateTime",
                WireMock.equalTo("2024-09-23T10:00:00"),
              ),
            )
            .withRequestBody(WireMock.matchingJsonPath("courtEventCharges.size()", WireMock.equalTo("4")))
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventCharges[0]",
                WireMock.equalTo(NOMIS_COURT_CHARGE_ID.toString()),
              ),

            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventCharges[1]",
                WireMock.equalTo(NOMIS_COURT_CHARGE_2_ID.toString()),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventCharges[2]",
                WireMock.equalTo(NOMIS_COURT_CHARGE_3_ID.toString()),
              ),

            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventCharges[3]",
                WireMock.equalTo(NOMIS_COURT_CHARGE_4_ID.toString()),
              ),
            ),
        )
      }

      @Test
      fun `will create a mapping between the two court appearances`() {
        waitForAnyProcessingToComplete()

        await untilAsserted {
          MappingExtension.mappingServer.verify(
            WireMock.postRequestedFor(WireMock.urlEqualTo("/mapping/court-sentencing/court-appearances"))
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "dpsCourtAppearanceId",
                  WireMock.equalTo(DPS_COURT_APPEARANCE_ID),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "nomisCourtAppearanceId",
                  WireMock.equalTo(
                    NOMIS_COURT_APPEARANCE_ID.toString(),
                  ),
                ),
              ),
          )
        }
        await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
      }
    }

    @Nested
    inner class WhenMappingAlreadyCreatedForCourtAppearance {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(COURT_CASE_ID_FOR_CREATION)
        MappingExtension.mappingServer.stubGetCourtAppearanceMappingGivenDpsId(
          DPS_COURT_APPEARANCE_ID,
          NOMIS_COURT_APPEARANCE_ID,
        )
        publishCreateCourtAppearanceDomainEvent()
      }

      @Test
      fun `will not create an court case in NOMIS`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("court-appearance-create-duplicate"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          },
          isNull(),
        )

        NomisApiExtension.nomisApi.verify(
          0,
          WireMock.postRequestedFor(WireMock.anyUrl()),
        )
      }
    }

    @Nested
    inner class WhenMappingServiceFailsOnce {
      @BeforeEach
      fun setUp() {
        CourtSentencingApiExtension.courtSentencingApi.stubCourtAppearanceGetWithFourCharges(
          COURT_CASE_ID_FOR_CREATION,
          offenderNo = OFFENDER_NO,
          courtAppearanceId = DPS_COURT_APPEARANCE_ID,
          courtCharge1Id = DPS_COURT_CHARGE_ID,
          courtCharge2Id = DPS_COURT_CHARGE_2_ID,
          courtCharge3Id = DPS_COURT_CHARGE_3_ID,
          courtCharge4Id = DPS_COURT_CHARGE_4_ID,
        )
        NomisApiExtension.nomisApi.stubCourtAppearanceCreate(
          OFFENDER_NO,
          NOMIS_COURT_CASE_ID_FOR_CREATION,
          nomisCourtAppearanceCreateResponse(),
        )
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        MappingExtension.mappingServer.stubGetCourtAppearanceMappingGivenDpsIdWithError(DPS_COURT_APPEARANCE_ID, 404)
        MappingExtension.mappingServer.stubCreateCourtAppearanceWithErrorFollowedBySlowSuccess()

        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsId(DPS_COURT_CHARGE_ID, NOMIS_COURT_CHARGE_ID)
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsId(
          DPS_COURT_CHARGE_2_ID,
          NOMIS_COURT_CHARGE_2_ID,
        )
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsId(
          DPS_COURT_CHARGE_3_ID,
          NOMIS_COURT_CHARGE_3_ID,
        )
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsId(
          DPS_COURT_CHARGE_4_ID,
          NOMIS_COURT_CHARGE_4_ID,
        )
        publishCreateCourtAppearanceDomainEvent()

        await untilCallTo { CourtSentencingApiExtension.courtSentencingApi.getCountFor("/legacy/court-appearance/$DPS_COURT_APPEARANCE_ID") } matches { it == 1 }
        await untilCallTo { NomisApiExtension.nomisApi.postCountFor("/prisoners/$OFFENDER_NO/sentencing/court-cases/$NOMIS_COURT_CASE_ID_FOR_CREATION/court-appearances") } matches { it == 1 }
      }

      @Test
      fun `should only create the NOMIS court appearance once`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("court-appearance-create-mapping-retry-success"),
            any(),
            isNull(),
          )
        }
        NomisApiExtension.nomisApi.verify(
          1,
          WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/court-appearances")),
        )
      }

      @Test
      fun `will eventually create a mapping after NOMIS court appearance is created`() {
        await untilAsserted {
          MappingExtension.mappingServer.verify(
            2,
            WireMock.postRequestedFor(WireMock.urlEqualTo("/mapping/court-sentencing/court-appearances"))
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "dpsCourtAppearanceId",
                  WireMock.equalTo(DPS_COURT_APPEARANCE_ID),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "nomisCourtAppearanceId",
                  WireMock.equalTo(
                    NOMIS_COURT_APPEARANCE_ID.toString(),
                  ),
                ),
              ),
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("court-appearance-create-mapping-retry-success"),
            any(),
            isNull(),
          )
        }
      }
    }

    @Nested
    inner class WhenMappingServiceFailsDueToMissingChargeEntity {
      @BeforeEach
      fun setUp() {
        CourtSentencingApiExtension.courtSentencingApi.stubCourtAppearanceGetWitOneCharge(
          COURT_CASE_ID_FOR_CREATION,
          offenderNo = OFFENDER_NO,
          courtAppearanceId = DPS_COURT_APPEARANCE_ID,
          courtCharge1Id = DPS_COURT_CHARGE_ID,
        )
        NomisApiExtension.nomisApi.stubCourtAppearanceCreate(
          OFFENDER_NO,
          NOMIS_COURT_CASE_ID_FOR_CREATION,
          nomisCourtAppearanceCreateResponse(),
        )
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        MappingExtension.mappingServer.stubGetCourtAppearanceMappingGivenDpsIdWithError(DPS_COURT_APPEARANCE_ID, 404)
        MappingExtension.mappingServer.stubCreateCourtAppearance()

        // a parent entity has initially not been created but then is available on retry
        MappingExtension.mappingServer.stubGetCourtChargeNotFoundFollowedBySlowSuccess(
          DPS_COURT_CHARGE_ID,
          NOMIS_COURT_CHARGE_ID,
        )
        publishCreateCourtAppearanceDomainEvent()

        await untilCallTo { CourtSentencingApiExtension.courtSentencingApi.getCountFor("/legacy/court-appearance/$DPS_COURT_APPEARANCE_ID") } matches { it == 2 }
        await untilCallTo { NomisApiExtension.nomisApi.postCountFor("/prisoners/$OFFENDER_NO/sentencing/court-cases/$NOMIS_COURT_CASE_ID_FOR_CREATION/court-appearances") } matches { it == 1 }
      }

      @Test
      fun `should only create the NOMIS court appearance once`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("court-appearance-create-success"),
            any(),
            isNull(),
          )
        }
        NomisApiExtension.nomisApi.verify(
          1,
          WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/court-appearances")),
        )
      }
    }
  }

  @Nested
  inner class UpdateCourtAppearance {

    @Nested
    inner class WhenCourtAppearanceHasBeenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        publishUpdateCourtAppearanceDomainEvent(source = "NOMIS")
      }

      @Test
      fun `will create ignore telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("court-appearance-updated-ignored"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCourtAppearanceHasBeenUpdatedInDPS {
      @BeforeEach
      fun setUp() {
        CourtSentencingApiExtension.courtSentencingApi.stubCourtAppearanceGetWithFourCharges(
          COURT_CASE_ID_FOR_CREATION,
          offenderNo = OFFENDER_NO,
          courtAppearanceId = DPS_COURT_APPEARANCE_ID,
          courtCharge1Id = DPS_COURT_CHARGE_ID,
          courtCharge2Id = DPS_COURT_CHARGE_2_ID,
          courtCharge3Id = DPS_COURT_CHARGE_3_ID,
          courtCharge4Id = DPS_COURT_CHARGE_4_ID,
        )
        NomisApiExtension.nomisApi.stubCourtAppearanceUpdate(
          OFFENDER_NO,
          NOMIS_COURT_CASE_ID_FOR_CREATION,
          NOMIS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceUpdateResponseWithTwoDeletedCharges(),
        )
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )

        MappingExtension.mappingServer.stubGetCourtAppearanceMappingGivenDpsId(
          DPS_COURT_APPEARANCE_ID,
          NOMIS_COURT_APPEARANCE_ID,
        )
        MappingExtension.mappingServer.stubCreateCourtAppearance()
        // mappings found for all 4 charges
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsId(DPS_COURT_CHARGE_ID, NOMIS_COURT_CHARGE_ID)
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsId(
          DPS_COURT_CHARGE_2_ID,
          NOMIS_COURT_CHARGE_2_ID,
        )
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsId(
          DPS_COURT_CHARGE_3_ID,
          NOMIS_COURT_CHARGE_3_ID,
        )
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsId(
          DPS_COURT_CHARGE_4_ID,
          NOMIS_COURT_CHARGE_4_ID,
        )
        MappingExtension.mappingServer.stubCourtChargeBatchUpdate()
        publishUpdateCourtAppearanceDomainEvent()
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete()
        CourtSentencingApiExtension.courtSentencingApi.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/legacy/court-appearance/${DPS_COURT_APPEARANCE_ID}")))
      }

      @Test
      fun `will map new DPS court charges to NOMIS court charges`() {
        waitForAnyProcessingToComplete()

        NomisApiExtension.nomisApi.verify(
          WireMock.putRequestedFor(WireMock.anyUrl())
            .withRequestBody(WireMock.matchingJsonPath("courtEventCharges.size()", WireMock.equalTo("4")))
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventCharges[0]",
                WireMock.equalTo(NOMIS_COURT_CHARGE_ID.toString()),
              ),

            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventCharges[1]",
                WireMock.equalTo(NOMIS_COURT_CHARGE_2_ID.toString()),
              ),
            ),
        )
      }

      @Test
      fun `will delete charge mappings as required`() {
        waitForAnyProcessingToComplete()
        await untilAsserted {
          MappingExtension.mappingServer.verify(
            WireMock.putRequestedFor(WireMock.urlEqualTo("/mapping/court-sentencing/court-charges"))
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "courtChargesToDelete[0].nomisCourtChargeId",
                  WireMock.equalTo(
                    NOMIS_COURT_CHARGE_5_ID.toString(),
                  ),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "courtChargesToDelete[1].nomisCourtChargeId",
                  WireMock.equalTo(
                    NOMIS_COURT_CHARGE_6_ID.toString(),
                  ),
                ),
              ),
          )
        }
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("court-appearance-updated-success"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            Assertions.assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            Assertions.assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
            Assertions.assertThat(it["deletedCourtChargeMappings"])
              .contains("[nomisCourtChargeId: 15, nomisCourtChargeId: 16]")
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to update the Court Appearance`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(WireMock.putRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/court-appearances/${NOMIS_COURT_APPEARANCE_ID}")))
      }
    }

    @Nested
    inner class WhenMappingDoesntExistForCourtAppearance {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(COURT_CASE_ID_FOR_CREATION)
        MappingExtension.mappingServer.stubGetCourtAppearanceMappingGivenDpsIdWithError(
          DPS_COURT_APPEARANCE_ID,
          404,
        )
        publishUpdateCourtAppearanceDomainEvent()
      }

      @Test
      fun `will not update an court appearance in NOMIS`() {
        await untilAsserted {
          verify(telemetryClient, times(3)).trackEvent(
            eq("court-appearance-updated-failed"),
            org.mockito.kotlin.check {
              Assertions.assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
              Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            },
            isNull(),
          )
        }

        NomisApiExtension.nomisApi.verify(
          0,
          WireMock.putRequestedFor(WireMock.anyUrl()),
        )
      }
    }
  }

  @Nested
  inner class DeleteCourtAppearance {
    @Nested
    inner class WhenCourtAppearanceHasBeenDeletedInDPS {
      @BeforeEach
      fun setUp() {
        NomisApiExtension.nomisApi.stubCourtAppearanceDelete(
          offenderNo = OFFENDER_NO,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          nomisEventId = NOMIS_COURT_APPEARANCE_ID,
        )
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        MappingExtension.mappingServer.stubGetCourtAppearanceMappingGivenDpsId(
          id = DPS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )
        MappingExtension.mappingServer.stubDeleteCourtAppearance(id = DPS_COURT_APPEARANCE_ID)
        publishDeleteCourtAppearanceDomainEvent()
      }

      @Test
      fun `will create success telemetry`() {
        waitForHearingProcessingToBeComplete()

        verify(telemetryClient).trackEvent(
          org.mockito.kotlin.eq("court-appearance-deleted-success"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            Assertions.assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to delete the court appearance`() {
        waitForHearingProcessingToBeComplete()
        NomisApiExtension.nomisApi.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/$NOMIS_COURT_CASE_ID_FOR_CREATION/court-appearances/$NOMIS_COURT_APPEARANCE_ID")))
      }

      @Test
      fun `will call the mapping service to delete the mapping`() {
        waitForHearingProcessingToBeComplete()
        MappingExtension.mappingServer.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/mapping/court-sentencing/court-appearances/dps-court-appearance-id/$DPS_COURT_APPEARANCE_ID")))
      }
    }

    @Nested
    inner class WhenNoMappingExistsForAppearance {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetCourtAppearanceMappingGivenDpsIdWithError(DPS_COURT_APPEARANCE_ID, 404)
        publishDeleteCourtAppearanceDomainEvent()
      }

      @Test
      fun `will not attempt to delete a court appearance in NOMIS`() {
        await untilAsserted {
          verify(telemetryClient, times(1)).trackEvent(
            org.mockito.kotlin.eq("court-appearance-deleted-skipped"),
            org.mockito.kotlin.check {
              Assertions.assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
              Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            },
            isNull(),
          )
        }

        NomisApiExtension.nomisApi.verify(
          0,
          WireMock.deleteRequestedFor(WireMock.anyUrl()),
        )
      }
    }

    private fun waitForHearingProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  @Nested
  inner class CreateCharge {

    @Nested
    inner class WhenCourtChargeHasBeenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        publishCreateCourtChargeDomainEvent(source = "NOMIS")
      }

      @Test
      fun `will create ignore telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("charge-create-ignored"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["dpsChargeId"]).isEqualTo(DPS_COURT_CHARGE_ID)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCourtChargeHasBeenCreatedInDPS {
      @BeforeEach
      fun setUp() {
        CourtSentencingApiExtension.courtSentencingApi.stubGetCourtCharge(
          DPS_COURT_CHARGE_ID,
          offenderNo = OFFENDER_NO,
          caseID = COURT_CASE_ID_FOR_CREATION,
        )
        NomisApiExtension.nomisApi.stubCourtChargeCreate(
          OFFENDER_NO,
          NOMIS_COURT_CASE_ID_FOR_CREATION,
          nomisCourtChargeCreateResponse(),
        )
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )

        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsIdWithError(DPS_COURT_CHARGE_ID, 404)
        MappingExtension.mappingServer.stubCreateCourtCharge()

        publishCreateCourtChargeDomainEvent()
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete()
        CourtSentencingApiExtension.courtSentencingApi.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/legacy/charge/${DPS_COURT_CHARGE_ID}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("charge-create-success"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            Assertions.assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["mappingType"]).isEqualTo(CourtChargeMappingDto.MappingType.DPS_CREATED.toString())
            Assertions.assertThat(it["dpsChargeId"]).isEqualTo(DPS_COURT_CHARGE_ID)
            Assertions.assertThat(it["nomisChargeId"]).isEqualTo(NOMIS_COURT_CHARGE_ID.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the Charge`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/charges")))
      }

      @Test
      fun `will create a mapping between the two charges`() {
        waitForAnyProcessingToComplete()

        await untilAsserted {
          MappingExtension.mappingServer.verify(
            WireMock.postRequestedFor(WireMock.urlEqualTo("/mapping/court-sentencing/court-charges"))
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "dpsCourtChargeId",
                  WireMock.equalTo(DPS_COURT_CHARGE_ID),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "nomisCourtChargeId",
                  WireMock.equalTo(
                    NOMIS_COURT_CHARGE_ID.toString(),
                  ),
                ),
              ),
          )
        }
        await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
      }
    }

    @Nested
    inner class WhenMappingAlreadyCreatedForCourtCharge {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(COURT_CASE_ID_FOR_CREATION)
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsId(
          DPS_COURT_CHARGE_ID,
          NOMIS_COURT_CHARGE_ID,
        )
        publishCreateCourtChargeDomainEvent()
      }

      @Test
      fun `will not create an court case in NOMIS`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("charge-create-duplicate"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsChargeId"]).isEqualTo(DPS_COURT_CHARGE_ID)
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          },
          isNull(),
        )

        NomisApiExtension.nomisApi.verify(
          0,
          WireMock.postRequestedFor(WireMock.anyUrl()),
        )
      }
    }

    @Nested
    inner class WhenMappingServiceFailsOnce {
      @BeforeEach
      fun setUp() {
        CourtSentencingApiExtension.courtSentencingApi.stubGetCourtCharge(
          DPS_COURT_CHARGE_ID,
          offenderNo = OFFENDER_NO,
          caseID = COURT_CASE_ID_FOR_CREATION,
        )
        NomisApiExtension.nomisApi.stubCourtChargeCreate(
          OFFENDER_NO,
          NOMIS_COURT_CASE_ID_FOR_CREATION,
          nomisCourtChargeCreateResponse(),
        )
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsIdWithError(DPS_COURT_CHARGE_ID, 404)
        MappingExtension.mappingServer.stubCreateCourtChargeWithErrorFollowedBySlowSuccess()
        publishCreateCourtChargeDomainEvent()

        await untilCallTo { CourtSentencingApiExtension.courtSentencingApi.getCountFor("/legacy/charge/$DPS_COURT_CHARGE_ID") } matches { it == 1 }
        await untilCallTo { NomisApiExtension.nomisApi.postCountFor("/prisoners/$OFFENDER_NO/sentencing/court-cases/$NOMIS_COURT_CASE_ID_FOR_CREATION/charges") } matches { it == 1 }
      }

      @Test
      fun `should only create the NOMIS court charge once`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("charge-create-mapping-retry-success"),
            any(),
            isNull(),
          )
        }
        NomisApiExtension.nomisApi.verify(
          1,
          WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/charges")),
        )
      }

      @Test
      fun `will eventually create a mapping after NOMIS court charge is created`() {
        await untilAsserted {
          MappingExtension.mappingServer.verify(
            2,
            WireMock.postRequestedFor(WireMock.urlEqualTo("/mapping/court-sentencing/court-charges"))
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "dpsCourtChargeId",
                  WireMock.equalTo(DPS_COURT_CHARGE_ID),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "nomisCourtChargeId",
                  WireMock.equalTo(
                    NOMIS_COURT_CHARGE_ID.toString(),
                  ),
                ),
              ),
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("charge-create-mapping-retry-success"),
            any(),
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  inner class UpdateCharge {
    @Nested
    inner class WhenCourtChargeHasBeenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        publishUpdatedCourtChargeDomainEvent(source = "NOMIS")
      }

      @Test
      fun `will create ignore telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("charge-updated-ignored"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            Assertions.assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["dpsChargeId"]).isEqualTo(DPS_COURT_CHARGE_ID)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenCourtChargeHasBeenUpdatedInDPS {
      @BeforeEach
      fun setUp() {
        CourtSentencingApiExtension.courtSentencingApi.stubGetCourtCharge(
          DPS_COURT_CHARGE_ID,
          offenderNo = OFFENDER_NO,
          caseID = COURT_CASE_ID_FOR_CREATION,
        )
        NomisApiExtension.nomisApi.stubCourtChargeUpdate(
          offenderChargeId = NOMIS_COURT_CHARGE_ID,
          offenderNo = OFFENDER_NO,
          courtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )

        MappingExtension.mappingServer.stubGetCourtAppearanceMappingGivenDpsId(
          id = DPS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )

        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsId(
          id = DPS_COURT_CHARGE_ID,
          nomisCourtChargeId = NOMIS_COURT_CHARGE_ID,
        )

        publishUpdatedCourtChargeDomainEvent()
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete()
        CourtSentencingApiExtension.courtSentencingApi.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/legacy/charge/${DPS_COURT_CHARGE_ID}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("charge-updated-success"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            Assertions.assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["dpsChargeId"]).isEqualTo(DPS_COURT_CHARGE_ID)
            Assertions.assertThat(it["nomisChargeId"]).isEqualTo(NOMIS_COURT_CHARGE_ID.toString())
            Assertions.assertThat(it["nomisOutcomeCode"]).isEqualTo(COURT_CHARGE_1_RESULT_CODE)
            Assertions.assertThat(it["nomisOffenceCode"]).isEqualTo(COURT_CHARGE_1_OFFENCE_CODE)
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to update the Charge`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(
          WireMock.putRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/$NOMIS_COURT_CASE_ID_FOR_CREATION/court-appearances/$NOMIS_COURT_APPEARANCE_ID/charges/$NOMIS_COURT_CHARGE_ID"))
            .withRequestBody(
              WireMock.matchingJsonPath(
                "offenceCode",
                WireMock.equalTo(COURT_CHARGE_1_OFFENCE_CODE),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "offenceEndDate",
                WireMock.equalTo(COURT_CHARGE_1_OFFENCE_END_DATE),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "offenceDate",
                WireMock.equalTo(COURT_CHARGE_1_OFFENCE_DATE),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "resultCode1",
                WireMock.equalTo(COURT_CHARGE_1_RESULT_CODE),
              ),
            ),
        )
      }
    }

    @Nested
    inner class WhenCourtChargeMappingDoesNotExist {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(COURT_CASE_ID_FOR_CREATION)
        MappingExtension.mappingServer.stubGetCourtAppearanceMappingGivenDpsId(DPS_COURT_APPEARANCE_ID)
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsIdWithError(
          DPS_COURT_CHARGE_ID,
          404,
        )
        publishUpdatedCourtChargeDomainEvent()
      }

      @Test
      fun `will not update an court case in NOMIS`() {
        waitForAnyProcessingToComplete()

        NomisApiExtension.nomisApi.verify(
          0,
          WireMock.putRequestedFor(WireMock.anyUrl()),
        )
      }
    }
  }

  @Nested
  inner class CreateSentence {

    @Nested
    inner class WhenSentenceHasBeenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        publishCreateSentenceDomainEvent(source = "NOMIS")
      }

      @Test
      fun `will create ignore telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("sentence-create-ignored"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            Assertions.assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenSentenceHasBeenCreatedInDPS {
      @BeforeEach
      fun setUp() {
        CourtSentencingApiExtension.courtSentencingApi.stubGetSentence(
          sentenceId = DPS_SENTENCE_ID,
          offenderNo = OFFENDER_NO,
          caseID = COURT_CASE_ID_FOR_CREATION,
          chargeUuid = DPS_COURT_CHARGE_ID,
          startDate = LocalDate.of(2024, 1, 1),
        )
        NomisApiExtension.nomisApi.stubSentenceCreate(
          offenderNo = OFFENDER_NO,
          caseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          nomisSentenceCreateResponseWithOneTerm(),
        )
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsId(
          id = DPS_COURT_CHARGE_ID,
          nomisCourtChargeId = NOMIS_COURT_CHARGE_ID,
        )
        MappingExtension.mappingServer.stubGetCourtAppearanceMappingGivenDpsId(
          id = DPS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )

        MappingExtension.mappingServer.stubGetSentenceMappingGivenDpsIdWithError(DPS_SENTENCE_ID, 404)
        MappingExtension.mappingServer.stubCreateSentence()

        publishCreateSentenceDomainEvent()
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete()
        CourtSentencingApiExtension.courtSentencingApi.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/legacy/sentence/${DPS_SENTENCE_ID}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("sentence-create-success"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            Assertions.assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            Assertions.assertThat(it["dpsChargeId"]).isEqualTo(DPS_COURT_CHARGE_ID)
            Assertions.assertThat(it["nomisChargeId"]).isEqualTo(NOMIS_COURT_CHARGE_ID.toString())
            Assertions.assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["mappingType"]).isEqualTo(SentenceMappingDto.MappingType.DPS_CREATED.toString())
            Assertions.assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
            Assertions.assertThat(it["nomisSentenceSeq"]).isEqualTo(NOMIS_SENTENCE_SEQ.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the Sentence`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(
          WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/court-cases/$NOMIS_COURT_CASE_ID_FOR_CREATION/sentences"))
            .withRequestBody(
              WireMock.matchingJsonPath(
                "offenderChargeIds[0]",
                WireMock.equalTo(NOMIS_COURT_CHARGE_ID.toString()),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "sentenceLevel",
                WireMock.equalTo(SENTENCE_LEVEL_IND),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "fine",
                WireMock.equalTo(FINE_AMOUNT),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "startDate",
                WireMock.equalTo("2024-01-01"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "status",
                WireMock.equalTo("A"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "eventId",
                WireMock.equalTo(NOMIS_COURT_APPEARANCE_ID.toString()),
              ),
            ),
        )
      }

      @Test
      fun `will create a mapping between the two sentences`() {
        waitForAnyProcessingToComplete()

        await untilAsserted {
          MappingExtension.mappingServer.verify(
            WireMock.postRequestedFor(WireMock.urlEqualTo("/mapping/court-sentencing/sentences"))
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "dpsSentenceId",
                  WireMock.equalTo(DPS_SENTENCE_ID),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "nomisSentenceSequence",
                  WireMock.equalTo(
                    NOMIS_SENTENCE_SEQ.toString(),
                  ),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "nomisBookingId",
                  WireMock.equalTo(
                    NOMIS_BOOKING_ID.toString(),
                  ),
                ),
              ),
          )
        }
        await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
      }
    }

    @Nested
    inner class WhenSentenceHasBeenCreatedInDPSAndConsecutiveToAnotherSentence {
      @BeforeEach
      fun setUp() {
        CourtSentencingApiExtension.courtSentencingApi.stubGetSentence(
          sentenceId = DPS_SENTENCE_ID,
          offenderNo = OFFENDER_NO,
          caseID = COURT_CASE_ID_FOR_CREATION,
          chargeUuid = DPS_COURT_CHARGE_ID,
          startDate = LocalDate.of(2024, 1, 1),
          consecutiveToLifetimeUuid = UUID.fromString(DPS_SENTENCE_ID_2),
        )
        NomisApiExtension.nomisApi.stubSentenceCreate(
          offenderNo = OFFENDER_NO,
          caseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          nomisSentenceCreateResponseWithOneTerm(),
        )
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        MappingExtension.mappingServer.stubGetCourtAppearanceMappingGivenDpsId(
          id = DPS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsId(
          id = DPS_COURT_CHARGE_ID,
          nomisCourtChargeId = NOMIS_COURT_CHARGE_ID,
        )

        MappingExtension.mappingServer.stubGetSentenceMappingGivenDpsIdWithError(DPS_SENTENCE_ID, 404)
        MappingExtension.mappingServer.stubGetSentenceMappingGivenDpsId(
          id = DPS_SENTENCE_ID_2,
          nomisSentenceSequence = NOMIS_SENTENCE_SEQ_2,
          nomisBookingId = NOMIS_BOOKING_ID,
        )
        MappingExtension.mappingServer.stubCreateSentence()

        publishCreateSentenceDomainEvent()
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("sentence-create-success"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            Assertions.assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            Assertions.assertThat(it["dpsChargeId"]).isEqualTo(DPS_COURT_CHARGE_ID)
            Assertions.assertThat(it["nomisChargeId"]).isEqualTo(NOMIS_COURT_CHARGE_ID.toString())
            Assertions.assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["mappingType"]).isEqualTo(SentenceMappingDto.MappingType.DPS_CREATED.toString())
            Assertions.assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
            Assertions.assertThat(it["nomisSentenceSeq"]).isEqualTo(NOMIS_SENTENCE_SEQ.toString())
            Assertions.assertThat(it["dpsConsecutiveSentenceId"]).isEqualTo(DPS_SENTENCE_ID_2)
            Assertions.assertThat(it["nomisConsecutiveSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQ_2.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the Sentence`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(
          WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/court-cases/$NOMIS_COURT_CASE_ID_FOR_CREATION/sentences"))
            .withRequestBody(
              WireMock.matchingJsonPath(
                "offenderChargeIds[0]",
                WireMock.equalTo(NOMIS_COURT_CHARGE_ID.toString()),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "sentenceLevel",
                WireMock.equalTo(SENTENCE_LEVEL_IND),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "fine",
                WireMock.equalTo(FINE_AMOUNT),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "startDate",
                WireMock.equalTo("2024-01-01"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "status",
                WireMock.equalTo("A"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "eventId",
                WireMock.equalTo(NOMIS_COURT_APPEARANCE_ID.toString()),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "consecutiveToSentenceSeq",
                WireMock.equalTo("$NOMIS_SENTENCE_SEQ_2"),
              ),
            ),
        )
      }
    }

    @Nested
    inner class WhenMappingAlreadyCreatedForSentence {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(COURT_CASE_ID_FOR_CREATION)
        MappingExtension.mappingServer.stubGetSentenceMappingGivenDpsId(
          id = DPS_SENTENCE_ID,
          nomisSentenceSequence = NOMIS_SENTENCE_SEQ,
          nomisBookingId = NOMIS_BOOKING_ID,
        )
        publishCreateSentenceDomainEvent()
      }

      @Test
      fun `will not create a sentence in NOMIS`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("sentence-create-duplicate"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          },
          isNull(),
        )

        NomisApiExtension.nomisApi.verify(
          0,
          WireMock.postRequestedFor(WireMock.anyUrl()),
        )
      }
    }

    @Nested
    inner class WhenMappingServiceFailsOnce {
      @BeforeEach
      fun setUp() {
        CourtSentencingApiExtension.courtSentencingApi.stubGetSentence(
          sentenceId = DPS_SENTENCE_ID,
          offenderNo = OFFENDER_NO,
          caseID = COURT_CASE_ID_FOR_CREATION,
          chargeUuid = DPS_COURT_CHARGE_ID,
        )
        NomisApiExtension.nomisApi.stubSentenceCreate(
          offenderNo = OFFENDER_NO,
          caseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          nomisSentenceCreateResponseWithOneTerm(),
        )
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsId(
          id = DPS_COURT_CHARGE_ID,
          nomisCourtChargeId = NOMIS_COURT_CHARGE_ID,
        )
        MappingExtension.mappingServer.stubGetCourtAppearanceMappingGivenDpsId(
          id = DPS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )

        MappingExtension.mappingServer.stubGetSentenceMappingGivenDpsIdWithError(DPS_SENTENCE_ID, 404)
        MappingExtension.mappingServer.stubCreateSentenceWithErrorFollowedBySlowSuccess()

        publishCreateSentenceDomainEvent()

        await untilCallTo { CourtSentencingApiExtension.courtSentencingApi.getCountFor("/legacy/sentence/$DPS_SENTENCE_ID") } matches { it == 1 }
        await untilCallTo { NomisApiExtension.nomisApi.postCountFor("/prisoners/$OFFENDER_NO/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/sentences") } matches { it == 1 }
      }

      @Test
      fun `should only create the NOMIS sentence once`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("sentence-mapping-retry-success"),
            any(),
            isNull(),
          )
        }
        NomisApiExtension.nomisApi.verify(
          1,
          WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/sentences")),
        )
      }

      @Test
      fun `will eventually create a mapping after NOMIS sentence is created`() {
        await untilAsserted {
          MappingExtension.mappingServer.verify(
            2,
            WireMock.postRequestedFor(WireMock.urlEqualTo("/mapping/court-sentencing/sentences"))
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "dpsSentenceId",
                  WireMock.equalTo(DPS_SENTENCE_ID),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "nomisSentenceSequence",
                  WireMock.equalTo(
                    NOMIS_SENTENCE_SEQ.toString(),
                  ),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "nomisBookingId",
                  WireMock.equalTo(
                    NOMIS_BOOKING_ID.toString(),
                  ),
                ),
              ),
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("sentence-mapping-retry-success"),
            any(),
            isNull(),
          )
        }
      }
    }

    @Nested
    inner class WhenMappingServiceFailsDueToMissingChargeEntity {
      @BeforeEach
      fun setUp() {
        CourtSentencingApiExtension.courtSentencingApi.stubGetSentence(
          sentenceId = DPS_SENTENCE_ID,
          offenderNo = OFFENDER_NO,
          caseID = COURT_CASE_ID_FOR_CREATION,
          chargeUuid = DPS_COURT_CHARGE_ID,
        )
        NomisApiExtension.nomisApi.stubSentenceCreate(
          offenderNo = OFFENDER_NO,
          caseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          nomisSentenceCreateResponseWithOneTerm(),
        )
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        MappingExtension.mappingServer.stubGetCourtAppearanceMappingGivenDpsId(
          id = DPS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )
        MappingExtension.mappingServer.stubGetSentenceMappingGivenDpsIdWithError(DPS_SENTENCE_ID, 404)
        MappingExtension.mappingServer.stubCreateSentence()

        // a parent entity has initially not been created but then is available on retry
        MappingExtension.mappingServer.stubGetCourtChargeNotFoundFollowedBySlowSuccess(
          DPS_COURT_CHARGE_ID,
          NOMIS_COURT_CHARGE_ID,
        )
        publishCreateSentenceDomainEvent()

        await untilCallTo { CourtSentencingApiExtension.courtSentencingApi.getCountFor("/legacy/sentence/$DPS_SENTENCE_ID") } matches { it == 2 }
        await untilCallTo { NomisApiExtension.nomisApi.postCountFor("/prisoners/$OFFENDER_NO/court-cases/$NOMIS_COURT_CASE_ID_FOR_CREATION/sentences") } matches { it == 1 }
      }

      @Test
      fun `should only create the NOMIS sentence once`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("sentence-create-success"),
            any(),
            isNull(),
          )
        }
        NomisApiExtension.nomisApi.verify(
          1,
          WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/sentences")),
        )
      }
    }
  }

  @Nested
  inner class UpdateSentence {

    @Nested
    inner class WhenSentenceHasBeenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        publishUpdateSentenceDomainEvent(source = "NOMIS")
      }

      @Test
      fun `will create ignore telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("sentence-updated-ignored"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenSentenceHasBeenUpdatedInDPS {
      @BeforeEach
      fun setUp() {
        CourtSentencingApiExtension.courtSentencingApi.stubGetSentence(
          sentenceId = DPS_SENTENCE_ID,
          offenderNo = OFFENDER_NO,
          caseID = COURT_CASE_ID_FOR_CREATION,
          chargeUuid = DPS_COURT_CHARGE_ID,
        )
        NomisApiExtension.nomisApi.stubSentenceUpdate(
          offenderNo = OFFENDER_NO,
          caseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          sentenceSeq = NOMIS_SENTENCE_SEQ,
        )
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        MappingExtension.mappingServer.stubGetCourtAppearanceMappingGivenDpsId(
          id = DPS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )

        MappingExtension.mappingServer.stubGetSentenceMappingGivenDpsId(
          id = DPS_SENTENCE_ID,
          nomisSentenceSequence = NOMIS_SENTENCE_SEQ,
          nomisBookingId = NOMIS_BOOKING_ID,
        )

        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsId(DPS_COURT_CHARGE_ID, NOMIS_COURT_CHARGE_ID)

        publishUpdateSentenceDomainEvent()
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete()
        CourtSentencingApiExtension.courtSentencingApi.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/legacy/sentence/${DPS_SENTENCE_ID}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("sentence-updated-success"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            Assertions.assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
            Assertions.assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            Assertions.assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            Assertions.assertThat(it["nomisSentenceSeq"]).isEqualTo(NOMIS_SENTENCE_SEQ.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to update the Sentence`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(WireMock.putRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/sentences/${NOMIS_SENTENCE_SEQ}")))
      }
    }

    @Nested
    inner class WhenMappingDoesntExistForSentence {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(COURT_CASE_ID_FOR_CREATION)
        MappingExtension.mappingServer.stubGetSentenceMappingGivenDpsIdWithError(
          DPS_SENTENCE_ID,
          404,
        )
        publishUpdateSentenceDomainEvent()
      }

      @Test
      fun `will not update a sentence in NOMIS`() {
        await untilAsserted {
          verify(telemetryClient, times(3)).trackEvent(
            eq("sentence-updated-failed"),
            org.mockito.kotlin.check {
              Assertions.assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
              Assertions.assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
              Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
              Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            },
            isNull(),
          )
        }

        NomisApiExtension.nomisApi.verify(
          0,
          WireMock.putRequestedFor(WireMock.anyUrl()),
        )
      }
    }

    @Nested
    inner class WhenMappingDoesntExistForCharge {
      @BeforeEach
      fun setUp() {
        CourtSentencingApiExtension.courtSentencingApi.stubGetSentence(
          sentenceId = DPS_SENTENCE_ID,
          offenderNo = OFFENDER_NO,
          caseID = COURT_CASE_ID_FOR_CREATION,
          chargeUuid = DPS_COURT_CHARGE_ID,
        )

        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )

        MappingExtension.mappingServer.stubGetCourtAppearanceMappingGivenDpsId(
          id = DPS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )

        MappingExtension.mappingServer.stubGetSentenceMappingGivenDpsId(
          id = DPS_SENTENCE_ID,
          nomisSentenceSequence = NOMIS_SENTENCE_SEQ,
          nomisBookingId = NOMIS_BOOKING_ID,
        )

        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsIdWithError(DPS_COURT_CHARGE_ID, 404)

        publishUpdateSentenceDomainEvent()
      }

      @Test
      fun `will create failed telemetry`() {
        waitForAnyProcessingToComplete()

        await untilAsserted {
          verify(telemetryClient, times(3)).trackEvent(
            eq("sentence-updated-failed"),
            org.mockito.kotlin.check {
              Assertions.assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
              Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
              Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
              Assertions.assertThat(it["reason"])
                .isEqualTo("Parent entity not found. Dps charge id: $DPS_COURT_CHARGE_ID")
            },
            isNull(),
          )
        }

        NomisApiExtension.nomisApi.verify(
          0,
          WireMock.putRequestedFor(WireMock.anyUrl()),
        )
      }
    }
  }

  @Nested
  inner class DeleteSentence {
    @Nested
    inner class WhenSentenceHasBeenDeletedInDPS {
      @BeforeEach
      fun setUp() {
        NomisApiExtension.nomisApi.stubSentenceDelete(
          offenderNo = OFFENDER_NO,
          caseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          sentenceSeq = NOMIS_SENTENCE_SEQ,
        )
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        MappingExtension.mappingServer.stubGetSentenceMappingGivenDpsId(
          id = DPS_SENTENCE_ID,
          nomisSentenceSequence = NOMIS_SENTENCE_SEQ,
          nomisBookingId = NOMIS_BOOKING_ID,
        )
        MappingExtension.mappingServer.stubDeleteSentence(id = DPS_SENTENCE_ID)
        publishDeleteSentenceDomainEvent()
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          org.mockito.kotlin.eq("sentence-deleted-success"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            Assertions.assertThat(it["nomisSentenceSeq"]).isEqualTo(NOMIS_SENTENCE_SEQ.toString())
            Assertions.assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to delete the sentence`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/court-cases/$NOMIS_COURT_CASE_ID_FOR_CREATION/sentences/$NOMIS_SENTENCE_SEQ")))
      }

      @Test
      fun `will call the mapping service to delete the mapping`() {
        waitForAnyProcessingToComplete()
        MappingExtension.mappingServer.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/mapping/court-sentencing/sentences/dps-sentence-id/$DPS_SENTENCE_ID")))
      }
    }

    @Nested
    inner class WhenNoMappingExistsForSentence {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetSentenceMappingGivenDpsIdWithError(DPS_SENTENCE_ID, 404)
        publishDeleteSentenceDomainEvent()
      }

      @Test
      fun `will not attempt to delete a sentence in NOMIS`() {
        await untilAsserted {
          verify(telemetryClient, times(1)).trackEvent(
            org.mockito.kotlin.eq("sentence-deleted-skipped"),
            org.mockito.kotlin.check {
              Assertions.assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
              Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            },
            isNull(),
          )
        }

        NomisApiExtension.nomisApi.verify(
          0,
          WireMock.deleteRequestedFor(WireMock.anyUrl()),
        )
      }
    }
  }

  @Nested
  inner class CreatePeriodLength {

    @Nested
    inner class WhenPeriodLengthHasBeenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        publishCreatePeriodLengthDomainEvent(source = "NOMIS")
      }

      @Test
      fun `will create ignore telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("sentence-term-create-ignored"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            Assertions.assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            Assertions.assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenPeriodLengthHasBeenCreatedInDPS {
      @BeforeEach
      fun setUp() {
        CourtSentencingApiExtension.courtSentencingApi.stubGetPeriodLength(
          periodLengthId = DPS_TERM_ID,
          sentenceId = DPS_SENTENCE_ID,
          offenderNo = OFFENDER_NO,
          caseId = COURT_CASE_ID_FOR_CREATION,
          chargeId = DPS_COURT_CHARGE_ID,
          appearanceId = DPS_COURT_APPEARANCE_ID,
        )
        NomisApiExtension.nomisApi.stubSentenceTermCreate(
          offenderNo = OFFENDER_NO,
          caseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          sentenceSeq = NOMIS_SENTENCE_SEQ,
          response = CreateSentenceTermResponse(sentenceSeq = NOMIS_SENTENCE_SEQ, termSeq = NOMIS_TERM_SEQ, bookingId = NOMIS_BOOKING_ID),
        )
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )

        MappingExtension.mappingServer.stubGetSentenceMappingGivenDpsId(
          id = DPS_SENTENCE_ID,
          nomisSentenceSequence = NOMIS_SENTENCE_SEQ,
          nomisBookingId = NOMIS_BOOKING_ID,
        )

        MappingExtension.mappingServer.stubGetSentenceTermMappingGivenDpsIdWithError(DPS_TERM_ID, 404)
        MappingExtension.mappingServer.stubCreateSentenceTerm()

        publishCreatePeriodLengthDomainEvent()
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete()
        CourtSentencingApiExtension.courtSentencingApi.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/legacy/period-length/${DPS_TERM_ID}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("sentence-term-create-success"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            Assertions.assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
            Assertions.assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            Assertions.assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["mappingType"]).isEqualTo(SentenceTermMappingDto.MappingType.DPS_CREATED.toString())
            Assertions.assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
            Assertions.assertThat(it["nomisSentenceSeq"]).isEqualTo(NOMIS_SENTENCE_SEQ.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the Sentence Term`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(
          WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/court-cases/$NOMIS_COURT_CASE_ID_FOR_CREATION/sentences/$NOMIS_SENTENCE_SEQ/sentence-terms"))
            .withRequestBody(
              WireMock.matchingJsonPath(
                "weeks",
                WireMock.equalTo("4"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "years",
                WireMock.equalTo("2"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "months",
                WireMock.equalTo("6"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "days",
                WireMock.equalTo("15"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "sentenceTermType",
                WireMock.equalTo("TERM"),
              ),
            ),
        )
      }

      @Test
      fun `will create a mapping between the two sentences`() {
        waitForAnyProcessingToComplete()

        await untilAsserted {
          MappingExtension.mappingServer.verify(
            WireMock.postRequestedFor(WireMock.urlEqualTo("/mapping/court-sentencing/sentence-terms"))
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "dpsTermId",
                  WireMock.equalTo(DPS_TERM_ID),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "nomisSentenceSequence",
                  WireMock.equalTo(
                    NOMIS_SENTENCE_SEQ.toString(),
                  ),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "nomisTermSequence",
                  WireMock.equalTo(
                    NOMIS_TERM_SEQ.toString(),
                  ),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "nomisBookingId",
                  WireMock.equalTo(
                    NOMIS_BOOKING_ID.toString(),
                  ),
                ),
              ),
          )
        }
        await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
      }
    }

    @Nested
    inner class WhenMappingAlreadyCreatedForPeriodLength {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetSentenceTermMappingGivenDpsId(
          id = DPS_TERM_ID,
          nomisSentenceSequence = NOMIS_SENTENCE_SEQ,
          nomisBookingId = NOMIS_BOOKING_ID,
          nomisTermSequence = NOMIS_TERM_SEQ,
        )
        publishCreatePeriodLengthDomainEvent()
      }

      @Test
      fun `will not create a sentence term in NOMIS`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("sentence-term-create-duplicate"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            Assertions.assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          },
          isNull(),
        )

        NomisApiExtension.nomisApi.verify(
          0,
          WireMock.postRequestedFor(WireMock.anyUrl()),
        )
      }
    }

    @Nested
    inner class WhenMappingServiceFailsOnce {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetSentenceTermMappingGivenDpsIdWithError(DPS_TERM_ID, 404)
        CourtSentencingApiExtension.courtSentencingApi.stubGetPeriodLength(
          sentenceId = DPS_SENTENCE_ID,
          offenderNo = OFFENDER_NO,
          caseId = COURT_CASE_ID_FOR_CREATION,
          periodLengthId = DPS_TERM_ID,
          chargeId = DPS_COURT_CHARGE_ID,
          appearanceId = DPS_COURT_APPEARANCE_ID,
        )
        NomisApiExtension.nomisApi.stubSentenceTermCreate(
          offenderNo = OFFENDER_NO,
          caseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          sentenceSeq = NOMIS_SENTENCE_SEQ,
          response = CreateSentenceTermResponse(sentenceSeq = NOMIS_SENTENCE_SEQ, termSeq = NOMIS_TERM_SEQ, bookingId = NOMIS_BOOKING_ID),
        )
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        MappingExtension.mappingServer.stubGetSentenceMappingGivenDpsId(
          id = DPS_SENTENCE_ID,
          nomisSentenceSequence = NOMIS_SENTENCE_SEQ,
          nomisBookingId = NOMIS_BOOKING_ID,
        )

        MappingExtension.mappingServer.stubCreateSentenceTermWithErrorFollowedBySlowSuccess()

        publishCreatePeriodLengthDomainEvent()

        await untilCallTo { CourtSentencingApiExtension.courtSentencingApi.getCountFor("/legacy/period-length/$DPS_TERM_ID") } matches { it == 1 }
        await untilCallTo { NomisApiExtension.nomisApi.postCountFor("/prisoners/$OFFENDER_NO/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/sentences/$NOMIS_SENTENCE_SEQ/sentence-terms") } matches { it == 1 }
      }

      @Test
      fun `should only create the NOMIS sentence term once`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("sentence-term-mapping-retry-success"),
            any(),
            isNull(),
          )
        }
        NomisApiExtension.nomisApi.verify(
          1,
          WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/sentences/$NOMIS_SENTENCE_SEQ/sentence-terms")),
        )
      }

      @Test
      fun `will eventually create a mapping after NOMIS sentence term is created`() {
        await untilAsserted {
          MappingExtension.mappingServer.verify(
            2,
            WireMock.postRequestedFor(WireMock.urlEqualTo("/mapping/court-sentencing/sentence-terms"))
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "dpsTermId",
                  WireMock.equalTo(DPS_TERM_ID),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "nomisSentenceSequence",
                  WireMock.equalTo(
                    NOMIS_SENTENCE_SEQ.toString(),
                  ),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "nomisTermSequence",
                  WireMock.equalTo(
                    NOMIS_TERM_SEQ.toString(),
                  ),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "nomisBookingId",
                  WireMock.equalTo(
                    NOMIS_BOOKING_ID.toString(),
                  ),
                ),
              ),
          )
        }
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("sentence-term-mapping-retry-success"),
            any(),
            isNull(),
          )
        }
      }
    }
  }

  @Nested
  inner class UpdatePeriodLength {

    @Nested
    inner class WhenSentenceTermHasBeenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        publishUpdatePeriodLengthDomainEvent(source = "NOMIS")
      }

      @Test
      fun `will create ignore telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("sentence-term-updated-ignored"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            Assertions.assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenPeriodLengthHasBeenUpdatedInDPS {
      @BeforeEach
      fun setUp() {
        CourtSentencingApiExtension.courtSentencingApi.stubGetPeriodLength(
          sentenceId = DPS_SENTENCE_ID,
          offenderNo = OFFENDER_NO,
          caseId = COURT_CASE_ID_FOR_CREATION,
          periodLengthId = DPS_TERM_ID,
          chargeId = DPS_COURT_CHARGE_ID,
          appearanceId = DPS_COURT_APPEARANCE_ID,
        )
        NomisApiExtension.nomisApi.stubSentenceTermUpdate(
          offenderNo = OFFENDER_NO,
          caseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          sentenceSeq = NOMIS_SENTENCE_SEQ,
          termSeq = NOMIS_TERM_SEQ,
        )
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )

        MappingExtension.mappingServer.stubGetSentenceTermMappingGivenDpsId(
          id = DPS_TERM_ID,
          nomisSentenceSequence = NOMIS_SENTENCE_SEQ,
          nomisTermSequence = NOMIS_TERM_SEQ,
          nomisBookingId = NOMIS_BOOKING_ID,
        )

        publishUpdatePeriodLengthDomainEvent()
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete()
        CourtSentencingApiExtension.courtSentencingApi.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/legacy/period-length/${DPS_TERM_ID}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("sentence-term-updated-success"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            Assertions.assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
            Assertions.assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            Assertions.assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
            Assertions.assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            Assertions.assertThat(it["nomisSentenceSeq"]).isEqualTo(NOMIS_SENTENCE_SEQ.toString())
            Assertions.assertThat(it["nomisTermSeq"]).isEqualTo(NOMIS_TERM_SEQ.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to update the Sentence term`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(WireMock.putRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/sentences/${NOMIS_SENTENCE_SEQ}/sentence-terms/${NOMIS_TERM_SEQ}")))
      }
    }

    @Nested
    inner class WhenMappingDoesntExistForPeriodLength {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(COURT_CASE_ID_FOR_CREATION)
        MappingExtension.mappingServer.stubGetSentenceTermMappingGivenDpsIdWithError(
          DPS_SENTENCE_ID,
          404,
        )
        publishUpdatePeriodLengthDomainEvent()
      }

      @Test
      fun `will not update a sentence term in NOMIS`() {
        await untilAsserted {
          verify(telemetryClient, times(3)).trackEvent(
            eq("sentence-term-updated-failed"),
            org.mockito.kotlin.check {
              Assertions.assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
              Assertions.assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
              Assertions.assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
              Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
              Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            },
            isNull(),
          )
        }

        NomisApiExtension.nomisApi.verify(
          0,
          WireMock.putRequestedFor(WireMock.anyUrl()),
        )
      }
    }
  }

  @Nested
  inner class DeletePeriodLength {
    @Nested
    inner class WhenPeriodLengthHasBeenDeletedInDPS {
      @BeforeEach
      fun setUp() {
        NomisApiExtension.nomisApi.stubSentenceTermDelete(
          offenderNo = OFFENDER_NO,
          caseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          sentenceSeq = NOMIS_SENTENCE_SEQ,
          termSeq = NOMIS_TERM_SEQ,
        )
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        MappingExtension.mappingServer.stubGetSentenceTermMappingGivenDpsId(
          id = DPS_TERM_ID,
          nomisSentenceSequence = NOMIS_SENTENCE_SEQ,
          nomisBookingId = NOMIS_BOOKING_ID,
          nomisTermSequence = NOMIS_TERM_SEQ,
        )
        MappingExtension.mappingServer.stubDeleteSentenceTerm(id = DPS_TERM_ID)
        publishDeletePeriodLengthDomainEvent()
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          org.mockito.kotlin.eq("sentence-term-deleted-success"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            Assertions.assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
            Assertions.assertThat(it["nomisSentenceSeq"]).isEqualTo(NOMIS_SENTENCE_SEQ.toString())
            Assertions.assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to delete the sentence term`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/court-cases/$NOMIS_COURT_CASE_ID_FOR_CREATION/sentences/$NOMIS_SENTENCE_SEQ/sentence-terms/$NOMIS_TERM_SEQ")))
      }

      @Test
      fun `will call the mapping service to delete the mapping`() {
        waitForAnyProcessingToComplete()
        MappingExtension.mappingServer.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/mapping/court-sentencing/sentence-terms/dps-term-id/$DPS_TERM_ID")))
      }
    }

    @Nested
    inner class WhenNoMappingExistsForPeriodLength {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetSentenceTermMappingGivenDpsIdWithError(DPS_TERM_ID, 404)
        publishDeletePeriodLengthDomainEvent()
      }

      @Test
      fun `will not attempt to delete a sentence term in NOMIS`() {
        await untilAsserted {
          verify(telemetryClient, times(1)).trackEvent(
            org.mockito.kotlin.eq("sentence-term-deleted-skipped"),
            org.mockito.kotlin.check {
              Assertions.assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
              Assertions.assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
              Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            },
            isNull(),
          )
        }

        NomisApiExtension.nomisApi.verify(
          0,
          WireMock.deleteRequestedFor(WireMock.anyUrl()),
        )
      }
    }
  }

  @Nested
  inner class RefreshCaseReferences {
    @Nested
    inner class WhenCourtAppearanceHasBeenUpdatedInDPS {
      @BeforeEach
      fun setUp() {
        NomisApiExtension.nomisApi.stubCaseReferenceRefresh(
          OFFENDER_NO,
          NOMIS_COURT_CASE_ID_FOR_CREATION,
        )

        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )

        CourtSentencingApiExtension.courtSentencingApi.stubCourtCaseGet(
          COURT_CASE_ID_FOR_CREATION,
          legacyCourtCaseResponse(),
        )
        publishCaseReferencesUpdatedDomainEvent()
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete()
        CourtSentencingApiExtension.courtSentencingApi.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/legacy/court-case/${COURT_CASE_ID_FOR_CREATION}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("case-references-refreshed-success"),
          org.mockito.kotlin.check {
            Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            Assertions.assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
            Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            Assertions.assertThat(it["caseReferences"])
              .isEqualTo("[CaseIdentifier(reference=$CASE_REFERENCE, createdDate=2024-01-01T10:10)]")
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to refresh the refs`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/case-identifiers")))
      }
    }

    @Nested
    inner class WhenMappingDoesntExistForCourtCase {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          DPS_COURT_APPEARANCE_ID,
          404,
        )
        publishCaseReferencesUpdatedDomainEvent()
      }

      @Test
      fun `will not update an court appearance in NOMIS`() {
        await untilAsserted {
          verify(telemetryClient, times(3)).trackEvent(
            eq("case-references-refreshed-failure"),
            org.mockito.kotlin.check {
              Assertions.assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
              Assertions.assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            },
            isNull(),
          )
        }

        NomisApiExtension.nomisApi.verify(
          0,
          WireMock.putRequestedFor(WireMock.anyUrl()),
        )
      }
    }
  }

  private fun publishCreateCourtCaseDomainEvent(source: String = "DPS") {
    val eventType = "court-case.inserted"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          courtCaseMessagePayload(
            courtCaseId = COURT_CASE_ID_FOR_CREATION,
            offenderNo = OFFENDER_NO,
            eventType = eventType,
            source = source,
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

  private fun publishDeleteCourtCaseDomainEvent(source: String = "DPS") {
    val eventType = "court-case.deleted"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          courtCaseMessagePayload(
            courtCaseId = COURT_CASE_ID_FOR_CREATION,
            offenderNo = OFFENDER_NO,
            eventType = eventType,
            source = source,
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

  private fun publishDeleteCourtAppearanceDomainEvent(source: String = "DPS") {
    val eventType = "court-appearance.deleted"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          courtAppearanceMessagePayload(
            courtCaseId = COURT_CASE_ID_FOR_CREATION,
            courtAppearanceId = DPS_COURT_APPEARANCE_ID,
            offenderNo = OFFENDER_NO,
            eventType = eventType,
            source = source,
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

  private fun publishCreateCourtAppearanceDomainEvent(source: String = "DPS") {
    val eventType = "court-appearance.inserted"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          courtAppearanceMessagePayload(
            courtAppearanceId = DPS_COURT_APPEARANCE_ID,
            courtCaseId = COURT_CASE_ID_FOR_CREATION,
            offenderNo = OFFENDER_NO,
            eventType = eventType,
            source = source,
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

  private fun publishUpdateCourtAppearanceDomainEvent(source: String = "DPS") {
    val eventType = "court-appearance.updated"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          courtAppearanceMessagePayload(
            courtAppearanceId = DPS_COURT_APPEARANCE_ID,
            courtCaseId = COURT_CASE_ID_FOR_CREATION,
            offenderNo = OFFENDER_NO,
            eventType = eventType,
            source = source,
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

  private fun publishCreateCourtChargeDomainEvent(source: String = "DPS") {
    val eventType = "charge.inserted"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          courtChargeMessagePayload(
            courtChargeId = DPS_COURT_CHARGE_ID,
            courtCaseId = COURT_CASE_ID_FOR_CREATION,
            offenderNo = OFFENDER_NO,
            eventType = eventType,
            source = source,
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

  private fun publishUpdatedCourtChargeDomainEvent(source: String = "DPS") {
    val eventType = "charge.updated"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          courtChargeMessagePayload(
            courtChargeId = DPS_COURT_CHARGE_ID,
            courtCaseId = COURT_CASE_ID_FOR_CREATION,
            courtAppearanceId = DPS_COURT_APPEARANCE_ID,
            offenderNo = OFFENDER_NO,
            eventType = eventType,
            source = source,
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

  private fun publishCaseReferencesUpdatedDomainEvent() {
    val eventType = "legacy.court-case-references.updated"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          caseReferencePayload(
            courtCaseId = COURT_CASE_ID_FOR_CREATION,
            offenderNo = OFFENDER_NO,
            eventType = eventType,
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

  private fun publishCreateSentenceDomainEvent(source: String = "DPS") {
    val eventType = "sentence.inserted"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          sentenceMessagePayload(
            sentenceId = DPS_SENTENCE_ID,
            courtAppearanceId = DPS_COURT_APPEARANCE_ID,
            courtCaseId = COURT_CASE_ID_FOR_CREATION,
            offenderNo = OFFENDER_NO,
            eventType = eventType,
            source = source,
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

  private fun publishUpdateSentenceDomainEvent(source: String = "DPS") {
    val eventType = "sentence.updated"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          sentenceMessagePayload(
            sentenceId = DPS_SENTENCE_ID,
            courtAppearanceId = DPS_COURT_APPEARANCE_ID,
            courtCaseId = COURT_CASE_ID_FOR_CREATION,
            offenderNo = OFFENDER_NO,
            eventType = eventType,
            source = source,
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

  private fun publishDeleteSentenceDomainEvent(source: String = "DPS") {
    val eventType = "sentence.deleted"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          sentenceMessagePayload(
            sentenceId = DPS_SENTENCE_ID,
            courtCaseId = COURT_CASE_ID_FOR_CREATION,
            offenderNo = OFFENDER_NO,
            eventType = eventType,
            courtAppearanceId = DPS_COURT_APPEARANCE_ID,
            source = source,
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

  private fun publishCreatePeriodLengthDomainEvent(source: String = "DPS") {
    val eventType = "sentence.period-length.inserted"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          periodLengthMessagePayload(
            sentenceId = DPS_SENTENCE_ID,
            periodLengthId = DPS_TERM_ID,
            courtAppearanceId = DPS_COURT_APPEARANCE_ID,
            courtCaseId = COURT_CASE_ID_FOR_CREATION,
            offenderNo = OFFENDER_NO,
            eventType = eventType,
            source = source,
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

  private fun publishUpdatePeriodLengthDomainEvent(source: String = "DPS") {
    val eventType = "sentence.period-length.updated"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          periodLengthMessagePayload(
            sentenceId = DPS_SENTENCE_ID,
            periodLengthId = DPS_TERM_ID,
            courtAppearanceId = DPS_COURT_APPEARANCE_ID,
            courtCaseId = COURT_CASE_ID_FOR_CREATION,
            offenderNo = OFFENDER_NO,
            eventType = eventType,
            source = source,
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

  private fun publishDeletePeriodLengthDomainEvent(source: String = "DPS") {
    val eventType = "sentence.period-length.deleted"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          periodLengthMessagePayload(
            sentenceId = DPS_SENTENCE_ID,
            periodLengthId = DPS_TERM_ID,
            courtCaseId = COURT_CASE_ID_FOR_CREATION,
            offenderNo = OFFENDER_NO,
            eventType = eventType,
            courtAppearanceId = DPS_COURT_APPEARANCE_ID,
            source = source,
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

  fun courtCaseMessagePayload(courtCaseId: String, offenderNo: String, eventType: String, source: String = "DPS") = """{"eventType":"$eventType", "additionalInformation": {"courtCaseId":"$courtCaseId", "source": "$source"}, "personReference": {"identifiers":[{"type":"NOMS", "value":"$offenderNo"}]}}"""

  fun courtAppearanceMessagePayload(
    courtCaseId: String,
    courtAppearanceId: String,
    offenderNo: String,
    eventType: String,
    source: String = "DPS",
  ) = """{"eventType":"$eventType", "additionalInformation": {"courtAppearanceId":"$courtAppearanceId", "courtCaseId":"$courtCaseId", "source": "$source"}, "personReference": {"identifiers":[{"type":"NOMS", "value":"$offenderNo"}]}}"""

  fun courtChargeMessagePayload(
    courtCaseId: String,
    courtChargeId: String,
    courtAppearanceId: String? = null,
    offenderNo: String,
    eventType: String,
    source: String = "DPS",
  ) = """{"eventType":"$eventType", "additionalInformation": {"courtChargeId":"$courtChargeId", "courtCaseId":"$courtCaseId", ${courtAppearanceId?.let { """"courtAppearanceId":"$it",""" } ?: ""} "source": "$source"}, "personReference": {"identifiers":[{"type":"NOMS", "value":"$offenderNo"}]}}"""

  fun sentenceMessagePayload(
    courtCaseId: String,
    sentenceId: String,
    courtAppearanceId: String,
    offenderNo: String,
    eventType: String,
    source: String = "DPS",
  ) = """{"eventType":"$eventType", "additionalInformation": {"sentenceId":"$sentenceId", "courtAppearanceId":"$courtAppearanceId", "courtCaseId":"$courtCaseId", "source": "$source"}, "personReference": {"identifiers":[{"type":"NOMS", "value":"$offenderNo"}]}}"""

  fun periodLengthMessagePayload(
    courtCaseId: String,
    sentenceId: String,
    periodLengthId: String,
    courtAppearanceId: String,
    offenderNo: String,
    eventType: String,
    source: String = "DPS",
  ) = """{"eventType":"$eventType", "additionalInformation": {"periodLengthId":"$periodLengthId","sentenceId":"$sentenceId", "courtAppearanceId":"$courtAppearanceId", "courtCaseId":"$courtCaseId", "source": "$source"}, "personReference": {"identifiers":[{"type":"NOMS", "value":"$offenderNo"}]}}"""

  fun nomisCourtAppearanceCreateResponse(): String = """{ "id": $NOMIS_COURT_APPEARANCE_ID, 
      |"courtEventChargesIds": [] }
  """.trimMargin()

  fun nomisCourtAppearanceUpdateResponseWithTwoDeletedCharges(): String = """{ "id": $NOMIS_COURT_APPEARANCE_ID, 
      |"createdCourtEventChargesIds": [],
      |"deletedOffenderChargesIds": [
      |{"offenderChargeId": $NOMIS_COURT_CHARGE_5_ID },
      |{"offenderChargeId": $NOMIS_COURT_CHARGE_6_ID }
      |]
      | }
  """.trimMargin()

  fun nomisSentenceCreateResponseWithOneTerm(): String = """{ "bookingId": $NOMIS_BOOKING_ID, 
      |"sentenceSeq": $NOMIS_SENTENCE_SEQ,
      |"termSeq": $NOMIS_TERM_SEQ
      | }
  """.trimMargin()

  fun nomisCourtChargeCreateResponse(): String = """{ "offenderChargeId": $NOMIS_COURT_CHARGE_ID }"""

  fun legacyCourtCaseResponse() = LegacyCourtCase(
    courtCaseUuid = COURT_CASE_ID_FOR_CREATION,
    prisonerId = OFFENDER_NO,
    courtId = DONCASTER_COURT_CODE,
    caseReference = CASE_REFERENCE,
    startDate = LocalDate.of(2024, 1, 1),
    active = true,
    caseReferences = listOf(
      CaseReferenceLegacyData(
        offenderCaseReference = CASE_REFERENCE,
        updatedDate = LocalDateTime.of(2024, 1, 1, 10, 10, 0),
      ),
    ),
  )

  fun caseReferencePayload(courtCaseId: String, offenderNo: String, eventType: String, source: String = "DPS") = """{"eventType":"$eventType", "additionalInformation": {"courtCaseId":"$courtCaseId", "source": "$source"}, "personReference": {"identifiers":[{"type":"NOMS", "value":"$offenderNo"}]}}"""
}
