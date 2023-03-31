package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class IncentivesApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
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
      get("/iep/reviews/id/$id").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200),
      ),
    )
  }

  fun stubIncentiveGetWithError(id: Long, status: Int = 500) {
    stubFor(
      get("/iep/reviews/id/$id").willReturn(
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
      get("/incentive/levels/$incentiveCode?with-inactive=true").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            {
              "code": "$incentiveCode",
              "description": "Description for $incentiveCode",
              "active": true,
              "required": true
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
                    "description": "Basic",
                    "active": true,
                    "required": true
                },
                {
                    "code": "STD",
                    "description": "Standard",
                    "active": true,
                    "required": true
                },
                {
                    "code": "ENH",
                    "description": "Enhanced",
                    "active": true,
                    "required": true
                },
                {
                    "code": "EN2",
                    "description": "Enhanced 2",
                    "active": true,
                    "required": false
                },
                {
                    "code": "EN3",
                    "description": "Enhanced 3",
                    "active": true,
                    "required": false
                },
                {
                    "code": "ENT",
                    "description": "Entry",
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

  fun getCountFor(url: String) = this.findAll(WireMock.getRequestedFor(WireMock.urlEqualTo(url))).count()
}
