package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
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

  fun stubChargeGet(chargeNumber: String, offenderNo: String = "A7937DY") {
    stubFor(
      get("/reported-adjudications/$chargeNumber/v2").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
{
        "adjudicationNumber": ${chargeNumber.toLong()},
        "chargeNumber": "$chargeNumber",
        "prisonerNumber": "$offenderNo",
        "gender": "FEMALE",
        "incidentDetails": {
            "locationId": 27002,
            "dateTimeOfIncident": "2023-07-26T09:00:00",
            "dateTimeOfDiscovery": "2023-07-26T09:00:00",
            "handoverDeadline": "2023-07-28T09:00:00"
        },
        "isYouthOffender": false,
        "incidentRole": {},
        "offenceDetails": {
            "offenceCode": 24101,
            "offenceRule": {
                "paragraphNumber": "24(a)",
                "paragraphDescription": "Displays, attaches or draws on any part of a prison, or on any other property, threatening, abusive or insulting racist words, drawings, symbols or other material"
            }
        },
        "incidentStatement": {
            "statement": "123456",
            "completed": true
        },
        "createdByUserId": "NCLAMP_GEN",
        "createdDateTime": "2023-07-26T10:53:19.280167",
        "status": "CHARGE_PROVED",
        "reviewedByUserId": "NCLAMP_GEN",
        "statusReason": "",
        "statusDetails": "",
        "damages": [],
        "evidence": [],
        "witnesses": [],
        "hearings": [
            {
                "id": 532,
                "locationId": 357596,
                "dateTimeOfHearing": "2023-07-26T16:00:00",
                "oicHearingType": "INAD_ADULT",
                "outcome": {
                    "id": 703,
                    "adjudicator": "Jacob Marley",
                    "code": "COMPLETE",
                    "plea": "GUILTY"
                },
                "agencyId": "MDI"
            }
        ],
        "disIssueHistory": [],
        "dateTimeOfFirstHearing": "2023-07-26T16:00:00",
        "outcomes": [
            {
                "hearing": {
                    "id": 532,
                    "locationId": 357596,
                    "dateTimeOfHearing": "2023-07-26T16:00:00",
                    "oicHearingType": "INAD_ADULT",
                    "outcome": {
                        "id": 703,
                        "adjudicator": "Jacob Marley",
                        "code": "COMPLETE",
                        "plea": "GUILTY"
                    },
                    "agencyId": "MDI"
                },
                "outcome": {
                    "outcome": {
                        "id": 877,
                        "code": "CHARGE_PROVED"
                    }
                }
            }
        ],
        "punishments": [
            {
                "id": 371,
                "type": "ADDITIONAL_DAYS",
                "schedule": {
                    "days": 10
                },
                "consecutiveReportNumber": 1525851,
                "consecutiveChargeNumber": "1525851",
                "consecutiveReportAvailable": true
            },
            {
                "id": 373,
                "type": "ADDITIONAL_DAYS",
                "activatedFrom": "1525853",
                "schedule": {
                    "days": 20
                }
            }
        ],
        "punishmentComments": [],
        "outcomeEnteredInNomis": false,
        "originatingAgencyId": "MDI"
}
            """.trimIndent(),
          )
          .withStatus(200),
      ),
    )
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
}
