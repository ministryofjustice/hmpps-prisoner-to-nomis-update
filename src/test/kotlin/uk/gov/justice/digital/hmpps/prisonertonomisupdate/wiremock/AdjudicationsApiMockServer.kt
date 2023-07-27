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
    "reportedAdjudication": {
        "adjudicationNumber": ${chargeNumber.toLong()},
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
                "paragraphNumber": "16",
                "paragraphDescription": "Intentionally or recklessly sets fire to any part of a prison or any other property, whether or not his own"
            }
        },
        "incidentStatement": {
            "statement": "12",
            "completed": true
        },
        "createdByUserId": "TWRIGHT",
        "createdDateTime": "2023-07-25T15:19:37.476664",
        "status": "UNSCHEDULED",
        "reviewedByUserId": "AMARKE_GEN",
        "statusReason": "",
        "statusDetails": "",
        "damages": [],
        "evidence": [],
        "witnesses": [],
        "hearings": [],
        "disIssueHistory": [],
        "outcomes": [],
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
