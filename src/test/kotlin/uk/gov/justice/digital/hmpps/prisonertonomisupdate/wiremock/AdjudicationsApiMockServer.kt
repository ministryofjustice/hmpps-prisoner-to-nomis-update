package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class AdjudicationsApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val adjudicationsApiServer = AdjudicationsApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    adjudicationsApiServer.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    adjudicationsApiServer.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    adjudicationsApiServer.stop()
  }
}

class AdjudicationsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8089
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  fun stubChargeGet(
    chargeNumber: String,
    offenderNo: String = "A7937DY",
    outcomes: String = "[]",
    damages: String = "[]",
    evidence: String = "[]",
    status: String = "UNSCHEDULED",
  ) {
    stubFor(
      get("/reported-adjudications/$chargeNumber/v2").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
{
    "reportedAdjudication": {
        "chargeNumber": "$chargeNumber",
        "prisonerNumber": "$offenderNo",
        "gender": "MALE",
        "incidentDetails": {
            "locationId": 197683,
            "dateTimeOfIncident": "2023-07-11T09:00:00",
            "dateTimeOfDiscovery": "2023-07-11T09:00:00",
            "handoverDeadline": "2023-07-13T09:00:00"
        },
        "isYouthOffender": false,
        "incidentRole": {},
        "offenceDetails": {
            "offenceCode": 16001,
            "offenceRule": {
                "paragraphNumber": "1",
                "paragraphDescription": "Commits any assault",
                "nomisCode": "51:1B"
            }
        },
        "incidentStatement": {
            "statement": "12",
            "completed": true
        },
        "createdByUserId": "TWRIGHT",
        "createdDateTime": "2023-07-25T15:19:37.476664",
        "status": "$status",
        "reviewedByUserId": "AMARKE_GEN",
        "statusReason": "",
        "statusDetails": "",
        "damages": $damages,
        "evidence": $evidence,
        "witnesses": [],
        "hearings": [{
                "id": 345,
                "locationId": 27187,
                "dateTimeOfHearing": "2023-08-23T14:25:00",
                "oicHearingType": "GOV_ADULT",
                "agencyId": "MDI",
                  "outcome": {
                        "id": 962,
                        "adjudicator": "JBULLENGEN",
                        "code": "COMPLETE",
                        "plea": "GUILTY"
                    }
            }],
        "disIssueHistory": [],
        "outcomes": $outcomes,
        "punishments": [],
        "punishmentComments": [],
        "outcomeEnteredInNomis": false,
        "originatingAgencyId": "MDI"
    }
}              
            """.trimIndent(),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubChargeGetWithCompletedOutcome(hearingId: Long = 123, chargeNumber: String, offenderNo: String = "A7937DY") {
    val outcomes = """
        [
            {
                "hearing": {
                    "id": $hearingId,
                    "locationId": 27187,
                    "dateTimeOfHearing": "2023-04-27T17:45:00",
                    "oicHearingType": "GOV_ADULT",
                    "outcome": {
                        "id": 407,
                        "adjudicator": "SWATSON_GEN",
                        "code": "COMPLETE",
                        "plea": "GUILTY"
                    },
                    "agencyId": "MDI"
                },
                "outcome": {
                    "outcome": {
                        "id": 591,
                        "code": "CHARGE_PROVED"
                    }
                }
            }
        ]
    """.trimIndent()
    stubChargeGet(chargeNumber = chargeNumber, offenderNo = offenderNo, outcomes = outcomes)
  }

  fun stubChargeGetWithReferIndependentAdjudicatorOutcome(hearingId: Long = 123, chargeNumber: String, offenderNo: String = "A7937DY") {
    stubChargeGetWithReferralOutcome(hearingId = hearingId, referralOutcomeCode = "REFER_INAD", chargeNumber = chargeNumber, offenderNo = offenderNo)
  }

  fun stubChargeGetWithReferPoliceOutcome(hearingId: Long = 123, chargeNumber: String, offenderNo: String = "A7937DY") {
    stubChargeGetWithReferralOutcome(hearingId = hearingId, referralOutcomeCode = "REFER_POLICE", chargeNumber = chargeNumber, offenderNo = offenderNo)
  }

  private fun stubChargeGetWithReferralOutcome(referralOutcomeCode: String, hearingId: Long = 123, chargeNumber: String, offenderNo: String = "A7937DY") {
    val outcomes = """
     [
      {
          "hearing": {
            "id": $hearingId,
            "locationId": 27187,
            "dateTimeOfHearing": "2023-10-04T13:20:00",
            "oicHearingType": "GOV_ADULT",
            "outcome": {
              "id": 975,
              "adjudicator": "JBULLENGEN",
              "code": "$referralOutcomeCode",
              "details": "pdfs"
              },
            "agencyId": "MDI"
          },
          "outcome": {
              "outcome": {
              "id": 1238,
              "code": "$referralOutcomeCode",
              "details": "pdfs"
            }
          }
      }
  ]
    """.trimIndent()
    stubChargeGet(chargeNumber = chargeNumber, offenderNo = offenderNo, outcomes = outcomes)
  }

  // no separate outcome block for Adjourn
  fun stubChargeGetWithAdjournOutcome(hearingId: Long = 123, chargeNumber: String, offenderNo: String = "A7937DY") {
    val outcomes = """
     [
            {
                "hearing": {
                    "id": $hearingId,
                    "locationId": 27187,
                    "dateTimeOfHearing": "2023-10-04T13:20:00",
                    "oicHearingType": "GOV_ADULT",
                    "outcome": {
                        "id": 976,
                        "adjudicator": "JBULLENGEN",
                        "code": "ADJOURN",
                        "reason": "RO_ATTEND",
                        "details": "cxvcx",
                        "plea": "UNFIT"
                    },
                    "agencyId": "MDI"
                }
            }
        ]
    """.trimIndent()
    stubChargeGet(chargeNumber = chargeNumber, offenderNo = offenderNo, outcomes = outcomes)
  }

  fun stubChargeGetWithError(chargeNumber: String, status: Int) {
    stubFor(
      get("/reported-adjudications/$chargeNumber/v2").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              {
                "error": "some error"
              }
            """.trimIndent(),
          )
          .withStatus(status),
      ),
    )
  }

  fun getCountFor(url: String) = this.findAll(WireMock.getRequestedFor(WireMock.urlEqualTo(url))).count()
}
