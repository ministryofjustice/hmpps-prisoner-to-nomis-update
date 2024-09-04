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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseMapping
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.COURT_CHARGE_1_OFFENCE_CODE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.COURT_CHARGE_1_OFFENCE_DATE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.COURT_CHARGE_1_OFFENCE_END_DATE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.COURT_CHARGE_2_OFFENCE_CODE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.COURT_CHARGE_2_OFFENCE_DATE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.COURT_CHARGE_2_OFFENCE_END_DATE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.COURT_CHARGE_3_OFFENCE_CODE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.COURT_CHARGE_3_OFFENCE_DATE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.COURT_CHARGE_4_OFFENCE_CODE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.COURT_CHARGE_4_OFFENCE_DATE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.CourtSentencingApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension

private const val COURT_CASE_ID_FOR_CREATION = "12345"
private const val NOMIS_COURT_CASE_ID_FOR_CREATION = 7L
private const val DPS_COURT_APPEARANCE_ID = "9c591b18-642a-484a-a967-2d17b5c9c5a1"
private const val NOMIS_COURT_APPEARANCE_ID = 3L
private const val NOMIS_NEXT_COURT_APPEARANCE_ID = 9L
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
private const val OFFENDER_NO = "AB12345"
private const val OFFENCES_COUNT = 1
private const val DONCASTER_COURT_CODE = "DRBYYC"
private const val PRISON_ID = "MDI"

class CourtCasesToNomisIntTest : SqsIntegrationTestBase() {

  @Nested
  inner class CreateCourtCase {
    @Nested
    inner class WhenCourtCaseHasBeenCreatedInDPS {
      @BeforeEach
      fun setUp() {
        CourtSentencingApiExtension.courtSentencingApi.stubCourtCaseGet(
          COURT_CASE_ID_FOR_CREATION,
          offenderNo = OFFENDER_NO,
          courtAppearanceId = DPS_COURT_APPEARANCE_ID,
          courtCharge1Id = DPS_COURT_CHARGE_ID,
          courtCharge2Id = DPS_COURT_CHARGE_2_ID,
          courtId = DONCASTER_COURT_CODE,
        )
        NomisApiExtension.nomisApi.stubCourtCaseCreate(
          OFFENDER_NO,
          nomisCourtCaseCreateResponseWithTwoCharges(),
        )
        MappingExtension.mappingServer.stubGetCreateCaseMappingGivenDpsIdWithError(COURT_CASE_ID_FOR_CREATION, 404)
        MappingExtension.mappingServer.stubCreateCourtCase()
        publishCreateCourtCaseDomainEvent()
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete()
        CourtSentencingApiExtension.courtSentencingApi.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/court-case/${COURT_CASE_ID_FOR_CREATION}")))
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
            Assertions.assertThat(it["mappingType"]).isEqualTo(CourtCaseMapping.MappingType.DPS_CREATED.toString())
            Assertions.assertThat(it["courtAppearances"]).contains("dpsCourtAppearanceId=$DPS_COURT_APPEARANCE_ID")
            Assertions.assertThat(it["courtAppearances"]).contains("nomisCourtAppearanceId=$NOMIS_COURT_APPEARANCE_ID")
            Assertions.assertThat(it["courtCharges"]).contains("dpsCourtChargeId=$DPS_COURT_CHARGE_ID")
            Assertions.assertThat(it["courtCharges"]).contains("nomisCourtChargeId=$NOMIS_COURT_CHARGE_ID")
            Assertions.assertThat(it["courtCharges"]).contains("dpsCourtChargeId=$DPS_COURT_CHARGE_2_ID")
            Assertions.assertThat(it["courtCharges"]).contains("nomisCourtChargeId=$NOMIS_COURT_CHARGE_2_ID")
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
                WireMock.equalTo("A"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtId",
                WireMock.equalTo(DONCASTER_COURT_CODE),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtAppearance.courtEventType",
                WireMock.equalTo(
                  "CRT",
                ),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtAppearance.courtId",
                WireMock.equalTo(
                  DONCASTER_COURT_CODE,
                ),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtAppearance.nextCourtId",
                WireMock.equalTo(
                  DONCASTER_COURT_CODE,
                ),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtAppearance.outcomeReasonCode",
                WireMock.equalTo(
                  "4531",
                ),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtAppearance.nextEventDateTime",
                WireMock.equalTo(
                  "2024-12-10T00:00",
                ),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtAppearance.courtEventChargesToCreate[0].offenceCode",
                WireMock.equalTo(
                  "PS90037",
                ),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtAppearance.courtEventChargesToCreate[1].offenceCode",
                WireMock.equalTo(
                  "PS90090",
                ),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtAppearance.courtEventChargesToCreate[0].resultCode1",
                WireMock.equalTo(
                  "4531",
                ),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtAppearance.courtEventChargesToCreate[1].resultCode1",
                WireMock.equalTo(
                  "4531",
                ),
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
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "courtAppearances[0].nomisCourtAppearanceId",
                  WireMock.equalTo(
                    NOMIS_COURT_APPEARANCE_ID.toString(),
                  ),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "courtAppearances[0].nomisNextCourtAppearanceId",
                  WireMock.equalTo(
                    NOMIS_NEXT_COURT_APPEARANCE_ID.toString(),
                  ),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "courtCharges[0].nomisCourtChargeId",
                  WireMock.equalTo(
                    NOMIS_COURT_CHARGE_ID.toString(),
                  ),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "courtCharges[0].dpsCourtChargeId",
                  WireMock.equalTo(
                    DPS_COURT_CHARGE_ID,
                  ),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "courtCharges[1].nomisCourtChargeId",
                  WireMock.equalTo(
                    NOMIS_COURT_CHARGE_2_ID.toString(),
                  ),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "courtCharges[1].dpsCourtChargeId",
                  WireMock.equalTo(
                    DPS_COURT_CHARGE_2_ID,
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
          offenderNo = OFFENDER_NO,
          courtAppearanceId = DPS_COURT_APPEARANCE_ID,
          courtCharge1Id = DPS_COURT_CHARGE_ID,
          courtCharge2Id = DPS_COURT_CHARGE_2_ID,
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
          WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/offenderNo/$OFFENDER_NO/sentencing/court-cases")),
        )
      }
    }

    @Nested
    inner class WhenMappingServiceFailsOnce {
      @BeforeEach
      fun setUp() {
        CourtSentencingApiExtension.courtSentencingApi.stubCourtCaseGet(
          COURT_CASE_ID_FOR_CREATION,
          offenderNo = OFFENDER_NO,
          courtAppearanceId = DPS_COURT_APPEARANCE_ID,
          courtCharge1Id = DPS_COURT_CHARGE_ID,
          courtCharge2Id = DPS_COURT_CHARGE_2_ID,
        )
        NomisApiExtension.nomisApi.stubCourtCaseCreate(
          OFFENDER_NO,
          nomisCourtCaseCreateResponseWithTwoCharges(),
        )
        MappingExtension.mappingServer.stubGetCreateCaseMappingGivenDpsIdWithError(COURT_CASE_ID_FOR_CREATION, 404)
        MappingExtension.mappingServer.stubCreateCourtCaseWithErrorFollowedBySlowSuccess()
        publishCreateCourtCaseDomainEvent()

        await untilCallTo { CourtSentencingApiExtension.courtSentencingApi.getCountFor("/court-case/$COURT_CASE_ID_FOR_CREATION") } matches { it == 1 }
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
  inner class CreateCourtAppearance {
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
          nomisCourtAppearanceCreateResponseWithTwoCharges(),
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
          DPS_COURT_CHARGE_3_ID,
          NOMIS_COURT_CHARGE_3_ID,
        )
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsIdWithError(DPS_COURT_CHARGE_2_ID, 404)
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsIdWithError(DPS_COURT_CHARGE_4_ID, 404)
        publishCreateCourtAppearanceDomainEvent()
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete()
        CourtSentencingApiExtension.courtSentencingApi.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/court-appearance/${DPS_COURT_APPEARANCE_ID}")))
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
            Assertions.assertThat(it["mappingType"]).isEqualTo(CourtCaseMapping.MappingType.DPS_CREATED.toString())
            Assertions.assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            Assertions.assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
            Assertions.assertThat(it["courtCharges"])
              .contains("[CourtChargeMappingDto(nomisCourtChargeId=12, dpsCourtChargeId=9996aa44-642a-484a-a967-2d17b5c9c5a1, label=null, mappingType=DPS_CREATED, whenCreated=null), CourtChargeMappingDto(nomisCourtChargeId=14, dpsCourtChargeId=1236aa44-642a-484a-a967-2d17b5c9c5a1, label=null, mappingType=DPS_CREATED, whenCreated=null)]")
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the Court Appearance`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/court-appearances")))
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
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "courtCharges[0].nomisCourtChargeId",
                  WireMock.equalTo(
                    NOMIS_COURT_CHARGE_2_ID.toString(),
                  ),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "courtCharges[0].dpsCourtChargeId",
                  WireMock.equalTo(
                    DPS_COURT_CHARGE_2_ID,
                  ),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "courtCharges[1].nomisCourtChargeId",
                  WireMock.equalTo(
                    NOMIS_COURT_CHARGE_4_ID.toString(),
                  ),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "courtCharges[1].dpsCourtChargeId",
                  WireMock.equalTo(
                    DPS_COURT_CHARGE_4_ID,
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
          WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/offenderNo/$OFFENDER_NO/sentencing/court-appearances")),
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
          nomisCourtAppearanceCreateResponseWithTwoCharges(),
        )
        MappingExtension.mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        MappingExtension.mappingServer.stubGetCourtAppearanceMappingGivenDpsIdWithError(DPS_COURT_APPEARANCE_ID, 404)
        MappingExtension.mappingServer.stubCreateCourtAppearanceWithErrorFollowedBySlowSuccess()
        publishCreateCourtAppearanceDomainEvent()

        await untilCallTo { CourtSentencingApiExtension.courtSentencingApi.getCountFor("/court-appearance/$DPS_COURT_APPEARANCE_ID") } matches { it == 1 }
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
  }

  @Nested
  inner class UpdateCourtAppearance {
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
          nomisCourtAppearanceUpdateResponseWithTwoCreatedAndTwoDeletedCharges(),
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
        // stub two mappings for charges out of the 4 - which makes 2 creates and 2 updates
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsId(DPS_COURT_CHARGE_ID, NOMIS_COURT_CHARGE_ID)
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsId(
          DPS_COURT_CHARGE_3_ID,
          NOMIS_COURT_CHARGE_3_ID,
        )
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsIdWithError(DPS_COURT_CHARGE_2_ID, 404)
        MappingExtension.mappingServer.stubGetCourtChargeMappingGivenDpsIdWithError(DPS_COURT_CHARGE_4_ID, 404)
        MappingExtension.mappingServer.stubCourtChargeBatchUpdate()
        publishUpdateCourtAppearanceDomainEvent()
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete()
        CourtSentencingApiExtension.courtSentencingApi.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/court-appearance/${DPS_COURT_APPEARANCE_ID}")))
      }

      @Test
      fun `will map new DPS court charges to NOMIS court charges`() {
        waitForAnyProcessingToComplete()

        NomisApiExtension.nomisApi.verify(
          WireMock.putRequestedFor(WireMock.anyUrl())
            .withRequestBody(WireMock.matchingJsonPath("courtEventChargesToUpdate.size()", WireMock.equalTo("2")))
            .withRequestBody(WireMock.matchingJsonPath("courtEventChargesToCreate.size()", WireMock.equalTo("2")))
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventChargesToUpdate[0].offenceCode",
                WireMock.equalTo("$COURT_CHARGE_1_OFFENCE_CODE"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventChargesToUpdate[0].offencesCount",
                WireMock.equalTo("$OFFENCES_COUNT"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventChargesToUpdate[0].offenceEndDate",
                WireMock.equalTo("$COURT_CHARGE_1_OFFENCE_END_DATE"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventChargesToUpdate[0].offenceDate",
                WireMock.equalTo("$COURT_CHARGE_1_OFFENCE_DATE"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventChargesToUpdate[0].resultCode1",
                WireMock.equalTo(HARDCODED_REMAND_RESULT_CODE),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventChargesToUpdate[1].offenceCode",
                WireMock.equalTo("$COURT_CHARGE_3_OFFENCE_CODE"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventChargesToUpdate[1].offencesCount",
                WireMock.equalTo("$OFFENCES_COUNT"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventChargesToUpdate[1].offenceEndDate",
                WireMock.absent(),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventChargesToUpdate[1].offenceDate",
                WireMock.equalTo("$COURT_CHARGE_3_OFFENCE_DATE"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventChargesToUpdate[1].resultCode1",
                WireMock.equalTo(HARDCODED_IMPRISONMENT_RESULT_CODE),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventChargesToCreate[0].offenceCode",
                WireMock.equalTo("$COURT_CHARGE_2_OFFENCE_CODE"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventChargesToCreate[0].offencesCount",
                WireMock.equalTo("$OFFENCES_COUNT"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventChargesToCreate[0].offenceEndDate",
                WireMock.equalTo("$COURT_CHARGE_2_OFFENCE_END_DATE"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventChargesToCreate[0].offenceDate",
                WireMock.equalTo("$COURT_CHARGE_2_OFFENCE_DATE"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventChargesToCreate[0].resultCode1",
                WireMock.equalTo(HARDCODED_REMAND_RESULT_CODE),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventChargesToCreate[1].offenceCode",
                WireMock.equalTo("$COURT_CHARGE_4_OFFENCE_CODE"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventChargesToCreate[1].offencesCount",
                WireMock.equalTo("$OFFENCES_COUNT"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventChargesToCreate[1].offenceEndDate",
                WireMock.absent(),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventChargesToCreate[1].offenceDate",
                WireMock.equalTo("$COURT_CHARGE_4_OFFENCE_DATE"),
              ),
            )
            .withRequestBody(
              WireMock.matchingJsonPath(
                "courtEventChargesToCreate[1].resultCode1",
                WireMock.equalTo(HARDCODED_IMPRISONMENT_RESULT_CODE),
              ),
            ),
        )
      }

      @Test
      fun `will add and delete charge mappings as required`() {
        waitForAnyProcessingToComplete()
        await untilAsserted {
          MappingExtension.mappingServer.verify(
            WireMock.putRequestedFor(WireMock.urlEqualTo("/mapping/court-sentencing/court-charges"))
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "courtChargesToCreate[0].nomisCourtChargeId",
                  WireMock.equalTo(
                    NOMIS_COURT_CHARGE_2_ID.toString(),
                  ),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "courtChargesToCreate[0].dpsCourtChargeId",
                  WireMock.equalTo(
                    DPS_COURT_CHARGE_2_ID,
                  ),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "courtChargesToCreate[1].nomisCourtChargeId",
                  WireMock.equalTo(
                    NOMIS_COURT_CHARGE_4_ID.toString(),
                  ),
                ),
              )
              .withRequestBody(
                WireMock.matchingJsonPath(
                  "courtChargesToCreate[1].dpsCourtChargeId",
                  WireMock.equalTo(
                    DPS_COURT_CHARGE_4_ID,
                  ),
                ),
              )
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
            Assertions.assertThat(it["newCourtChargeMappings"])
              .contains("[dpsCourtChargeId: 9996aa44-642a-484a-a967-2d17b5c9c5a1, nomisCourtChargeId: 12, dpsCourtChargeId: 1236aa44-642a-484a-a967-2d17b5c9c5a1, nomisCourtChargeId: 14]")
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
          WireMock.putRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/$COURT_CASE_ID_FOR_CREATION/court-appearances/$NOMIS_COURT_APPEARANCE_ID")),
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

  private fun publishCreateCourtAppearanceDomainEvent() {
    val eventType = "court-sentencing.court-appearance.created"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          courtAppearanceMessagePayload(
            courtAppearanceId = DPS_COURT_APPEARANCE_ID,
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

  private fun publishUpdateCourtAppearanceDomainEvent() {
    val eventType = "court-sentencing.court-appearance.updated"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          courtAppearanceMessagePayload(
            courtAppearanceId = DPS_COURT_APPEARANCE_ID,
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

  fun courtCaseMessagePayload(courtCaseId: String, offenderNo: String, eventType: String, source: String = "DPS") =
    """{"eventType":"$eventType", "additionalInformation": {"courtCaseId":"$courtCaseId", "source": "$source"}, "personReference": {"identifiers":[{"type":"NOMS", "value":"$offenderNo"}]}}"""

  fun courtAppearanceMessagePayload(
    courtCaseId: String,
    courtAppearanceId: String,
    offenderNo: String,
    eventType: String,
    source: String = "DPS",
  ) =
    """{"eventType":"$eventType", "additionalInformation": {"id":"$courtAppearanceId", "courtCaseId":"$courtCaseId", "offenderNo": "$offenderNo", "source": "$source"}}"""

  fun nomisCourtCaseCreateResponseWithTwoCharges(): String {
    return """{ "id": $NOMIS_COURT_CASE_ID_FOR_CREATION, "courtAppearanceIds": [{"id": $NOMIS_COURT_APPEARANCE_ID, "nextCourtAppearanceId": $NOMIS_NEXT_COURT_APPEARANCE_ID, "courtEventChargesIds": [{"offenderChargeId": $NOMIS_COURT_CHARGE_ID }, {"offenderChargeId": $NOMIS_COURT_CHARGE_2_ID }] }] }"""
  }

  fun nomisCourtAppearanceCreateResponseWithTwoCharges(): String {
    return """{ "id": $NOMIS_COURT_APPEARANCE_ID, "nextCourtAppearanceId": $NOMIS_NEXT_COURT_APPEARANCE_ID, 
      |"courtEventChargesIds": [
      |{"offenderChargeId": $NOMIS_COURT_CHARGE_2_ID },
      |{"offenderChargeId": $NOMIS_COURT_CHARGE_4_ID }
      |] }
    """.trimMargin()
  }

  fun nomisCourtAppearanceUpdateResponseWithTwoCreatedAndTwoDeletedCharges(): String {
    return """{ "id": $NOMIS_COURT_APPEARANCE_ID, "nextCourtAppearanceId": $NOMIS_NEXT_COURT_APPEARANCE_ID, 
      |"createdCourtEventChargesIds": [
      |{"offenderChargeId": $NOMIS_COURT_CHARGE_2_ID },
      |{"offenderChargeId": $NOMIS_COURT_CHARGE_4_ID }
      |],
      |"deletedOffenderChargesIds": [
      |{"offenderChargeId": $NOMIS_COURT_CHARGE_5_ID },
      |{"offenderChargeId": $NOMIS_COURT_CHARGE_6_ID }
      |]
      | }
    """.trimMargin()
  }
}
