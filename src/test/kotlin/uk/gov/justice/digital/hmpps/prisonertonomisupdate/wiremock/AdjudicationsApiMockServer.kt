package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedAdjudicationDto

class AdjudicationsApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val adjudicationsApiServer = AdjudicationsApiMockServer()
    lateinit var objectMapper: ObjectMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
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
    punishments: String = "[]",
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
        "punishments": $punishments,
        "punishmentComments": [],
        "linkedChargeNumbers": [],
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

  fun stubChargeGetWithCompletedOutcome(
    hearingId: Long = 123,
    chargeNumber: String,
    offenderNo: String = "A7937DY",
    outcomeFindingCode: String = "CHARGE_PROVED",
  ) {
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
                        "code": "$outcomeFindingCode"
                    }
                }
            }
        ]
    """.trimIndent()
    stubChargeGet(chargeNumber = chargeNumber, offenderNo = offenderNo, outcomes = outcomes)
  }

  fun stubChargeGetWithHearingAndReferIndependentAdjudicatorOutcome(
    hearingId: Long = 123,
    chargeNumber: String,
    offenderNo: String = "A7937DY",
  ) {
    stubChargeGetWithHearingAndSeparateOutcomeBlock(
      hearingId = hearingId,
      outcomeCode = "REFER_INAD",
      chargeNumber = chargeNumber,
      offenderNo = offenderNo,
    )
  }

  fun stubChargeGetWithHearingAndReferPoliceOutcome(hearingId: Long = 123, chargeNumber: String, offenderNo: String = "A7937DY") {
    stubChargeGetWithHearingAndSeparateOutcomeBlock(
      hearingId = hearingId,
      outcomeCode = "REFER_POLICE",
      chargeNumber = chargeNumber,
      offenderNo = offenderNo,
    )
  }

  private fun stubChargeGetWithHearingAndSeparateOutcomeBlock(
    outcomeCode: String,
    hearingId: Long = 123,
    chargeNumber: String,
    offenderNo: String = "A7937DY",
  ) {
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
              "code": "$outcomeCode",
              "details": "pdfs"
              },
            "agencyId": "MDI"
          },
          "outcome": {
              "outcome": {
              "id": 1238,
              "code": "$outcomeCode",
              "details": "pdfs"
            }
          }
      }
  ]
    """.trimIndent()
    stubChargeGet(chargeNumber = chargeNumber, offenderNo = offenderNo, outcomes = outcomes)
  }

  fun stubChargeGetWithReferralOutcome(
    outcomeCode: String = "REFER_POLICE",
    referralOutcomeCode: String = "PROSECUTION",
    hearingId: Long = 123,
    chargeNumber: String,
    offenderNo: String = "A7937DY",
    hearingType: String = "INAD_ADULT",
  ) {
    val outcomes = """
     [
            {
                "hearing": {
                    "id": $hearingId,
                    "locationId": 357596,
                    "dateTimeOfHearing": "2023-10-12T14:00:00",
                    "oicHearingType": "$hearingType",
                    "outcome": {
                        "id": 1031,
                        "adjudicator": "jack_b",
                        "code": "$outcomeCode",
                        "details": "yuiuy"
                    },
                    "agencyId": "MDI"
                },
                "outcome": {
                    "outcome": {
                        "id": 1319,
                        "code": "$outcomeCode",
                        "details": "yuiuy"
                    },
                    "referralOutcome": {
                        "id": 1320,
                        "code": "$referralOutcomeCode"
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

  fun stubChargeGetWithPoliceReferral(chargeNumber: String, offenderNo: String = "A7937DY") {
    val outcomes = """
     [
         {
             "outcome": {
                 "outcome": {
                     "id": 1411,
                     "code": "REFER_POLICE",
                     "details": "eewr"
                 }
             }
         }
     ]
    """.trimIndent()
    stubChargeGet(chargeNumber = chargeNumber, offenderNo = offenderNo, outcomes = outcomes)
  }

  fun stubChargeGetWithNotProceedReferral(chargeNumber: String, offenderNo: String = "A7937DY") {
    val outcomes = """
     [
           {
               "outcome": {
                   "outcome": {
                       "id": 1412,
                       "code": "NOT_PROCEED",
                       "details": "dssds",
                       "reason": "EXPIRED_NOTICE"
                   }
               }
           }
       ]
    """.trimIndent()
    stubChargeGet(chargeNumber = chargeNumber, offenderNo = offenderNo, outcomes = outcomes)
  }

  fun stubChargeGetWithHearingFollowingReferral(chargeNumber: String, offenderNo: String = "A7937DY") {
    val outcomes = """
    [
    {
        "outcome": {
          "outcome": {
          "id": 1492,
          "code": "REFER_POLICE",
          "details": "fdggre"
        },
          "referralOutcome": {
          "id": 1493,
          "code": "SCHEDULE_HEARING"
        }
      }
    },
    {
        "hearing": {
          "id": 816,
          "locationId": 357596,
          "dateTimeOfHearing": "2023-10-26T16:10:00",
          "oicHearingType": "INAD_ADULT",
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

  fun stubGetAdjudicationsByBookingId(bookingId: Long, adjudications: List<ReportedAdjudicationDto> = emptyList()) {
    stubFor(
      get(
        WireMock.urlPathEqualTo("/reported-adjudications/all-by-booking/$bookingId"),
      )
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(adjudications),
        ),
    )
  }

  fun stubGetAdjudicationsByBookingIdWithError(bookingId: Long, status: Int) {
    stubFor(
      get(
        WireMock.urlPathEqualTo("/reported-adjudications/all-by-booking/$bookingId"),
      ).willReturn(
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

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder {
    this.withBody(AdjudicationsApiExtension.objectMapper.writeValueAsString(body))
    return this
  }
}
