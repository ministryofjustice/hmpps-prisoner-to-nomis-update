package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions
import org.awaitility.kotlin.await
import org.awaitility.kotlin.untilAsserted
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OutcomeDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.integration.SqsIntegrationTestBase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.AdjudicationsApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension

private const val CHARGE_NUMBER = "12345-1"
private const val ADJUDICATION_NUMBER = 12345L
private const val PRISON_ID = "MDI"
private const val OFFENDER_NO = "A1234AA"
private const val DPS_HEARING_ID = "345"
private const val NOMIS_HEARING_ID = 2345L
private const val CHARGE_SEQUENCE = 1

class HearingOutcomesToNomisIntTest : SqsIntegrationTestBase() {

  @Nested
  inner class CreateHearingCompleted {
    @Nested
    inner class WhenHearingHasAnOutcomeOfAdjourn {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER, ADJUDICATION_NUMBER)
        AdjudicationsApiExtension.adjudicationsApiServer.stubChargeGetWithAdjournOutcome(
          hearingId = DPS_HEARING_ID.toLong(),
          chargeNumber = CHARGE_NUMBER,
          offenderNo = OFFENDER_NO,
        )
        NomisApiExtension.nomisApi.stubHearingResultUpsert(ADJUDICATION_NUMBER, NOMIS_HEARING_ID)
        MappingExtension.mappingServer.stubGetByDpsHearingId(DPS_HEARING_ID, NOMIS_HEARING_ID)
        publishCreateHearingAdjournedDomainEvent()
      }

      @Test
      fun `will callback back to adjudication service, post the create and track success`() {
        waitForCreateHearingProcessingToBeComplete()

        AdjudicationsApiExtension.adjudicationsApiServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/reported-adjudications/$CHARGE_NUMBER/v2")))
        NomisApiExtension.nomisApi.verify(
          WireMock.postRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings/$NOMIS_HEARING_ID/charge/$CHARGE_SEQUENCE/result"))
            .withRequestBody(WireMock.matchingJsonPath("$.adjudicatorUsername", WireMock.equalTo("JBULLENGEN"))),
        )
        verifyHearingResultCreatedSuccessCustomEvent(findingCode = "ADJOURNED", plea = "UNFIT")
      }
    }

    @Nested
    inner class WhenHearingHasAnOutcomeOfReferPolice {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER, ADJUDICATION_NUMBER)
        AdjudicationsApiExtension.adjudicationsApiServer.stubChargeGetWithReferPoliceOutcome(
          hearingId = DPS_HEARING_ID.toLong(),
          chargeNumber = CHARGE_NUMBER,
          offenderNo = OFFENDER_NO,
        )
        NomisApiExtension.nomisApi.stubHearingResultUpsert(ADJUDICATION_NUMBER, NOMIS_HEARING_ID)
        MappingExtension.mappingServer.stubGetByDpsHearingId(DPS_HEARING_ID, NOMIS_HEARING_ID)
        publishCreateHearingReferredDomainEvent()
      }

      @Test
      fun `will callback back to adjudication service, post the create and track success`() {
        waitForCreateHearingProcessingToBeComplete()

        AdjudicationsApiExtension.adjudicationsApiServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/reported-adjudications/$CHARGE_NUMBER/v2")))
        NomisApiExtension.nomisApi.verify(
          WireMock.postRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings/$NOMIS_HEARING_ID/charge/$CHARGE_SEQUENCE/result"))
            .withRequestBody(WireMock.matchingJsonPath("$.pleaFindingCode", WireMock.equalTo("NOT_ASKED")))
            .withRequestBody(WireMock.matchingJsonPath("$.findingCode", WireMock.equalTo("REF_POLICE")))
            .withRequestBody(WireMock.matchingJsonPath("$.adjudicatorUsername", WireMock.equalTo("JBULLENGEN"))),
        )

        verifyHearingResultCreatedSuccessCustomEvent(findingCode = "REF_POLICE", plea = "NOT_ASKED")
      }
    }

    @Nested
    inner class WhenHearingHasAnOutcomeOfReferIndependentAdjudicator {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER, ADJUDICATION_NUMBER)
        AdjudicationsApiExtension.adjudicationsApiServer.stubChargeGetWithReferIndependentAdjudicatorOutcome(
          hearingId = DPS_HEARING_ID.toLong(),
          chargeNumber = CHARGE_NUMBER,
          offenderNo = OFFENDER_NO,
        )
        NomisApiExtension.nomisApi.stubHearingResultUpsert(ADJUDICATION_NUMBER, NOMIS_HEARING_ID)
        MappingExtension.mappingServer.stubGetByDpsHearingId(DPS_HEARING_ID, NOMIS_HEARING_ID)
        publishCreateHearingReferredDomainEvent()
      }

      @Test
      fun `will callback back to adjudication service, post the create and track success`() {
        waitForCreateHearingProcessingToBeComplete()

        AdjudicationsApiExtension.adjudicationsApiServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/reported-adjudications/$CHARGE_NUMBER/v2")))
        NomisApiExtension.nomisApi.verify(
          WireMock.postRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings/$NOMIS_HEARING_ID/charge/$CHARGE_SEQUENCE/result"))
            .withRequestBody(WireMock.matchingJsonPath("$.adjudicatorUsername", WireMock.equalTo("JBULLENGEN"))),
        )

        verifyHearingResultCreatedSuccessCustomEvent(findingCode = "ADJOURNED", plea = "NOT_ASKED")
      }
    }

    @Nested
    inner class WhenHearingHasAReferralOutcomeOfProsecuted {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER, ADJUDICATION_NUMBER)
        AdjudicationsApiExtension.adjudicationsApiServer.stubChargeGetWithReferralOutcome(
          outcomeCode = OutcomeDto.Code.REFER_POLICE.name,
          referralOutcomeCode = OutcomeDto.Code.PROSECUTION.name,
          hearingId = DPS_HEARING_ID.toLong(),
          chargeNumber = CHARGE_NUMBER,
          offenderNo = OFFENDER_NO,
        )
        NomisApiExtension.nomisApi.stubHearingResultUpsert(ADJUDICATION_NUMBER, NOMIS_HEARING_ID)
        MappingExtension.mappingServer.stubGetByDpsHearingId(DPS_HEARING_ID, NOMIS_HEARING_ID)
        publishCreateHearingReferralProsecutionDomainEvent()
      }

      @Test
      fun `will callback back to adjudication service, post the create and track success`() {
        waitForCreateHearingProcessingToBeComplete()

        AdjudicationsApiExtension.adjudicationsApiServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/reported-adjudications/$CHARGE_NUMBER/v2")))
        NomisApiExtension.nomisApi.verify(
          WireMock.postRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings/$NOMIS_HEARING_ID/charge/$CHARGE_SEQUENCE/result"))
            .withRequestBody(WireMock.matchingJsonPath("$.pleaFindingCode", WireMock.equalTo("NOT_ASKED")))
            .withRequestBody(WireMock.matchingJsonPath("$.findingCode", WireMock.equalTo("PROSECUTED")))
            .withRequestBody(WireMock.matchingJsonPath("$.adjudicatorUsername", WireMock.absent())),
        )

        verifyHearingResultCreatedSuccessCustomEvent(plea = "NOT_ASKED", findingCode = "PROSECUTED")
      }
    }

    @Nested
    inner class WhenHearingHasAReferralOutcomeOfNotProceed {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER, ADJUDICATION_NUMBER)
        AdjudicationsApiExtension.adjudicationsApiServer.stubChargeGetWithReferralOutcome(
          outcomeCode = OutcomeDto.Code.REFER_POLICE.name,
          referralOutcomeCode = OutcomeDto.Code.NOT_PROCEED.name,
          hearingId = DPS_HEARING_ID.toLong(),
          chargeNumber = CHARGE_NUMBER,
          offenderNo = OFFENDER_NO,
          hearingType = "GOV_ADULT",
        )
        NomisApiExtension.nomisApi.stubHearingResultUpsert(ADJUDICATION_NUMBER, NOMIS_HEARING_ID)
        MappingExtension.mappingServer.stubGetByDpsHearingId(DPS_HEARING_ID, NOMIS_HEARING_ID)
        publishCreateHearingReferralNotProceedDomainEvent()
      }

      @Test
      fun `will callback back to adjudication service, post the create and track success`() {
        waitForCreateHearingProcessingToBeComplete()

        AdjudicationsApiExtension.adjudicationsApiServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/reported-adjudications/$CHARGE_NUMBER/v2")))
        NomisApiExtension.nomisApi.verify(
          WireMock.postRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings/$NOMIS_HEARING_ID/charge/$CHARGE_SEQUENCE/result"))
            .withRequestBody(WireMock.matchingJsonPath("$.adjudicatorUsername", WireMock.equalTo("jack_b"))),
        )

        verifyHearingResultCreatedSuccessCustomEvent(plea = "NOT_ASKED", findingCode = "NOT_PROCEED")
      }
    }

    @Nested
    inner class WhenHearingHasAReferralOutcomeOfReferGov {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER, ADJUDICATION_NUMBER)
        AdjudicationsApiExtension.adjudicationsApiServer.stubChargeGetWithReferralOutcome(
          outcomeCode = OutcomeDto.Code.REFER_INAD.name,
          referralOutcomeCode = OutcomeDto.Code.REFER_GOV.name,
          hearingId = DPS_HEARING_ID.toLong(),
          chargeNumber = CHARGE_NUMBER,
          offenderNo = OFFENDER_NO,
          hearingType = "GOV_ADULT",
        )
        NomisApiExtension.nomisApi.stubHearingResultUpsert(ADJUDICATION_NUMBER, NOMIS_HEARING_ID)
        MappingExtension.mappingServer.stubGetByDpsHearingId(DPS_HEARING_ID, NOMIS_HEARING_ID)
        publishCreateHearingReferralReferGovDomainEvent()
      }

      @Test
      fun `will callback back to adjudication service, post the create and track success`() {
        waitForCreateHearingProcessingToBeComplete()

        AdjudicationsApiExtension.adjudicationsApiServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/reported-adjudications/$CHARGE_NUMBER/v2")))
        NomisApiExtension.nomisApi.verify(
          WireMock.postRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings/$NOMIS_HEARING_ID/charge/$CHARGE_SEQUENCE/result"))
            .withRequestBody(WireMock.matchingJsonPath("$.adjudicatorUsername", WireMock.equalTo("jack_b"))),
        )

        verifyHearingResultCreatedSuccessCustomEvent(plea = "NOT_ASKED", findingCode = "ADJOURNED")
      }
    }

    @Nested
    inner class WhenHearingHasAnOutcomeOfCompleted {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER, ADJUDICATION_NUMBER)
        AdjudicationsApiExtension.adjudicationsApiServer.stubChargeGetWithCompletedOutcome(
          hearingId = DPS_HEARING_ID.toLong(),
          chargeNumber = CHARGE_NUMBER,
          offenderNo = OFFENDER_NO,
        )
        NomisApiExtension.nomisApi.stubHearingResultUpsert(ADJUDICATION_NUMBER, NOMIS_HEARING_ID)
        MappingExtension.mappingServer.stubGetByDpsHearingId(DPS_HEARING_ID, NOMIS_HEARING_ID)
        publishCreateHearingCompletedDomainEvent()
      }

      @Test
      fun `will callback back to adjudication service, post the create and track success`() {
        waitForCreateHearingProcessingToBeComplete()

        AdjudicationsApiExtension.adjudicationsApiServer.verify(WireMock.getRequestedFor(WireMock.urlEqualTo("/reported-adjudications/$CHARGE_NUMBER/v2")))
        NomisApiExtension.nomisApi.verify(
          WireMock.postRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings/$NOMIS_HEARING_ID/charge/$CHARGE_SEQUENCE/result"))
            .withRequestBody(WireMock.matchingJsonPath("$.adjudicatorUsername", WireMock.equalTo("SWATSON_GEN"))),
        )

        verifyHearingResultCreatedSuccessCustomEvent(findingCode = "PROVED", plea = "GUILTY")
      }
    }

    @Nested
    inner class invalidFindingCodeFromDPS {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER, ADJUDICATION_NUMBER)
        AdjudicationsApiExtension.adjudicationsApiServer.stubChargeGetWithCompletedOutcome(
          hearingId = DPS_HEARING_ID.toLong(),
          chargeNumber = CHARGE_NUMBER,
          offenderNo = OFFENDER_NO,
          outcomeFindingCode = OutcomeDto.Code.SCHEDULE_HEARING.name,
        )
        MappingExtension.mappingServer.stubGetByDpsHearingId(DPS_HEARING_ID, NOMIS_HEARING_ID)
        publishCreateHearingCompletedDomainEvent()
      }

      @Test
      fun `invalid finding code is rejected`() {
        await untilAsserted {
          verify(telemetryClient, Mockito.times(3)).trackEvent(
            eq("hearing-result-created-failed"),
            org.mockito.kotlin.check {
              Assertions.assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER)
              Assertions.assertThat(it["prisonerNumber"]).isEqualTo(OFFENDER_NO)
              Assertions.assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
              Assertions.assertThat(it["dpsHearingId"]).isEqualTo(DPS_HEARING_ID)
              Assertions.assertThat(it["nomisHearingId"]).isEqualTo(NOMIS_HEARING_ID.toString())
            },
            isNull(),
          )
        }
      }
    }

    private fun verifyHearingResultCreatedSuccessCustomEvent(
      findingCode: String = "CHARGE_PROVEN",
      plea: String = "NOT_GUILTY",
    ) {
      verify(telemetryClient).trackEvent(
        eq("hearing-result-created-success"),
        org.mockito.kotlin.check {
          Assertions.assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER)
          Assertions.assertThat(it["prisonerNumber"]).isEqualTo(OFFENDER_NO)
          Assertions.assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
          Assertions.assertThat(it["dpsHearingId"]).isEqualTo(DPS_HEARING_ID)
          Assertions.assertThat(it["findingCode"]).isEqualTo(findingCode)
          Assertions.assertThat(it["plea"]).isEqualTo(plea)
        },
        isNull(),
      )
    }

    @Nested
    inner class WhenHearingMappingNotFound {

      @BeforeEach
      fun setUp() {
        AdjudicationsApiExtension.adjudicationsApiServer.stubChargeGetWithCompletedOutcome(
          hearingId = DPS_HEARING_ID.toLong(),
          chargeNumber = CHARGE_NUMBER,
          offenderNo = OFFENDER_NO,
        )
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER, ADJUDICATION_NUMBER)
        MappingExtension.mappingServer.stubGetByDpsHearingIdWithError(hearingId = DPS_HEARING_ID, 404)
        publishCreateHearingCompletedDomainEvent()
      }

      @Test
      fun `will not create a hearing result in NOMIS`() {
        await untilAsserted {
          verify(telemetryClient, times(3)).trackEvent(
            eq("hearing-result-created-failed"),
            org.mockito.kotlin.check {
              Assertions.assertThat(it["dpsHearingId"]).isEqualTo(DPS_HEARING_ID)
              Assertions.assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
              Assertions.assertThat(it["prisonerNumber"]).isEqualTo(OFFENDER_NO)
              Assertions.assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER)
              Assertions.assertThat(it["adjudicationNumber"]).isEqualTo(ADJUDICATION_NUMBER.toString())
              Assertions.assertThat(it["chargeSequence"]).isEqualTo(CHARGE_SEQUENCE.toString())
            },
            isNull(),
          )

          NomisApiExtension.nomisApi.verify(
            0,
            WireMock.postRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings")),
          )
        }
      }
    }

    private fun waitForCreateHearingProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  @Nested
  inner class DeleteHearingCompleted {
    @Nested
    inner class WhenHearingResultHasBeenDeletedInDPS {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER, ADJUDICATION_NUMBER)
        MappingExtension.mappingServer.stubGetByDpsHearingId(DPS_HEARING_ID, NOMIS_HEARING_ID)
        AdjudicationsApiExtension.adjudicationsApiServer.stubChargeGet(
          chargeNumber = CHARGE_NUMBER,
          offenderNo = OFFENDER_NO,
        )
        NomisApiExtension.nomisApi.stubHearingResultDelete(ADJUDICATION_NUMBER, NOMIS_HEARING_ID, CHARGE_SEQUENCE)
        publishDeleteHearingCompletedDomainEvent()
      }

      @Test
      fun `will create success telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("hearing-result-deleted-success"),
            org.mockito.kotlin.check {
              Assertions.assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER)
              Assertions.assertThat(it["prisonerNumber"]).isEqualTo(OFFENDER_NO)
              Assertions.assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
              Assertions.assertThat(it["dpsHearingId"]).isEqualTo(DPS_HEARING_ID)
              Assertions.assertThat(it["nomisHearingId"]).isEqualTo(NOMIS_HEARING_ID.toString())
            },
            isNull(),
          )
        }
      }

      @Test
      fun `will call nomis api to delete the hearing result`() {
        waitForHearingProcessingToBeComplete()
        NomisApiExtension.nomisApi.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings/$NOMIS_HEARING_ID/charge/$CHARGE_SEQUENCE/result")))
      }
    }

    @Nested
    inner class WhenNoMappingExistsForHearing {

      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER, ADJUDICATION_NUMBER)
        MappingExtension.mappingServer.stubGetByDpsHearingIdWithError(hearingId = DPS_HEARING_ID, 404)
        publishDeleteHearingCompletedDomainEvent()
      }

      @Test
      fun `will not attempt to delete a hearing in NOMIS`() {
        await untilAsserted {
          verify(telemetryClient, times(3)).trackEvent(
            eq("hearing-result-deleted-failed"),
            org.mockito.kotlin.check {
              Assertions.assertThat(it["dpsHearingId"]).isEqualTo(DPS_HEARING_ID)
              Assertions.assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
              Assertions.assertThat(it["prisonerNumber"]).isEqualTo(OFFENDER_NO)
              Assertions.assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER)
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
  inner class DeleteHearingAdjourn {
    @Nested
    inner class WhenHearingResultHasBeenDeletedInDPS {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER, ADJUDICATION_NUMBER)
        MappingExtension.mappingServer.stubGetByDpsHearingId(DPS_HEARING_ID, NOMIS_HEARING_ID)
        AdjudicationsApiExtension.adjudicationsApiServer.stubChargeGet(
          chargeNumber = CHARGE_NUMBER,
          offenderNo = OFFENDER_NO,
        )
        NomisApiExtension.nomisApi.stubHearingResultDelete(ADJUDICATION_NUMBER, NOMIS_HEARING_ID, CHARGE_SEQUENCE)
        publishDeleteHearingAdjournedDomainEvent()
      }

      @Test
      fun `will create success telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("hearing-result-deleted-success"),
            org.mockito.kotlin.check {
              Assertions.assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER)
              Assertions.assertThat(it["prisonerNumber"]).isEqualTo(OFFENDER_NO)
              Assertions.assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
              Assertions.assertThat(it["dpsHearingId"]).isEqualTo(DPS_HEARING_ID)
              Assertions.assertThat(it["nomisHearingId"]).isEqualTo(NOMIS_HEARING_ID.toString())
            },
            isNull(),
          )
        }
      }

      @Test
      fun `will call nomis api to delete the hearing result`() {
        waitForHearingProcessingToBeComplete()
        NomisApiExtension.nomisApi.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings/$NOMIS_HEARING_ID/charge/$CHARGE_SEQUENCE/result")))
      }
    }

    private fun waitForHearingProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  @Nested
  inner class DeleteHearingReferral {
    @Nested
    inner class WhenHearingResultHasBeenDeletedInDPS {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER, ADJUDICATION_NUMBER)
        MappingExtension.mappingServer.stubGetByDpsHearingId(DPS_HEARING_ID, NOMIS_HEARING_ID)
        NomisApiExtension.nomisApi.stubHearingResultDelete(ADJUDICATION_NUMBER, NOMIS_HEARING_ID, CHARGE_SEQUENCE)
        publishDeleteHearingReferralDomainEvent()
      }

      @Test
      fun `will create success telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("hearing-result-deleted-success"),
            org.mockito.kotlin.check {
              Assertions.assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER)
              Assertions.assertThat(it["prisonerNumber"]).isEqualTo(OFFENDER_NO)
              Assertions.assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
              Assertions.assertThat(it["dpsHearingId"]).isEqualTo(DPS_HEARING_ID)
              Assertions.assertThat(it["nomisHearingId"]).isEqualTo(NOMIS_HEARING_ID.toString())
            },
            isNull(),
          )
        }
      }

      @Test
      fun `will call nomis api to delete the hearing result`() {
        waitForHearingProcessingToBeComplete()
        NomisApiExtension.nomisApi.verify(WireMock.deleteRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings/$NOMIS_HEARING_ID/charge/$CHARGE_SEQUENCE/result")))
      }
    }

    private fun waitForHearingProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  @Nested
  inner class DeleteHearingReferralOutcome {
    @Nested
    inner class WhenHearingReferralOutcomeHasBeenDeletedInDPS {
      @BeforeEach
      fun setUp() {
        MappingExtension.mappingServer.stubGetByChargeNumber(CHARGE_NUMBER, ADJUDICATION_NUMBER)
        MappingExtension.mappingServer.stubGetByDpsHearingId(DPS_HEARING_ID, NOMIS_HEARING_ID)
        AdjudicationsApiExtension.adjudicationsApiServer.stubChargeGetWithReferPoliceOutcome(
          hearingId = DPS_HEARING_ID.toLong(),
          chargeNumber = CHARGE_NUMBER,
          offenderNo = OFFENDER_NO,
        )
        NomisApiExtension.nomisApi.stubHearingResultUpsert(ADJUDICATION_NUMBER, NOMIS_HEARING_ID, CHARGE_SEQUENCE)
        publishDeleteHearingReferralOutcomeDomainEvent()
      }

      @Test
      fun `will create success telemetry`() {
        await untilAsserted {
          verify(telemetryClient).trackEvent(
            eq("hearing-result-deleted-success"),
            org.mockito.kotlin.check {
              Assertions.assertThat(it["chargeNumber"]).isEqualTo(CHARGE_NUMBER)
              Assertions.assertThat(it["prisonerNumber"]).isEqualTo(OFFENDER_NO)
              Assertions.assertThat(it["prisonId"]).isEqualTo(PRISON_ID)
              Assertions.assertThat(it["dpsHearingId"]).isEqualTo(DPS_HEARING_ID)
              Assertions.assertThat(it["nomisHearingId"]).isEqualTo(NOMIS_HEARING_ID.toString())
            },
            isNull(),
          )
        }
      }

      @Test
      fun `will call nomis api to amend the hearing result (roll back to ref_police outcome)`() {
        waitForHearingProcessingToBeComplete()
        NomisApiExtension.nomisApi.verify(
          WireMock.postRequestedFor(WireMock.urlEqualTo("/adjudications/adjudication-number/$ADJUDICATION_NUMBER/hearings/$NOMIS_HEARING_ID/charge/$CHARGE_SEQUENCE/result"))
            .withRequestBody(WireMock.matchingJsonPath("$.findingCode", WireMock.equalTo("REF_POLICE"))),
        )
      }
    }

    private fun waitForHearingProcessingToBeComplete() {
      await untilAsserted { verify(telemetryClient).trackEvent(any(), any(), isNull()) }
    }
  }

  private fun publishCreateHearingCompletedDomainEvent() {
    val eventType = "adjudication.hearingCompleted.created"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(hearingMessagePayload(DPS_HEARING_ID, CHARGE_NUMBER, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishCreateHearingAdjournedDomainEvent() {
    val eventType = "adjudication.hearingAdjourn.created"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(hearingMessagePayload(DPS_HEARING_ID, CHARGE_NUMBER, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishCreateHearingReferredDomainEvent() {
    val eventType = "adjudication.hearingReferral.created"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(hearingMessagePayload(DPS_HEARING_ID, CHARGE_NUMBER, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishCreateHearingReferralProsecutionDomainEvent() {
    val eventType = "adjudication.referral.outcome.prosecution"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(hearingMessagePayload(DPS_HEARING_ID, CHARGE_NUMBER, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishCreateHearingReferralNotProceedDomainEvent() {
    val eventType = "adjudication.referral.outcome.notProceed"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(hearingMessagePayload(DPS_HEARING_ID, CHARGE_NUMBER, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishCreateHearingReferralReferGovDomainEvent() {
    val eventType = "adjudication.referral.outcome.referGov"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(hearingMessagePayload(DPS_HEARING_ID, CHARGE_NUMBER, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishDeleteHearingReferralOutcomeDomainEvent() {
    val eventType = "adjudication.referral.outcome.deleted"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(hearingMessagePayload(DPS_HEARING_ID, CHARGE_NUMBER, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishDeleteHearingCompletedDomainEvent() {
    val eventType = "adjudication.hearingCompleted.deleted"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(hearingMessagePayload(DPS_HEARING_ID, CHARGE_NUMBER, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishDeleteHearingAdjournedDomainEvent() {
    val eventType = "adjudication.hearingAdjourn.deleted"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(hearingMessagePayload(DPS_HEARING_ID, CHARGE_NUMBER, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  private fun publishDeleteHearingReferralDomainEvent() {
    val eventType = "adjudication.hearingReferral.deleted"
    awsSnsClient.publish(
      PublishRequest.builder().topicArn(topicArn)
        .message(hearingMessagePayload(DPS_HEARING_ID, CHARGE_NUMBER, PRISON_ID, OFFENDER_NO, eventType))
        .messageAttributes(
          mapOf(
            "eventType" to MessageAttributeValue.builder().dataType("String")
              .stringValue(eventType).build(),
          ),
        ).build(),
    ).get()
  }

  fun hearingMessagePayload(
    hearingId: String,
    chargeNumber: String,
    prisonId: String,
    prisonerNumber: String,
    eventType: String,
    status: String = "REFER_POLICE",
  ) =
    """{"eventType":"$eventType", "additionalInformation": {"chargeNumber":"$chargeNumber", "prisonId": "$prisonId", "hearingId": "$hearingId", "prisonerNumber": "$prisonerNumber", "status": "$status"}}"""
}
