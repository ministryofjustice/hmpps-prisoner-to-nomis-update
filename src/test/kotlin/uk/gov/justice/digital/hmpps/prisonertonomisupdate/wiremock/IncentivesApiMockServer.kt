package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class IncentivesApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val incentivesApi = IncentivesApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    incentivesApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    incentivesApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    incentivesApi.stop()
  }
}

class IncentivesApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8085
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

  fun stubIncentiveGet(id: Long, response: String) {
    stubFor(
      get("/incentive-reviews/id/$id").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200),
      ),
    )
  }

  fun stubIncentiveGetWithError(id: Long, status: Int = 500) {
    stubFor(
      get("/incentive-reviews/id/$id").willReturn(
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

  fun stubGlobalIncentiveLevelGet(incentiveCode: String? = "STD") {
    stubFor(
      get("/incentive/levels/$incentiveCode").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            {
              "code": "$incentiveCode",
              "name": "Description for $incentiveCode",
              "active": true,
              "required": true,
              "systemDataFlag": false
            }
            """,
          )
          .withStatus(200),
      ),
    )
  }

  fun stubGlobalIncentiveLevelsGet() {
    stubFor(
      get("/incentive/levels?with-inactive=true").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            [
                {
                    "code": "BAS",
                    "name": "Basic",
                    "active": true,
                    "required": true
                },
                {
                    "code": "STD",
                    "name": "Standard",
                    "active": true,
                    "required": true
                },
                {
                    "code": "ENH",
                    "name": "Enhanced",
                    "active": true,
                    "required": true
                },
                {
                    "code": "EN2",
                    "name": "Enhanced 2",
                    "active": true,
                    "required": false
                },
                {
                    "code": "EN3",
                    "name": "Enhanced 3",
                    "active": true,
                    "required": false
                },
                {
                    "code": "ENT",
                    "name": "Entry",
                    "active": false,
                    "required": false
                }
            ]
            """,
          )
          .withStatus(200),
      ),
    )
  }

  fun stubCurrentIncentiveGet(bookingId: Long, iepCode: String = "STD", id: Long = 7654, prisonerNumber: String = "A1234AA") {
    stubFor(
      get("/incentive-reviews/booking/$bookingId?with-details=false").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              {
                "id": $id,
                "iepCode": "$iepCode",
                "iepLevel": "5",
                "prisonerNumber": "$prisonerNumber",
                "bookingId": 12345,
                "iepDate": "2023-02-12",
                "iepTime": "2023-02-12T10:23:45",
                "iepDetails": [],
                "nextReviewDate": "2020-01-02",
                "daysSinceReview": 23
              }
            """.trimMargin(),
          )
          .withFixedDelay(500)
          .withStatus(200),
      ),
    )
  }

  fun stubCurrentIncentiveGetWithError(bookingId: Long, responseCode: Int) {
    stubFor(
      get("/incentive-reviews/booking/$bookingId?with-details=false").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(responseCode)
          .withBody("""{"message":"Error"}"""),
      ),
    )
  }

  fun stubPrisonIncentiveLevelGet(incentiveCode: String? = "STD", prisonId: String? = "MDI", defaultOnAdmission: Boolean? = true) {
    stubFor(
      get("/incentive/prison-levels/$prisonId/level/$incentiveCode").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            {
              "levelCode": "$incentiveCode",
              "levelName": "Standard",
              "prisonId": "$prisonId",
              "active": true,
              "defaultOnAdmission": $defaultOnAdmission,
              "remandTransferLimitInPence": 6050,
              "remandSpendLimitInPence": 60500,
              "convictedTransferLimitInPence": 1980,
              "convictedSpendLimitInPence": 19800,
              "visitOrders": 1,
              "privilegedVisitOrders": 2
            }
            """,
          )
          .withStatus(200),
      ),
    )
  }

  fun getCountFor(url: String) = this.findAll(WireMock.getRequestedFor(WireMock.urlEqualTo(url))).count()
}
