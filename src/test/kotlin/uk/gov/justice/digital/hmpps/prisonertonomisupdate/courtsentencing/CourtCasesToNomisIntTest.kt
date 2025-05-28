package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock
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
import org.mockito.ArgumentMatchers.eq
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.isNull
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CaseReferenceLegacyData
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyRecall
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtAppearanceMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseAllMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtChargeMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.SentenceMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.SentenceTermMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCourtAppearanceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateSentenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateSentenceTermResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderChargeIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCourtAppearanceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.BOOKING_ID
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.COURT_CHARGE_1_OFFENCE_CODE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.COURT_CHARGE_1_OFFENCE_DATE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.COURT_CHARGE_1_OFFENCE_END_DATE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.COURT_CHARGE_1_RESULT_CODE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.CourtSentencingApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.CourtSentencingApiExtension.Companion.legacySentence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
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

  @Autowired
  private lateinit var courtSentencingNomisApi: CourtSentencingNomisApiMockServer

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
        courtSentencingNomisApi.stubCourtCaseCreate(
          OFFENDER_NO,
          CreateCourtCaseResponse(
            id = NOMIS_COURT_CASE_ID_FOR_CREATION,
            courtAppearanceIds = emptyList(),
          ),
        )
        mappingServer.stubGetCaseMappingGivenDpsIdWithError(COURT_CASE_ID_FOR_CREATION, 404)
        mappingServer.stubCreateCourtCase()
        publishCreateCourtCaseDomainEvent()
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete()
        CourtSentencingApiExtension.courtSentencingApi.verify(getRequestedFor(urlEqualTo("/legacy/court-case/${COURT_CASE_ID_FOR_CREATION}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("court-case-create-success"),
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["mappingType"]).isEqualTo(CourtCaseAllMappingDto.MappingType.DPS_CREATED.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the Court Case`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(
          postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases"))
            .withRequestBody(
              matchingJsonPath(
                "status",
                equalTo("A"),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "legalCaseType",
                equalTo("NE"),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "startDate",
                equalTo("2024-01-01"),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "caseReference",
                equalTo(CASE_REFERENCE),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "courtId",
                equalTo(DONCASTER_COURT_CODE),
              ),
            ),

        )
      }

      @Test
      fun `will create a mapping between the two court cases`() {
        waitForAnyProcessingToComplete()

        await untilAsserted {
          mappingServer.verify(
            postRequestedFor(urlEqualTo("/mapping/court-sentencing/court-cases"))
              .withRequestBody(
                matchingJsonPath(
                  "dpsCourtCaseId",
                  equalTo(COURT_CASE_ID_FOR_CREATION),
                ),
              )
              .withRequestBody(
                matchingJsonPath(
                  "nomisCourtCaseId",
                  equalTo(
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
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["reason"]).isEqualTo("Court case created in NOMIS")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenMappingAlreadyCreatedForCourtCase {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
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
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          },
          isNull(),
        )

        NomisApiExtension.nomisApi.verify(
          0,
          postRequestedFor(WireMock.anyUrl()),
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
        courtSentencingNomisApi.stubCourtCaseCreate(
          OFFENDER_NO,
          CreateCourtCaseResponse(
            id = NOMIS_COURT_CASE_ID_FOR_CREATION,
            courtAppearanceIds = emptyList(),
          ),
        )
        mappingServer.stubGetCaseMappingGivenDpsIdWithError(COURT_CASE_ID_FOR_CREATION, 404)
        mappingServer.stubCreateCourtCaseWithErrorFollowedBySlowSuccess()
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
          postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases")),
        )
      }

      @Test
      fun `will eventually create a mapping after NOMIS court case is created`() {
        await untilAsserted {
          mappingServer.verify(
            2,
            postRequestedFor(urlEqualTo("/mapping/court-sentencing/court-cases"))
              .withRequestBody(
                matchingJsonPath(
                  "dpsCourtCaseId",
                  equalTo(COURT_CASE_ID_FOR_CREATION),
                ),
              )
              .withRequestBody(
                matchingJsonPath(
                  "nomisCourtCaseId",
                  equalTo(
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
        courtSentencingNomisApi.stubCourtCaseDelete(
          offenderNo = OFFENDER_NO,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,

        )
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        mappingServer.stubDeleteCourtCase(id = COURT_CASE_ID_FOR_CREATION)
        publishDeleteCourtCaseDomainEvent()
      }

      @Test
      fun `will create success telemetry`() {
        waitForHearingProcessingToBeComplete()

        verify(telemetryClient).trackEvent(
          org.mockito.kotlin.eq("court-case-deleted-success"),
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to delete the court case`() {
        waitForHearingProcessingToBeComplete()
        NomisApiExtension.nomisApi.verify(WireMock.deleteRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/$NOMIS_COURT_CASE_ID_FOR_CREATION")))
      }

      @Test
      fun `will call the mapping service to delete the mapping`() {
        waitForHearingProcessingToBeComplete()
        mappingServer.verify(WireMock.deleteRequestedFor(urlEqualTo("/mapping/court-sentencing/court-cases/dps-court-case-id/$COURT_CASE_ID_FOR_CREATION")))
      }
    }

    @Nested
    inner class WhenNoMappingExistsForCase {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetCaseMappingGivenDpsIdWithError(COURT_CASE_ID_FOR_CREATION, 404)
        publishDeleteCourtCaseDomainEvent()
      }

      @Test
      fun `will not attempt to delete a court case in NOMIS`() {
        await untilAsserted {
          verify(telemetryClient, times(1)).trackEvent(
            org.mockito.kotlin.eq("court-case-deleted-skipped"),
            check {
              assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
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
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
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
        courtSentencingNomisApi.stubCourtAppearanceCreate(
          OFFENDER_NO,
          NOMIS_COURT_CASE_ID_FOR_CREATION,
          nomisCourtAppearanceCreateResponse(),
        )
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )

        mappingServer.stubGetCourtAppearanceMappingGivenDpsIdWithError(DPS_COURT_APPEARANCE_ID, 404)
        mappingServer.stubCreateCourtAppearance()
        // stub two mappings for charges out of the 4 - which makes 2 creates and 2 updates
        mappingServer.stubGetCourtChargeMappingGivenDpsId(DPS_COURT_CHARGE_ID, NOMIS_COURT_CHARGE_ID)
        mappingServer.stubGetCourtChargeMappingGivenDpsId(
          DPS_COURT_CHARGE_2_ID,
          NOMIS_COURT_CHARGE_2_ID,
        )
        mappingServer.stubGetCourtChargeMappingGivenDpsId(
          DPS_COURT_CHARGE_3_ID,
          NOMIS_COURT_CHARGE_3_ID,
        )
        mappingServer.stubGetCourtChargeMappingGivenDpsId(
          DPS_COURT_CHARGE_4_ID,
          NOMIS_COURT_CHARGE_4_ID,
        )

        publishCreateCourtAppearanceDomainEvent()
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete()
        CourtSentencingApiExtension.courtSentencingApi.verify(getRequestedFor(urlEqualTo("/legacy/court-appearance/${DPS_COURT_APPEARANCE_ID}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("court-appearance-create-success"),
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["mappingType"]).isEqualTo(CourtAppearanceMappingDto.MappingType.DPS_CREATED.toString())
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            assertThat(it["courtEventCharges"])
              .isEqualTo("[$NOMIS_COURT_CHARGE_ID, $NOMIS_COURT_CHARGE_2_ID, $NOMIS_COURT_CHARGE_3_ID, $NOMIS_COURT_CHARGE_4_ID]")
            assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the Court Appearance`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(
          postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/court-appearances"))
            .withRequestBody(
              matchingJsonPath(
                "eventDateTime",
                equalTo("2024-09-23T10:00:00"),
              ),
            )
            .withRequestBody(matchingJsonPath("courtEventCharges.size()", equalTo("4")))
            .withRequestBody(
              matchingJsonPath(
                "courtEventCharges[0]",
                equalTo(NOMIS_COURT_CHARGE_ID.toString()),
              ),

            )
            .withRequestBody(
              matchingJsonPath(
                "courtEventCharges[1]",
                equalTo(NOMIS_COURT_CHARGE_2_ID.toString()),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "courtEventCharges[2]",
                equalTo(NOMIS_COURT_CHARGE_3_ID.toString()),
              ),

            )
            .withRequestBody(
              matchingJsonPath(
                "courtEventCharges[3]",
                equalTo(NOMIS_COURT_CHARGE_4_ID.toString()),
              ),
            ),
        )
      }

      @Test
      fun `will create a mapping between the two court appearances`() {
        waitForAnyProcessingToComplete()

        await untilAsserted {
          mappingServer.verify(
            postRequestedFor(urlEqualTo("/mapping/court-sentencing/court-appearances"))
              .withRequestBody(
                matchingJsonPath(
                  "dpsCourtAppearanceId",
                  equalTo(DPS_COURT_APPEARANCE_ID),
                ),
              )
              .withRequestBody(
                matchingJsonPath(
                  "nomisCourtAppearanceId",
                  equalTo(
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
        mappingServer.stubGetCourtCaseMappingGivenDpsId(COURT_CASE_ID_FOR_CREATION)
        mappingServer.stubGetCourtAppearanceMappingGivenDpsId(
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
          check {
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          },
          isNull(),
        )

        NomisApiExtension.nomisApi.verify(
          0,
          postRequestedFor(WireMock.anyUrl()),
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
        courtSentencingNomisApi.stubCourtAppearanceCreate(
          OFFENDER_NO,
          NOMIS_COURT_CASE_ID_FOR_CREATION,
          nomisCourtAppearanceCreateResponse(),
        )
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        mappingServer.stubGetCourtAppearanceMappingGivenDpsIdWithError(DPS_COURT_APPEARANCE_ID, 404)
        mappingServer.stubCreateCourtAppearanceWithErrorFollowedBySlowSuccess()

        mappingServer.stubGetCourtChargeMappingGivenDpsId(DPS_COURT_CHARGE_ID, NOMIS_COURT_CHARGE_ID)
        mappingServer.stubGetCourtChargeMappingGivenDpsId(
          DPS_COURT_CHARGE_2_ID,
          NOMIS_COURT_CHARGE_2_ID,
        )
        mappingServer.stubGetCourtChargeMappingGivenDpsId(
          DPS_COURT_CHARGE_3_ID,
          NOMIS_COURT_CHARGE_3_ID,
        )
        mappingServer.stubGetCourtChargeMappingGivenDpsId(
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
          postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/court-appearances")),
        )
      }

      @Test
      fun `will eventually create a mapping after NOMIS court appearance is created`() {
        await untilAsserted {
          mappingServer.verify(
            2,
            postRequestedFor(urlEqualTo("/mapping/court-sentencing/court-appearances"))
              .withRequestBody(
                matchingJsonPath(
                  "dpsCourtAppearanceId",
                  equalTo(DPS_COURT_APPEARANCE_ID),
                ),
              )
              .withRequestBody(
                matchingJsonPath(
                  "nomisCourtAppearanceId",
                  equalTo(
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
        courtSentencingNomisApi.stubCourtAppearanceCreate(
          OFFENDER_NO,
          NOMIS_COURT_CASE_ID_FOR_CREATION,
          nomisCourtAppearanceCreateResponse(),
        )
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        mappingServer.stubGetCourtAppearanceMappingGivenDpsIdWithError(DPS_COURT_APPEARANCE_ID, 404)
        mappingServer.stubCreateCourtAppearance()

        // a parent entity has initially not been created but then is available on retry
        mappingServer.stubGetCourtChargeNotFoundFollowedBySlowSuccess(
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
          postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/court-appearances")),
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
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
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
        courtSentencingNomisApi.stubCourtAppearanceUpdate(
          OFFENDER_NO,
          NOMIS_COURT_CASE_ID_FOR_CREATION,
          NOMIS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceUpdateResponseWithTwoDeletedCharges(),
        )
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )

        mappingServer.stubGetCourtAppearanceMappingGivenDpsId(
          DPS_COURT_APPEARANCE_ID,
          NOMIS_COURT_APPEARANCE_ID,
        )
        mappingServer.stubCreateCourtAppearance()
        // mappings found for all 4 charges
        mappingServer.stubGetCourtChargeMappingGivenDpsId(DPS_COURT_CHARGE_ID, NOMIS_COURT_CHARGE_ID)
        mappingServer.stubGetCourtChargeMappingGivenDpsId(
          DPS_COURT_CHARGE_2_ID,
          NOMIS_COURT_CHARGE_2_ID,
        )
        mappingServer.stubGetCourtChargeMappingGivenDpsId(
          DPS_COURT_CHARGE_3_ID,
          NOMIS_COURT_CHARGE_3_ID,
        )
        mappingServer.stubGetCourtChargeMappingGivenDpsId(
          DPS_COURT_CHARGE_4_ID,
          NOMIS_COURT_CHARGE_4_ID,
        )
        mappingServer.stubCourtChargeBatchUpdate()
        publishUpdateCourtAppearanceDomainEvent()
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete()
        CourtSentencingApiExtension.courtSentencingApi.verify(getRequestedFor(urlEqualTo("/legacy/court-appearance/${DPS_COURT_APPEARANCE_ID}")))
      }

      @Test
      fun `will map new DPS court charges to NOMIS court charges`() {
        waitForAnyProcessingToComplete()

        NomisApiExtension.nomisApi.verify(
          WireMock.putRequestedFor(WireMock.anyUrl())
            .withRequestBody(matchingJsonPath("courtEventCharges.size()", equalTo("4")))
            .withRequestBody(
              matchingJsonPath(
                "courtEventCharges[0]",
                equalTo(NOMIS_COURT_CHARGE_ID.toString()),
              ),

            )
            .withRequestBody(
              matchingJsonPath(
                "courtEventCharges[1]",
                equalTo(NOMIS_COURT_CHARGE_2_ID.toString()),
              ),
            ),
        )
      }

      @Test
      fun `will delete charge mappings as required`() {
        waitForAnyProcessingToComplete()
        await untilAsserted {
          mappingServer.verify(
            WireMock.putRequestedFor(urlEqualTo("/mapping/court-sentencing/court-charges"))
              .withRequestBody(
                matchingJsonPath(
                  "courtChargesToDelete[0].nomisCourtChargeId",
                  equalTo(
                    NOMIS_COURT_CHARGE_5_ID.toString(),
                  ),
                ),
              )
              .withRequestBody(
                matchingJsonPath(
                  "courtChargesToDelete[1].nomisCourtChargeId",
                  equalTo(
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
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
            assertThat(it["deletedCourtChargeMappings"])
              .contains("[nomisCourtChargeId: 15, nomisCourtChargeId: 16]")
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to update the Court Appearance`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(WireMock.putRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/court-appearances/${NOMIS_COURT_APPEARANCE_ID}")))
      }
    }

    @Nested
    inner class WhenMappingDoesntExistForCourtAppearance {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetCourtCaseMappingGivenDpsId(COURT_CASE_ID_FOR_CREATION)
        mappingServer.stubGetCourtAppearanceMappingGivenDpsIdWithError(
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
            check {
              assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
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
        courtSentencingNomisApi.stubCourtAppearanceDelete(
          offenderNo = OFFENDER_NO,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          nomisEventId = NOMIS_COURT_APPEARANCE_ID,
        )
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        mappingServer.stubGetCourtAppearanceMappingGivenDpsId(
          id = DPS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )
        mappingServer.stubDeleteCourtAppearance(id = DPS_COURT_APPEARANCE_ID)
        publishDeleteCourtAppearanceDomainEvent()
      }

      @Test
      fun `will create success telemetry`() {
        waitForHearingProcessingToBeComplete()

        verify(telemetryClient).trackEvent(
          org.mockito.kotlin.eq("court-appearance-deleted-success"),
          check {
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            assertThat(it["nomisCourtAppearanceId"]).isEqualTo(NOMIS_COURT_APPEARANCE_ID.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to delete the court appearance`() {
        waitForHearingProcessingToBeComplete()
        NomisApiExtension.nomisApi.verify(WireMock.deleteRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/$NOMIS_COURT_CASE_ID_FOR_CREATION/court-appearances/$NOMIS_COURT_APPEARANCE_ID")))
      }

      @Test
      fun `will call the mapping service to delete the mapping`() {
        waitForHearingProcessingToBeComplete()
        mappingServer.verify(WireMock.deleteRequestedFor(urlEqualTo("/mapping/court-sentencing/court-appearances/dps-court-appearance-id/$DPS_COURT_APPEARANCE_ID")))
      }
    }

    @Nested
    inner class WhenNoMappingExistsForAppearance {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetCourtAppearanceMappingGivenDpsIdWithError(DPS_COURT_APPEARANCE_ID, 404)
        publishDeleteCourtAppearanceDomainEvent()
      }

      @Test
      fun `will not attempt to delete a court appearance in NOMIS`() {
        await untilAsserted {
          verify(telemetryClient, times(1)).trackEvent(
            org.mockito.kotlin.eq("court-appearance-deleted-skipped"),
            check {
              assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
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
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["dpsChargeId"]).isEqualTo(DPS_COURT_CHARGE_ID)
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
        courtSentencingNomisApi.stubCourtChargeCreate(
          OFFENDER_NO,
          NOMIS_COURT_CASE_ID_FOR_CREATION,
          nomisCourtChargeCreateResponse(),
        )
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )

        mappingServer.stubGetCourtChargeMappingGivenDpsIdWithError(DPS_COURT_CHARGE_ID, 404)
        mappingServer.stubCreateCourtCharge()

        publishCreateCourtChargeDomainEvent()
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete()
        CourtSentencingApiExtension.courtSentencingApi.verify(getRequestedFor(urlEqualTo("/legacy/charge/${DPS_COURT_CHARGE_ID}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("charge-create-success"),
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["mappingType"]).isEqualTo(CourtChargeMappingDto.MappingType.DPS_CREATED.toString())
            assertThat(it["dpsChargeId"]).isEqualTo(DPS_COURT_CHARGE_ID)
            assertThat(it["nomisChargeId"]).isEqualTo(NOMIS_COURT_CHARGE_ID.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the Charge`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/charges")))
      }

      @Test
      fun `will create a mapping between the two charges`() {
        waitForAnyProcessingToComplete()

        await untilAsserted {
          mappingServer.verify(
            postRequestedFor(urlEqualTo("/mapping/court-sentencing/court-charges"))
              .withRequestBody(
                matchingJsonPath(
                  "dpsCourtChargeId",
                  equalTo(DPS_COURT_CHARGE_ID),
                ),
              )
              .withRequestBody(
                matchingJsonPath(
                  "nomisCourtChargeId",
                  equalTo(
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
        mappingServer.stubGetCourtCaseMappingGivenDpsId(COURT_CASE_ID_FOR_CREATION)
        mappingServer.stubGetCourtChargeMappingGivenDpsId(
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
          check {
            assertThat(it["dpsChargeId"]).isEqualTo(DPS_COURT_CHARGE_ID)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          },
          isNull(),
        )

        NomisApiExtension.nomisApi.verify(
          0,
          postRequestedFor(WireMock.anyUrl()),
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
        courtSentencingNomisApi.stubCourtChargeCreate(
          OFFENDER_NO,
          NOMIS_COURT_CASE_ID_FOR_CREATION,
          nomisCourtChargeCreateResponse(),
        )
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        mappingServer.stubGetCourtChargeMappingGivenDpsIdWithError(DPS_COURT_CHARGE_ID, 404)
        mappingServer.stubCreateCourtChargeWithErrorFollowedBySlowSuccess()
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
          postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/charges")),
        )
      }

      @Test
      fun `will eventually create a mapping after NOMIS court charge is created`() {
        await untilAsserted {
          mappingServer.verify(
            2,
            postRequestedFor(urlEqualTo("/mapping/court-sentencing/court-charges"))
              .withRequestBody(
                matchingJsonPath(
                  "dpsCourtChargeId",
                  equalTo(DPS_COURT_CHARGE_ID),
                ),
              )
              .withRequestBody(
                matchingJsonPath(
                  "nomisCourtChargeId",
                  equalTo(
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
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["dpsChargeId"]).isEqualTo(DPS_COURT_CHARGE_ID)
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
        courtSentencingNomisApi.stubCourtChargeUpdate(
          offenderChargeId = NOMIS_COURT_CHARGE_ID,
          offenderNo = OFFENDER_NO,
          courtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          courtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )

        mappingServer.stubGetCourtAppearanceMappingGivenDpsId(
          id = DPS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )

        mappingServer.stubGetCourtChargeMappingGivenDpsId(
          id = DPS_COURT_CHARGE_ID,
          nomisCourtChargeId = NOMIS_COURT_CHARGE_ID,
        )

        publishUpdatedCourtChargeDomainEvent()
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete()
        CourtSentencingApiExtension.courtSentencingApi.verify(getRequestedFor(urlEqualTo("/legacy/charge/${DPS_COURT_CHARGE_ID}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("charge-updated-success"),
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["dpsChargeId"]).isEqualTo(DPS_COURT_CHARGE_ID)
            assertThat(it["nomisChargeId"]).isEqualTo(NOMIS_COURT_CHARGE_ID.toString())
            assertThat(it["nomisOutcomeCode"]).isEqualTo(COURT_CHARGE_1_RESULT_CODE)
            assertThat(it["nomisOffenceCode"]).isEqualTo(COURT_CHARGE_1_OFFENCE_CODE)
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to update the Charge`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(
          WireMock.putRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/$NOMIS_COURT_CASE_ID_FOR_CREATION/court-appearances/$NOMIS_COURT_APPEARANCE_ID/charges/$NOMIS_COURT_CHARGE_ID"))
            .withRequestBody(
              matchingJsonPath(
                "offenceCode",
                equalTo(COURT_CHARGE_1_OFFENCE_CODE),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "offenceEndDate",
                equalTo(COURT_CHARGE_1_OFFENCE_END_DATE),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "offenceDate",
                equalTo(COURT_CHARGE_1_OFFENCE_DATE),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "resultCode1",
                equalTo(COURT_CHARGE_1_RESULT_CODE),
              ),
            ),
        )
      }
    }

    @Nested
    inner class WhenCourtChargeMappingDoesNotExist {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetCourtCaseMappingGivenDpsId(COURT_CASE_ID_FOR_CREATION)
        mappingServer.stubGetCourtAppearanceMappingGivenDpsId(DPS_COURT_APPEARANCE_ID)
        mappingServer.stubGetCourtChargeMappingGivenDpsIdWithError(
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
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
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
        courtSentencingNomisApi.stubSentenceCreate(
          offenderNo = OFFENDER_NO,
          caseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          nomisSentenceCreateResponseWithOneTerm(),
        )
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        mappingServer.stubGetCourtChargeMappingGivenDpsId(
          id = DPS_COURT_CHARGE_ID,
          nomisCourtChargeId = NOMIS_COURT_CHARGE_ID,
        )
        mappingServer.stubGetCourtAppearanceMappingGivenDpsId(
          id = DPS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )

        mappingServer.stubGetSentenceMappingGivenDpsIdWithError(DPS_SENTENCE_ID, 404)
        mappingServer.stubCreateSentence()

        publishCreateSentenceDomainEvent()
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete()
        CourtSentencingApiExtension.courtSentencingApi.verify(getRequestedFor(urlEqualTo("/legacy/sentence/${DPS_SENTENCE_ID}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("sentence-create-success"),
          check {
            assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            assertThat(it["dpsChargeId"]).isEqualTo(DPS_COURT_CHARGE_ID)
            assertThat(it["nomisChargeId"]).isEqualTo(NOMIS_COURT_CHARGE_ID.toString())
            assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["mappingType"]).isEqualTo(SentenceMappingDto.MappingType.DPS_CREATED.toString())
            assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
            assertThat(it["nomisSentenceSeq"]).isEqualTo(NOMIS_SENTENCE_SEQ.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the Sentence`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(
          postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/court-cases/$NOMIS_COURT_CASE_ID_FOR_CREATION/sentences"))
            .withRequestBody(
              matchingJsonPath(
                "offenderChargeIds[0]",
                equalTo(NOMIS_COURT_CHARGE_ID.toString()),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "sentenceLevel",
                equalTo(SENTENCE_LEVEL_IND),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "fine",
                equalTo(FINE_AMOUNT),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "startDate",
                equalTo("2024-01-01"),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "status",
                equalTo("A"),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "eventId",
                equalTo(NOMIS_COURT_APPEARANCE_ID.toString()),
              ),
            ),
        )
      }

      @Test
      fun `will create a mapping between the two sentences`() {
        waitForAnyProcessingToComplete()

        await untilAsserted {
          mappingServer.verify(
            postRequestedFor(urlEqualTo("/mapping/court-sentencing/sentences"))
              .withRequestBody(
                matchingJsonPath(
                  "dpsSentenceId",
                  equalTo(DPS_SENTENCE_ID),
                ),
              )
              .withRequestBody(
                matchingJsonPath(
                  "nomisSentenceSequence",
                  equalTo(
                    NOMIS_SENTENCE_SEQ.toString(),
                  ),
                ),
              )
              .withRequestBody(
                matchingJsonPath(
                  "nomisBookingId",
                  equalTo(
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
        courtSentencingNomisApi.stubSentenceCreate(
          offenderNo = OFFENDER_NO,
          caseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          nomisSentenceCreateResponseWithOneTerm(),
        )
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        mappingServer.stubGetCourtAppearanceMappingGivenDpsId(
          id = DPS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )
        mappingServer.stubGetCourtChargeMappingGivenDpsId(
          id = DPS_COURT_CHARGE_ID,
          nomisCourtChargeId = NOMIS_COURT_CHARGE_ID,
        )

        mappingServer.stubGetSentenceMappingGivenDpsIdWithError(DPS_SENTENCE_ID, 404)
        mappingServer.stubGetSentenceMappingGivenDpsId(
          id = DPS_SENTENCE_ID_2,
          nomisSentenceSequence = NOMIS_SENTENCE_SEQ_2,
          nomisBookingId = NOMIS_BOOKING_ID,
        )
        mappingServer.stubCreateSentence()

        publishCreateSentenceDomainEvent()
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("sentence-create-success"),
          check {
            assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            assertThat(it["dpsChargeId"]).isEqualTo(DPS_COURT_CHARGE_ID)
            assertThat(it["nomisChargeId"]).isEqualTo(NOMIS_COURT_CHARGE_ID.toString())
            assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["mappingType"]).isEqualTo(SentenceMappingDto.MappingType.DPS_CREATED.toString())
            assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
            assertThat(it["nomisSentenceSeq"]).isEqualTo(NOMIS_SENTENCE_SEQ.toString())
            assertThat(it["dpsConsecutiveSentenceId"]).isEqualTo(DPS_SENTENCE_ID_2)
            assertThat(it["nomisConsecutiveSentenceSequence"]).isEqualTo(NOMIS_SENTENCE_SEQ_2.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the Sentence`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(
          postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/court-cases/$NOMIS_COURT_CASE_ID_FOR_CREATION/sentences"))
            .withRequestBody(
              matchingJsonPath(
                "offenderChargeIds[0]",
                equalTo(NOMIS_COURT_CHARGE_ID.toString()),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "sentenceLevel",
                equalTo(SENTENCE_LEVEL_IND),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "fine",
                equalTo(FINE_AMOUNT),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "startDate",
                equalTo("2024-01-01"),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "status",
                equalTo("A"),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "eventId",
                equalTo(NOMIS_COURT_APPEARANCE_ID.toString()),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "consecutiveToSentenceSeq",
                equalTo("$NOMIS_SENTENCE_SEQ_2"),
              ),
            ),
        )
      }
    }

    @Nested
    inner class WhenMappingAlreadyCreatedForSentence {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetCourtCaseMappingGivenDpsId(COURT_CASE_ID_FOR_CREATION)
        mappingServer.stubGetSentenceMappingGivenDpsId(
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
          check {
            assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          },
          isNull(),
        )

        NomisApiExtension.nomisApi.verify(
          0,
          postRequestedFor(WireMock.anyUrl()),
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
        courtSentencingNomisApi.stubSentenceCreate(
          offenderNo = OFFENDER_NO,
          caseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          nomisSentenceCreateResponseWithOneTerm(),
        )
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        mappingServer.stubGetCourtChargeMappingGivenDpsId(
          id = DPS_COURT_CHARGE_ID,
          nomisCourtChargeId = NOMIS_COURT_CHARGE_ID,
        )
        mappingServer.stubGetCourtAppearanceMappingGivenDpsId(
          id = DPS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )

        mappingServer.stubGetSentenceMappingGivenDpsIdWithError(DPS_SENTENCE_ID, 404)
        mappingServer.stubCreateSentenceWithErrorFollowedBySlowSuccess()

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
          postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/sentences")),
        )
      }

      @Test
      fun `will eventually create a mapping after NOMIS sentence is created`() {
        await untilAsserted {
          mappingServer.verify(
            2,
            postRequestedFor(urlEqualTo("/mapping/court-sentencing/sentences"))
              .withRequestBody(
                matchingJsonPath(
                  "dpsSentenceId",
                  equalTo(DPS_SENTENCE_ID),
                ),
              )
              .withRequestBody(
                matchingJsonPath(
                  "nomisSentenceSequence",
                  equalTo(
                    NOMIS_SENTENCE_SEQ.toString(),
                  ),
                ),
              )
              .withRequestBody(
                matchingJsonPath(
                  "nomisBookingId",
                  equalTo(
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
        courtSentencingNomisApi.stubSentenceCreate(
          offenderNo = OFFENDER_NO,
          caseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          nomisSentenceCreateResponseWithOneTerm(),
        )
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        mappingServer.stubGetCourtAppearanceMappingGivenDpsId(
          id = DPS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )
        mappingServer.stubGetSentenceMappingGivenDpsIdWithError(DPS_SENTENCE_ID, 404)
        mappingServer.stubCreateSentence()

        // a parent entity has initially not been created but then is available on retry
        mappingServer.stubGetCourtChargeNotFoundFollowedBySlowSuccess(
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
          postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/sentences")),
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
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
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
        courtSentencingNomisApi.stubSentenceUpdate(
          offenderNo = OFFENDER_NO,
          caseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          sentenceSeq = NOMIS_SENTENCE_SEQ,
        )
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        mappingServer.stubGetCourtAppearanceMappingGivenDpsId(
          id = DPS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )

        mappingServer.stubGetSentenceMappingGivenDpsId(
          id = DPS_SENTENCE_ID,
          nomisSentenceSequence = NOMIS_SENTENCE_SEQ,
          nomisBookingId = NOMIS_BOOKING_ID,
        )

        mappingServer.stubGetCourtChargeMappingGivenDpsId(DPS_COURT_CHARGE_ID, NOMIS_COURT_CHARGE_ID)

        publishUpdateSentenceDomainEvent()
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete()
        CourtSentencingApiExtension.courtSentencingApi.verify(getRequestedFor(urlEqualTo("/legacy/sentence/${DPS_SENTENCE_ID}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("sentence-updated-success"),
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
            assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            assertThat(it["nomisSentenceSeq"]).isEqualTo(NOMIS_SENTENCE_SEQ.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to update the Sentence`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(WireMock.putRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/sentences/${NOMIS_SENTENCE_SEQ}")))
      }
    }

    @Nested
    inner class WhenMappingDoesntExistForSentence {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetCourtCaseMappingGivenDpsId(COURT_CASE_ID_FOR_CREATION)
        mappingServer.stubGetSentenceMappingGivenDpsIdWithError(
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
            check {
              assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
              assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
              assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
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

        mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )

        mappingServer.stubGetCourtAppearanceMappingGivenDpsId(
          id = DPS_COURT_APPEARANCE_ID,
          nomisCourtAppearanceId = NOMIS_COURT_APPEARANCE_ID,
        )

        mappingServer.stubGetSentenceMappingGivenDpsId(
          id = DPS_SENTENCE_ID,
          nomisSentenceSequence = NOMIS_SENTENCE_SEQ,
          nomisBookingId = NOMIS_BOOKING_ID,
        )

        mappingServer.stubGetCourtChargeMappingGivenDpsIdWithError(DPS_COURT_CHARGE_ID, 404)

        publishUpdateSentenceDomainEvent()
      }

      @Test
      fun `will create failed telemetry`() {
        waitForAnyProcessingToComplete()

        await untilAsserted {
          verify(telemetryClient, times(3)).trackEvent(
            eq("sentence-updated-failed"),
            check {
              assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
              assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
              assertThat(it["reason"])
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
        courtSentencingNomisApi.stubSentenceDelete(
          offenderNo = OFFENDER_NO,
          caseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          sentenceSeq = NOMIS_SENTENCE_SEQ,
        )
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        mappingServer.stubGetSentenceMappingGivenDpsId(
          id = DPS_SENTENCE_ID,
          nomisSentenceSequence = NOMIS_SENTENCE_SEQ,
          nomisBookingId = NOMIS_BOOKING_ID,
        )
        mappingServer.stubDeleteSentence(id = DPS_SENTENCE_ID)
        publishDeleteSentenceDomainEvent()
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          org.mockito.kotlin.eq("sentence-deleted-success"),
          check {
            assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            assertThat(it["nomisSentenceSeq"]).isEqualTo(NOMIS_SENTENCE_SEQ.toString())
            assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to delete the sentence`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(WireMock.deleteRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/court-cases/$NOMIS_COURT_CASE_ID_FOR_CREATION/sentences/$NOMIS_SENTENCE_SEQ")))
      }

      @Test
      fun `will call the mapping service to delete the mapping`() {
        waitForAnyProcessingToComplete()
        mappingServer.verify(WireMock.deleteRequestedFor(urlEqualTo("/mapping/court-sentencing/sentences/dps-sentence-id/$DPS_SENTENCE_ID")))
      }
    }

    @Nested
    inner class WhenNoMappingExistsForSentence {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetSentenceMappingGivenDpsIdWithError(DPS_SENTENCE_ID, 404)
        publishDeleteSentenceDomainEvent()
      }

      @Test
      fun `will not attempt to delete a sentence in NOMIS`() {
        await untilAsserted {
          verify(telemetryClient, times(1)).trackEvent(
            org.mockito.kotlin.eq("sentence-deleted-skipped"),
            check {
              assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
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
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
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
        courtSentencingNomisApi.stubSentenceTermCreate(
          offenderNo = OFFENDER_NO,
          caseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          sentenceSeq = NOMIS_SENTENCE_SEQ,
          response = CreateSentenceTermResponse(sentenceSeq = NOMIS_SENTENCE_SEQ, termSeq = NOMIS_TERM_SEQ, bookingId = NOMIS_BOOKING_ID),
        )
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )

        mappingServer.stubGetSentenceMappingGivenDpsId(
          id = DPS_SENTENCE_ID,
          nomisSentenceSequence = NOMIS_SENTENCE_SEQ,
          nomisBookingId = NOMIS_BOOKING_ID,
        )

        mappingServer.stubGetSentenceTermMappingGivenDpsIdWithError(DPS_TERM_ID, 404)
        mappingServer.stubCreateSentenceTerm()

        publishCreatePeriodLengthDomainEvent()
      }

      @Test
      fun `will callback back to court sentencing service to get more details`() {
        waitForAnyProcessingToComplete()
        CourtSentencingApiExtension.courtSentencingApi.verify(getRequestedFor(urlEqualTo("/legacy/period-length/${DPS_TERM_ID}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("sentence-term-create-success"),
          check {
            assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["mappingType"]).isEqualTo(SentenceTermMappingDto.MappingType.DPS_CREATED.toString())
            assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
            assertThat(it["nomisSentenceSeq"]).isEqualTo(NOMIS_SENTENCE_SEQ.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to create the Sentence Term`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(
          postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/court-cases/$NOMIS_COURT_CASE_ID_FOR_CREATION/sentences/$NOMIS_SENTENCE_SEQ/sentence-terms"))
            .withRequestBody(
              matchingJsonPath(
                "weeks",
                equalTo("4"),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "years",
                equalTo("2"),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "months",
                equalTo("6"),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "days",
                equalTo("15"),
              ),
            )
            .withRequestBody(
              matchingJsonPath(
                "sentenceTermType",
                equalTo("TERM"),
              ),
            ),
        )
      }

      @Test
      fun `will create a mapping between the two sentences`() {
        waitForAnyProcessingToComplete()

        await untilAsserted {
          mappingServer.verify(
            postRequestedFor(urlEqualTo("/mapping/court-sentencing/sentence-terms"))
              .withRequestBody(
                matchingJsonPath(
                  "dpsTermId",
                  equalTo(DPS_TERM_ID),
                ),
              )
              .withRequestBody(
                matchingJsonPath(
                  "nomisSentenceSequence",
                  equalTo(
                    NOMIS_SENTENCE_SEQ.toString(),
                  ),
                ),
              )
              .withRequestBody(
                matchingJsonPath(
                  "nomisTermSequence",
                  equalTo(
                    NOMIS_TERM_SEQ.toString(),
                  ),
                ),
              )
              .withRequestBody(
                matchingJsonPath(
                  "nomisBookingId",
                  equalTo(
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
        mappingServer.stubGetSentenceTermMappingGivenDpsId(
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
          check {
            assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
          },
          isNull(),
        )

        NomisApiExtension.nomisApi.verify(
          0,
          postRequestedFor(WireMock.anyUrl()),
        )
      }
    }

    @Nested
    inner class WhenMappingServiceFailsOnce {
      @BeforeEach
      fun setUp() {
        mappingServer.stubGetSentenceTermMappingGivenDpsIdWithError(DPS_TERM_ID, 404)
        CourtSentencingApiExtension.courtSentencingApi.stubGetPeriodLength(
          sentenceId = DPS_SENTENCE_ID,
          offenderNo = OFFENDER_NO,
          caseId = COURT_CASE_ID_FOR_CREATION,
          periodLengthId = DPS_TERM_ID,
          chargeId = DPS_COURT_CHARGE_ID,
          appearanceId = DPS_COURT_APPEARANCE_ID,
        )
        courtSentencingNomisApi.stubSentenceTermCreate(
          offenderNo = OFFENDER_NO,
          caseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          sentenceSeq = NOMIS_SENTENCE_SEQ,
          response = CreateSentenceTermResponse(sentenceSeq = NOMIS_SENTENCE_SEQ, termSeq = NOMIS_TERM_SEQ, bookingId = NOMIS_BOOKING_ID),
        )
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        mappingServer.stubGetSentenceMappingGivenDpsId(
          id = DPS_SENTENCE_ID,
          nomisSentenceSequence = NOMIS_SENTENCE_SEQ,
          nomisBookingId = NOMIS_BOOKING_ID,
        )

        mappingServer.stubCreateSentenceTermWithErrorFollowedBySlowSuccess()

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
          postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/sentences/$NOMIS_SENTENCE_SEQ/sentence-terms")),
        )
      }

      @Test
      fun `will eventually create a mapping after NOMIS sentence term is created`() {
        await untilAsserted {
          mappingServer.verify(
            2,
            postRequestedFor(urlEqualTo("/mapping/court-sentencing/sentence-terms"))
              .withRequestBody(
                matchingJsonPath(
                  "dpsTermId",
                  equalTo(DPS_TERM_ID),
                ),
              )
              .withRequestBody(
                matchingJsonPath(
                  "nomisSentenceSequence",
                  equalTo(
                    NOMIS_SENTENCE_SEQ.toString(),
                  ),
                ),
              )
              .withRequestBody(
                matchingJsonPath(
                  "nomisTermSequence",
                  equalTo(
                    NOMIS_TERM_SEQ.toString(),
                  ),
                ),
              )
              .withRequestBody(
                matchingJsonPath(
                  "nomisBookingId",
                  equalTo(
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
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
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
        courtSentencingNomisApi.stubSentenceTermUpdate(
          offenderNo = OFFENDER_NO,
          caseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          sentenceSeq = NOMIS_SENTENCE_SEQ,
          termSeq = NOMIS_TERM_SEQ,
        )
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )

        mappingServer.stubGetSentenceTermMappingGivenDpsId(
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
        CourtSentencingApiExtension.courtSentencingApi.verify(getRequestedFor(urlEqualTo("/legacy/period-length/${DPS_TERM_ID}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("sentence-term-updated-success"),
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
            assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
            assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
            assertThat(it["nomisSentenceSeq"]).isEqualTo(NOMIS_SENTENCE_SEQ.toString())
            assertThat(it["nomisTermSeq"]).isEqualTo(NOMIS_TERM_SEQ.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to update the Sentence term`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(WireMock.putRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/sentences/${NOMIS_SENTENCE_SEQ}/sentence-terms/${NOMIS_TERM_SEQ}")))
      }
    }

    @Nested
    inner class WhenMappingDoesntExistForPeriodLength {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetCourtCaseMappingGivenDpsId(COURT_CASE_ID_FOR_CREATION)
        mappingServer.stubGetSentenceTermMappingGivenDpsIdWithError(
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
            check {
              assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
              assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
              assertThat(it["dpsCourtAppearanceId"]).isEqualTo(DPS_COURT_APPEARANCE_ID)
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
              assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
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
        courtSentencingNomisApi.stubSentenceTermDelete(
          offenderNo = OFFENDER_NO,
          caseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
          sentenceSeq = NOMIS_SENTENCE_SEQ,
          termSeq = NOMIS_TERM_SEQ,
        )
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
          id = COURT_CASE_ID_FOR_CREATION,
          nomisCourtCaseId = NOMIS_COURT_CASE_ID_FOR_CREATION,
        )
        mappingServer.stubGetSentenceTermMappingGivenDpsId(
          id = DPS_TERM_ID,
          nomisSentenceSequence = NOMIS_SENTENCE_SEQ,
          nomisBookingId = NOMIS_BOOKING_ID,
          nomisTermSequence = NOMIS_TERM_SEQ,
        )
        mappingServer.stubDeleteSentenceTerm(id = DPS_TERM_ID)
        publishDeletePeriodLengthDomainEvent()
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          org.mockito.kotlin.eq("sentence-term-deleted-success"),
          check {
            assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
            assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
            assertThat(it["nomisSentenceSeq"]).isEqualTo(NOMIS_SENTENCE_SEQ.toString())
            assertThat(it["nomisBookingId"]).isEqualTo(NOMIS_BOOKING_ID.toString())
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to delete the sentence term`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(WireMock.deleteRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/court-cases/$NOMIS_COURT_CASE_ID_FOR_CREATION/sentences/$NOMIS_SENTENCE_SEQ/sentence-terms/$NOMIS_TERM_SEQ")))
      }

      @Test
      fun `will call the mapping service to delete the mapping`() {
        waitForAnyProcessingToComplete()
        mappingServer.verify(WireMock.deleteRequestedFor(urlEqualTo("/mapping/court-sentencing/sentence-terms/dps-term-id/$DPS_TERM_ID")))
      }
    }

    @Nested
    inner class WhenNoMappingExistsForPeriodLength {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetSentenceTermMappingGivenDpsIdWithError(DPS_TERM_ID, 404)
        publishDeletePeriodLengthDomainEvent()
      }

      @Test
      fun `will not attempt to delete a sentence term in NOMIS`() {
        await untilAsserted {
          verify(telemetryClient, times(1)).trackEvent(
            org.mockito.kotlin.eq("sentence-term-deleted-skipped"),
            check {
              assertThat(it["dpsSentenceId"]).isEqualTo(DPS_SENTENCE_ID)
              assertThat(it["dpsTermId"]).isEqualTo(DPS_TERM_ID)
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
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
        courtSentencingNomisApi.stubCaseReferenceRefresh(
          OFFENDER_NO,
          NOMIS_COURT_CASE_ID_FOR_CREATION,
        )

        mappingServer.stubGetCourtCaseMappingGivenDpsId(
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
        CourtSentencingApiExtension.courtSentencingApi.verify(getRequestedFor(urlEqualTo("/legacy/court-case/${COURT_CASE_ID_FOR_CREATION}")))
      }

      @Test
      fun `will create success telemetry`() {
        waitForAnyProcessingToComplete()

        verify(telemetryClient).trackEvent(
          eq("case-references-refreshed-success"),
          check {
            assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
            assertThat(it["nomisCourtCaseId"]).isEqualTo(NOMIS_COURT_CASE_ID_FOR_CREATION.toString())
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["caseReferences"])
              .isEqualTo("[CaseIdentifier(reference=$CASE_REFERENCE, createdDate=2024-01-01T10:10)]")
          },
          isNull(),
        )
      }

      @Test
      fun `will call nomis api to refresh the refs`() {
        waitForAnyProcessingToComplete()
        NomisApiExtension.nomisApi.verify(postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases/${NOMIS_COURT_CASE_ID_FOR_CREATION}/case-identifiers")))
      }
    }

    @Nested
    inner class WhenMappingDoesntExistForCourtCase {

      @BeforeEach
      fun setUp() {
        mappingServer.stubGetCourtCaseMappingGivenDpsId(
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
            check {
              assertThat(it["dpsCourtCaseId"]).isEqualTo(COURT_CASE_ID_FOR_CREATION)
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
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
  inner class CreateRecallSentences {

    @Nested
    inner class WhenRecallHasBeenCreatedInNomis {
      @BeforeEach
      fun setUp() {
        publishRecallInsertedDomainEvent(
          source = "NOMIS",
          recallId = "dc71f3c5-70d4-4faf-a4a5-ff9662d5f714",
          sentenceIds = listOf("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc", "7ed5c261-9644-4516-9ab5-1b2cd48e6ca1"),
        ).also {
          waitForAnyProcessingToComplete()
        }
      }

      @Test
      fun `will create ignore telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("recall-inserted-ignored"),
          check {
            assertThat(it["dpsRecallId"]).isEqualTo("dc71f3c5-70d4-4faf-a4a5-ff9662d5f714")
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["dpsSentenceIds"]).isEqualTo("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc, 7ed5c261-9644-4516-9ab5-1b2cd48e6ca1")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenRecallHasBeenInsertedInDPS {
      @BeforeEach
      fun setUp() {
        // Will change to a legacy specific endpoint once implemented by DPS
        CourtSentencingApiExtension.courtSentencingApi.stubGetRecall(
          "dc71f3c5-70d4-4faf-a4a5-ff9662d5f714",
          LegacyRecall(
            recallUuid = UUID.fromString("dc71f3c5-70d4-4faf-a4a5-ff9662d5f714"),
            recallType = LegacyRecall.RecallType.FTR_14,
            recallBy = "T.SMITH",
            returnToCustodyDate = LocalDate.parse("2025-04-23"),
            prisonerId = OFFENDER_NO,
            sentenceIds = listOf(
              UUID.fromString("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc"),
              UUID.fromString("7ed5c261-9644-4516-9ab5-1b2cd48e6ca1"),
            ),
          ),
        )
        mappingServer.stubGetMappingsGivenSentenceIds(
          listOf(
            SentenceMappingDto(
              dpsSentenceId = "9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc",
              nomisBookingId = BOOKING_ID,
              nomisSentenceSequence = 1,
            ),
            SentenceMappingDto(
              dpsSentenceId = "7ed5c261-9644-4516-9ab5-1b2cd48e6ca1",
              nomisBookingId = BOOKING_ID,
              nomisSentenceSequence = 2,
            ),
          ),
        )

        CourtSentencingApiExtension.courtSentencingApi.stubGetSentences(
          sentences = listOf(
            legacySentence(sentenceId = "9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc", sentenceCalcType = "FTR_14", sentenceCategory = "2020"),
            legacySentence(sentenceId = "7ed5c261-9644-4516-9ab5-1b2cd48e6ca1", sentenceCalcType = "FTR_14", sentenceCategory = "2013"),
          ),
        )

        courtSentencingNomisApi.stubRecallSentences(OFFENDER_NO)
        publishRecallInsertedDomainEvent(
          source = "DPS",
          recallId = "dc71f3c5-70d4-4faf-a4a5-ff9662d5f714",
          sentenceIds = listOf("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc", "7ed5c261-9644-4516-9ab5-1b2cd48e6ca1"),
        ).also {
          waitForAnyProcessingToComplete()
        }
      }

      @Test
      fun `will get the NOMIS sentenceIds for each DPS sentence`() {
        mappingServer.verify(
          postRequestedFor(urlEqualTo("/mapping/court-sentencing/sentences/dps-sentence-ids/get-list"))
            .withRequestBody(matchingJsonPath("$[0]", equalTo("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc")))
            .withRequestBody(matchingJsonPath("$[1]", equalTo("7ed5c261-9644-4516-9ab5-1b2cd48e6ca1"))),
        )
      }

      @Test
      fun `will retrieve DPS recall information`() {
        CourtSentencingApiExtension.courtSentencingApi.verify(getRequestedFor(urlEqualTo("/legacy/recall/dc71f3c5-70d4-4faf-a4a5-ff9662d5f714")))
      }

      @Test
      fun `will retrieve DPS sentence information`() {
        CourtSentencingApiExtension.courtSentencingApi.verify(
          postRequestedFor(urlEqualTo("/legacy/sentence/search"))
            .withRequestBody(matchingJsonPath("lifetimeUuids[0]", equalTo("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc")))
            .withRequestBody(matchingJsonPath("lifetimeUuids[1]", equalTo("7ed5c261-9644-4516-9ab5-1b2cd48e6ca1"))),
        )
      }

      @Test
      fun `will update NOMIS sentence information`() {
        NomisApiExtension.nomisApi.verify(
          postRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/sentences/recall"))
            .withRequestBody(matchingJsonPath("sentences[0].sentenceId.offenderBookingId", equalTo("$BOOKING_ID")))
            .withRequestBody(matchingJsonPath("sentences[0].sentenceId.sentenceSequence", equalTo("1")))
            .withRequestBody(matchingJsonPath("sentences[0].sentenceCategory", equalTo("2020")))
            .withRequestBody(matchingJsonPath("sentences[0].sentenceCalcType", equalTo("FTR_14")))
            .withRequestBody(matchingJsonPath("sentences[0].active", equalTo("true")))
            .withRequestBody(matchingJsonPath("sentences[1].sentenceId.offenderBookingId", equalTo("$BOOKING_ID")))
            .withRequestBody(matchingJsonPath("sentences[1].sentenceId.sentenceSequence", equalTo("2")))
            .withRequestBody(matchingJsonPath("sentences[1].sentenceCategory", equalTo("2013")))
            .withRequestBody(matchingJsonPath("sentences[1].sentenceCalcType", equalTo("FTR_14")))
            .withRequestBody(matchingJsonPath("sentences[1].active", equalTo("true")))
            .withRequestBody(matchingJsonPath("returnToCustody.returnToCustodyDate", equalTo("2025-04-23")))
            .withRequestBody(matchingJsonPath("returnToCustody.recallLength", equalTo("14")))
            .withRequestBody(matchingJsonPath("returnToCustody.enteredByStaffUsername", equalTo("T.SMITH"))),
        )
      }

      @Test
      fun `will create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("recall-inserted-success"),
          check {
            assertThat(it["recallType"]).isEqualTo("FTR_14")
            assertThat(it["dpsRecallId"]).isEqualTo("dc71f3c5-70d4-4faf-a4a5-ff9662d5f714")
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["dpsSentenceIds"]).isEqualTo("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc, 7ed5c261-9644-4516-9ab5-1b2cd48e6ca1")
            assertThat(it["nomisSentenceSeq"]).isEqualTo("1, 2")
            assertThat(it["nomisBookingId"]).isEqualTo("$BOOKING_ID")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  inner class UpdateRecallSentences {

    @Nested
    inner class WhenRecallHasBeenUpdatedInNomis {
      @BeforeEach
      fun setUp() {
        publishRecallUpdatedDomainEvent(
          source = "NOMIS",
          recallId = "dc71f3c5-70d4-4faf-a4a5-ff9662d5f714",
          sentenceIds = listOf("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc", "7ed5c261-9644-4516-9ab5-1b2cd48e6ca1"),
        ).also {
          waitForAnyProcessingToComplete()
        }
      }

      @Test
      fun `will create ignore telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("recall-updated-ignored"),
          check {
            assertThat(it["dpsRecallId"]).isEqualTo("dc71f3c5-70d4-4faf-a4a5-ff9662d5f714")
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["dpsSentenceIds"]).isEqualTo("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc, 7ed5c261-9644-4516-9ab5-1b2cd48e6ca1")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenRecallHasBeenUpdatedInDPS {
      @BeforeEach
      fun setUp() {
        CourtSentencingApiExtension.courtSentencingApi.stubGetRecall(
          "dc71f3c5-70d4-4faf-a4a5-ff9662d5f714",
          LegacyRecall(
            recallUuid = UUID.fromString("dc71f3c5-70d4-4faf-a4a5-ff9662d5f714"),
            recallType = LegacyRecall.RecallType.FTR_14,
            recallBy = "T.SMITH",
            returnToCustodyDate = LocalDate.parse("2025-04-23"),
            prisonerId = OFFENDER_NO,
            sentenceIds = listOf(
              UUID.fromString("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc"),
              UUID.fromString("7ed5c261-9644-4516-9ab5-1b2cd48e6ca1"),
            ),
          ),
        )
        mappingServer.stubGetMappingsGivenSentenceIds(
          listOf(
            SentenceMappingDto(
              dpsSentenceId = "9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc",
              nomisBookingId = BOOKING_ID,
              nomisSentenceSequence = 1,
            ),
            SentenceMappingDto(
              dpsSentenceId = "7ed5c261-9644-4516-9ab5-1b2cd48e6ca1",
              nomisBookingId = BOOKING_ID,
              nomisSentenceSequence = 2,
            ),
          ),
        )

        CourtSentencingApiExtension.courtSentencingApi.stubGetSentences(
          sentences = listOf(
            legacySentence(sentenceId = "9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc", sentenceCalcType = "FTR_14", sentenceCategory = "2020", active = true),
            legacySentence(sentenceId = "7ed5c261-9644-4516-9ab5-1b2cd48e6ca1", sentenceCalcType = "FTR_14", sentenceCategory = "2013", active = false),
          ),
        )

        courtSentencingNomisApi.stubUpdateRecallSentences(OFFENDER_NO)
        publishRecallUpdatedDomainEvent(
          source = "DPS",
          recallId = "dc71f3c5-70d4-4faf-a4a5-ff9662d5f714",
          sentenceIds = listOf("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc", "7ed5c261-9644-4516-9ab5-1b2cd48e6ca1"),
        ).also {
          waitForAnyProcessingToComplete()
        }
      }

      @Test
      fun `will get the NOMIS sentenceIds for each DPS sentence`() {
        mappingServer.verify(
          postRequestedFor(urlEqualTo("/mapping/court-sentencing/sentences/dps-sentence-ids/get-list"))
            .withRequestBody(matchingJsonPath("$[0]", equalTo("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc")))
            .withRequestBody(matchingJsonPath("$[1]", equalTo("7ed5c261-9644-4516-9ab5-1b2cd48e6ca1"))),
        )
      }

      @Test
      fun `will retrieve DPS recall information`() {
        CourtSentencingApiExtension.courtSentencingApi.verify(getRequestedFor(urlEqualTo("/legacy/recall/dc71f3c5-70d4-4faf-a4a5-ff9662d5f714")))
      }

      @Test
      fun `will retrieve DPS sentence information`() {
        CourtSentencingApiExtension.courtSentencingApi.verify(
          postRequestedFor(urlEqualTo("/legacy/sentence/search"))
            .withRequestBody(matchingJsonPath("lifetimeUuids[0]", equalTo("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc")))
            .withRequestBody(matchingJsonPath("lifetimeUuids[1]", equalTo("7ed5c261-9644-4516-9ab5-1b2cd48e6ca1"))),
        )
      }

      @Test
      fun `will update NOMIS sentence information`() {
        NomisApiExtension.nomisApi.verify(
          putRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/sentences/recall"))
            .withRequestBody(matchingJsonPath("sentences[0].sentenceId.offenderBookingId", equalTo("$BOOKING_ID")))
            .withRequestBody(matchingJsonPath("sentences[0].sentenceId.sentenceSequence", equalTo("1")))
            .withRequestBody(matchingJsonPath("sentences[0].sentenceCategory", equalTo("2020")))
            .withRequestBody(matchingJsonPath("sentences[0].sentenceCalcType", equalTo("FTR_14")))
            .withRequestBody(matchingJsonPath("sentences[0].active", equalTo("true")))
            .withRequestBody(matchingJsonPath("sentences[1].sentenceId.offenderBookingId", equalTo("$BOOKING_ID")))
            .withRequestBody(matchingJsonPath("sentences[1].sentenceId.sentenceSequence", equalTo("2")))
            .withRequestBody(matchingJsonPath("sentences[1].sentenceCategory", equalTo("2013")))
            .withRequestBody(matchingJsonPath("sentences[1].sentenceCalcType", equalTo("FTR_14")))
            .withRequestBody(matchingJsonPath("sentences[1].active", equalTo("false")))
            .withRequestBody(matchingJsonPath("returnToCustody.returnToCustodyDate", equalTo("2025-04-23")))
            .withRequestBody(matchingJsonPath("returnToCustody.recallLength", equalTo("14")))
            .withRequestBody(matchingJsonPath("returnToCustody.enteredByStaffUsername", equalTo("T.SMITH"))),
        )
      }

      @Test
      fun `will create success telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("recall-updated-success"),
          check {
            assertThat(it["recallType"]).isEqualTo("FTR_14")
            assertThat(it["dpsRecallId"]).isEqualTo("dc71f3c5-70d4-4faf-a4a5-ff9662d5f714")
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["dpsSentenceIds"]).isEqualTo("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc, 7ed5c261-9644-4516-9ab5-1b2cd48e6ca1")
            assertThat(it["nomisSentenceSeq"]).isEqualTo("1, 2")
            assertThat(it["nomisBookingId"]).isEqualTo("$BOOKING_ID")
          },
          isNull(),
        )
      }
    }
  }

  @Nested
  inner class DeleteRecallSentences {

    @Nested
    inner class WhenRecallHasBeenDeletedInNomis {
      @BeforeEach
      fun setUp() {
        publishRecallDeletedDomainEvent(
          source = "NOMIS",
          recallId = "dc71f3c5-70d4-4faf-a4a5-ff9662d5f714",
          previousRecallId = null,
          sentenceIds = listOf("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc", "7ed5c261-9644-4516-9ab5-1b2cd48e6ca1"),
        ).also {
          waitForAnyProcessingToComplete()
        }
      }

      @Test
      fun `will create ignore telemetry`() {
        verify(telemetryClient).trackEvent(
          eq("recall-deleted-ignored"),
          check {
            assertThat(it["dpsRecallId"]).isEqualTo("dc71f3c5-70d4-4faf-a4a5-ff9662d5f714")
            assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
            assertThat(it["dpsSentenceIds"]).isEqualTo("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc, 7ed5c261-9644-4516-9ab5-1b2cd48e6ca1")
          },
          isNull(),
        )
      }
    }

    @Nested
    inner class WhenRecallHasBeenDeletedInDPS {
      @Nested
      inner class WhenRevertingBackToPreviousRecall {

        @BeforeEach
        fun setUp() {
          CourtSentencingApiExtension.courtSentencingApi.stubGetRecall(
            "dc71f3c5-70d4-4faf-a4a5-ff9662d5f714",
            LegacyRecall(
              recallUuid = UUID.fromString("dc71f3c5-70d4-4faf-a4a5-ff9662d5f714"),
              recallType = LegacyRecall.RecallType.FTR_14,
              recallBy = "T.SMITH",
              returnToCustodyDate = LocalDate.parse("2025-04-23"),
              prisonerId = OFFENDER_NO,
              sentenceIds = listOf(
                UUID.fromString("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc"),
                UUID.fromString("7ed5c261-9644-4516-9ab5-1b2cd48e6ca1"),
              ),
            ),
          )
          mappingServer.stubGetMappingsGivenSentenceIds(
            listOf(
              SentenceMappingDto(
                dpsSentenceId = "9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc",
                nomisBookingId = BOOKING_ID,
                nomisSentenceSequence = 1,
              ),
              SentenceMappingDto(
                dpsSentenceId = "7ed5c261-9644-4516-9ab5-1b2cd48e6ca1",
                nomisBookingId = BOOKING_ID,
                nomisSentenceSequence = 2,
              ),
            ),
          )

          CourtSentencingApiExtension.courtSentencingApi.stubGetSentences(
            sentences = listOf(
              legacySentence(
                sentenceId = "9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc",
                sentenceCalcType = "FTR_14",
                sentenceCategory = "2020",
                active = true,
              ),
              legacySentence(
                sentenceId = "7ed5c261-9644-4516-9ab5-1b2cd48e6ca1",
                sentenceCalcType = "FTR_14",
                sentenceCategory = "2013",
                active = true,
              ),
            ),
          )

          courtSentencingNomisApi.stubUpdateRecallSentences(OFFENDER_NO)
          publishRecallDeletedDomainEvent(
            source = "DPS",
            previousRecallId = "dc71f3c5-70d4-4faf-a4a5-ff9662d5f714",
            recallId = "ee1c3e64-3e5d-441b-98c6-c4449d94fd9c",
            sentenceIds = listOf("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc", "7ed5c261-9644-4516-9ab5-1b2cd48e6ca1"),
          ).also {
            waitForAnyProcessingToComplete()
          }
        }

        @Test
        fun `will get the NOMIS sentenceIds for each DPS sentence`() {
          mappingServer.verify(
            postRequestedFor(urlEqualTo("/mapping/court-sentencing/sentences/dps-sentence-ids/get-list"))
              .withRequestBody(matchingJsonPath("$[0]", equalTo("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc")))
              .withRequestBody(matchingJsonPath("$[1]", equalTo("7ed5c261-9644-4516-9ab5-1b2cd48e6ca1"))),
          )
        }

        @Test
        fun `will retrieve DPS recall information for previous recall`() {
          CourtSentencingApiExtension.courtSentencingApi.verify(getRequestedFor(urlEqualTo("/legacy/recall/dc71f3c5-70d4-4faf-a4a5-ff9662d5f714")))
        }

        @Test
        fun `will retrieve DPS sentence information`() {
          CourtSentencingApiExtension.courtSentencingApi.verify(
            postRequestedFor(urlEqualTo("/legacy/sentence/search"))
              .withRequestBody(matchingJsonPath("lifetimeUuids[0]", equalTo("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc")))
              .withRequestBody(matchingJsonPath("lifetimeUuids[1]", equalTo("7ed5c261-9644-4516-9ab5-1b2cd48e6ca1"))),
          )
        }

        @Test
        fun `will update NOMIS sentence information with previous recall data`() {
          NomisApiExtension.nomisApi.verify(
            putRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/sentences/recall"))
              .withRequestBody(matchingJsonPath("sentences[0].sentenceId.offenderBookingId", equalTo("$BOOKING_ID")))
              .withRequestBody(matchingJsonPath("sentences[0].sentenceId.sentenceSequence", equalTo("1")))
              .withRequestBody(matchingJsonPath("sentences[0].sentenceCategory", equalTo("2020")))
              .withRequestBody(matchingJsonPath("sentences[0].sentenceCalcType", equalTo("FTR_14")))
              .withRequestBody(matchingJsonPath("sentences[0].active", equalTo("true")))
              .withRequestBody(matchingJsonPath("sentences[1].sentenceId.offenderBookingId", equalTo("$BOOKING_ID")))
              .withRequestBody(matchingJsonPath("sentences[1].sentenceId.sentenceSequence", equalTo("2")))
              .withRequestBody(matchingJsonPath("sentences[1].sentenceCategory", equalTo("2013")))
              .withRequestBody(matchingJsonPath("sentences[1].sentenceCalcType", equalTo("FTR_14")))
              .withRequestBody(matchingJsonPath("sentences[1].active", equalTo("true")))
              .withRequestBody(matchingJsonPath("returnToCustody.returnToCustodyDate", equalTo("2025-04-23")))
              .withRequestBody(matchingJsonPath("returnToCustody.recallLength", equalTo("14")))
              .withRequestBody(matchingJsonPath("returnToCustody.enteredByStaffUsername", equalTo("T.SMITH"))),
          )
        }

        @Test
        fun `will create success telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("recall-deleted-success"),
            check {
              assertThat(it["recallType"]).isEqualTo("FTR_14")
              assertThat(it["dpsRecallId"]).isEqualTo("ee1c3e64-3e5d-441b-98c6-c4449d94fd9c")
              assertThat(it["dpsPreviousRecallId"]).isEqualTo("dc71f3c5-70d4-4faf-a4a5-ff9662d5f714")
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
              assertThat(it["dpsSentenceIds"]).isEqualTo("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc, 7ed5c261-9644-4516-9ab5-1b2cd48e6ca1")
              assertThat(it["nomisSentenceSeq"]).isEqualTo("1, 2")
              assertThat(it["nomisBookingId"]).isEqualTo("$BOOKING_ID")
            },
            isNull(),
          )
        }
      }

      @Nested
      inner class WhenRevertingBackToPreviousSentence {

        @BeforeEach
        fun setUp() {
          mappingServer.stubGetMappingsGivenSentenceIds(
            listOf(
              SentenceMappingDto(
                dpsSentenceId = "9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc",
                nomisBookingId = BOOKING_ID,
                nomisSentenceSequence = 1,
              ),
              SentenceMappingDto(
                dpsSentenceId = "7ed5c261-9644-4516-9ab5-1b2cd48e6ca1",
                nomisBookingId = BOOKING_ID,
                nomisSentenceSequence = 2,
              ),
            ),
          )

          CourtSentencingApiExtension.courtSentencingApi.stubGetSentences(
            sentences = listOf(
              legacySentence(
                sentenceId = "9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc",
                sentenceCalcType = "ADIMP_ORA",
                sentenceCategory = "2020",
                active = false,
              ),
              legacySentence(
                sentenceId = "7ed5c261-9644-4516-9ab5-1b2cd48e6ca1",
                sentenceCalcType = "ADIMP_ORA",
                sentenceCategory = "2013",
                active = false,
              ),
            ),
          )

          courtSentencingNomisApi.stubDeleteRecallSentences(OFFENDER_NO)
          publishRecallDeletedDomainEvent(
            source = "DPS",
            previousRecallId = null,
            recallId = "ee1c3e64-3e5d-441b-98c6-c4449d94fd9c",
            sentenceIds = listOf("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc", "7ed5c261-9644-4516-9ab5-1b2cd48e6ca1"),
          ).also {
            waitForAnyProcessingToComplete()
          }
        }

        @Test
        fun `will get the NOMIS sentenceIds for each DPS sentence`() {
          mappingServer.verify(
            postRequestedFor(urlEqualTo("/mapping/court-sentencing/sentences/dps-sentence-ids/get-list"))
              .withRequestBody(matchingJsonPath("$[0]", equalTo("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc")))
              .withRequestBody(matchingJsonPath("$[1]", equalTo("7ed5c261-9644-4516-9ab5-1b2cd48e6ca1"))),
          )
        }

        @Test
        fun `will not retrieve DPS recall information for previous recall`() {
          CourtSentencingApiExtension.courtSentencingApi.verify(0, getRequestedFor(urlEqualTo("/legacy/recall/ee1c3e64-3e5d-441b-98c6-c4449d94fd9c")))
        }

        @Test
        fun `will retrieve DPS sentence information`() {
          CourtSentencingApiExtension.courtSentencingApi.verify(
            postRequestedFor(urlEqualTo("/legacy/sentence/search"))
              .withRequestBody(matchingJsonPath("lifetimeUuids[0]", equalTo("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc")))
              .withRequestBody(matchingJsonPath("lifetimeUuids[1]", equalTo("7ed5c261-9644-4516-9ab5-1b2cd48e6ca1"))),
          )
        }

        @Test
        fun `will update NOMIS sentence information with previous sentence data`() {
          NomisApiExtension.nomisApi.verify(
            putRequestedFor(urlEqualTo("/prisoners/$OFFENDER_NO/sentences/recall/restore-original"))
              .withRequestBody(matchingJsonPath("sentences[0].sentenceId.offenderBookingId", equalTo("$BOOKING_ID")))
              .withRequestBody(matchingJsonPath("sentences[0].sentenceId.sentenceSequence", equalTo("1")))
              .withRequestBody(matchingJsonPath("sentences[0].sentenceCategory", equalTo("2020")))
              .withRequestBody(matchingJsonPath("sentences[0].sentenceCalcType", equalTo("ADIMP_ORA")))
              .withRequestBody(matchingJsonPath("sentences[1].sentenceId.offenderBookingId", equalTo("$BOOKING_ID")))
              .withRequestBody(matchingJsonPath("sentences[1].sentenceId.sentenceSequence", equalTo("2")))
              .withRequestBody(matchingJsonPath("sentences[1].sentenceCategory", equalTo("2013")))
              .withRequestBody(matchingJsonPath("sentences[1].sentenceCalcType", equalTo("ADIMP_ORA"))),
          )
        }

        @Test
        fun `will create success telemetry`() {
          verify(telemetryClient).trackEvent(
            eq("recall-deleted-success"),
            check {
              assertThat(it["recallType"]).isNull()
              assertThat(it["dpsRecallId"]).isEqualTo("ee1c3e64-3e5d-441b-98c6-c4449d94fd9c")
              assertThat(it["dpsPreviousRecallId"]).isNull()
              assertThat(it["offenderNo"]).isEqualTo(OFFENDER_NO)
              assertThat(it["dpsSentenceIds"]).isEqualTo("9ee21616-bbe4-4adc-b05e-c6e2a6a67cfc, 7ed5c261-9644-4516-9ab5-1b2cd48e6ca1")
              assertThat(it["nomisSentenceSeq"]).isEqualTo("1, 2")
              assertThat(it["nomisBookingId"]).isEqualTo("$BOOKING_ID")
            },
            isNull(),
          )
        }
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

  private fun publishRecallInsertedDomainEvent(@Suppress("SameParameterValue") recallId: String = UUID.randomUUID().toString(), sentenceIds: List<String> = listOf(UUID.randomUUID().toString()), source: String = "DPS") {
    val eventType = "recall.inserted"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          recallMessagePayload(
            offenderNo = OFFENDER_NO,
            recallId = recallId,
            sentenceIds = sentenceIds,
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

  private fun publishRecallUpdatedDomainEvent(@Suppress("SameParameterValue") recallId: String = UUID.randomUUID().toString(), sentenceIds: List<String> = listOf(UUID.randomUUID().toString()), source: String = "DPS") {
    val eventType = "recall.updated"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          recallMessagePayload(
            offenderNo = OFFENDER_NO,
            recallId = recallId,
            sentenceIds = sentenceIds,
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
  private fun publishRecallDeletedDomainEvent(recallId: String = UUID.randomUUID().toString(), previousRecallId: String? = null, sentenceIds: List<String> = listOf(UUID.randomUUID().toString()), source: String = "DPS") {
    val eventType = "recall.deleted"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          recallMessagePayload(
            offenderNo = OFFENDER_NO,
            recallId = recallId,
            previousRecallId = previousRecallId,
            sentenceIds = sentenceIds,
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

  fun recallMessagePayload(
    recallId: String,
    previousRecallId: String? = null,
    sentenceIds: List<String>,
    offenderNo: String,
    eventType: String,
    source: String = "DPS",
  ) = """{"eventType":"$eventType", "additionalInformation": {"recallId":"$recallId", "previousRecallId": ${previousRecallId?.let {"\"$it\""}},  "sentenceIds":[${sentenceIds.joinToString { "\"$it\"" }}], "source": "$source"}, "personReference": {"identifiers":[{"type":"NOMS", "value":"$offenderNo"}]}}"""

  fun nomisCourtAppearanceCreateResponse(): CreateCourtAppearanceResponse = CreateCourtAppearanceResponse(id = NOMIS_COURT_APPEARANCE_ID, courtEventChargesIds = emptyList())

  fun nomisCourtAppearanceUpdateResponseWithTwoDeletedCharges(): UpdateCourtAppearanceResponse = UpdateCourtAppearanceResponse(
    createdCourtEventChargesIds = emptyList(),
    deletedOffenderChargesIds = listOf(OffenderChargeIdResponse(offenderChargeId = NOMIS_COURT_CHARGE_5_ID), OffenderChargeIdResponse(offenderChargeId = NOMIS_COURT_CHARGE_6_ID)),
  )

  fun nomisSentenceCreateResponseWithOneTerm(): CreateSentenceResponse = CreateSentenceResponse(
    sentenceSeq = NOMIS_SENTENCE_SEQ,
    bookingId = NOMIS_BOOKING_ID,
  )

  fun nomisCourtChargeCreateResponse(): OffenderChargeIdResponse = OffenderChargeIdResponse(NOMIS_COURT_CHARGE_ID)

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
