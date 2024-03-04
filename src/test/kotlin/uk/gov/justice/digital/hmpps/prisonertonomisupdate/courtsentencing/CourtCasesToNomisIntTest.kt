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
import org.mockito.kotlin.verify
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtCaseMapping
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
private const val DPS_COURT_CHARGE_ID = "8576aa44-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_COURT_CHARGE_2_ID = "9996aa44-642a-484a-a967-2d17b5c9c5a1"
private const val OFFENDER_NO = "AB12345"
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
        NomisApiExtension.nomisApi.verify(WireMock.postRequestedFor(WireMock.urlEqualTo("/prisoners/$OFFENDER_NO/sentencing/court-cases")))
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
                  WireMock.equalTo(COURT_CASE_ID_FOR_CREATION.toString()),
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
                  WireMock.equalTo(COURT_CASE_ID_FOR_CREATION.toString()),
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

  private fun publishCreateCourtCaseDomainEvent() {
    val eventType = "court-sentencing.court-case.created"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(
          courtCaseMessagePayload(
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
    """{"eventType":"$eventType", "additionalInformation": {"id":"$courtCaseId", "offenderNo": "$offenderNo", "source": "$source"}}"""

  fun nomisCourtCaseCreateResponseWithTwoCharges(): String {
    return """{ "id": $NOMIS_COURT_CASE_ID_FOR_CREATION, "courtAppearanceIds": [{"id": $NOMIS_COURT_APPEARANCE_ID, "nextCourtAppearanceId": $NOMIS_NEXT_COURT_APPEARANCE_ID, "courtEventChargesIds": [{"offenderChargeId": $NOMIS_COURT_CHARGE_ID }, {"offenderChargeId": $NOMIS_COURT_CHARGE_2_ID }] }] }"""
  }
}
