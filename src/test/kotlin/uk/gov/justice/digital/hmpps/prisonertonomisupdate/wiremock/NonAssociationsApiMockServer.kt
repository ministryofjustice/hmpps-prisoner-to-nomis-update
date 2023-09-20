package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class NonAssociationsApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val nonAssociationsApiServer = NonAssociationsApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    nonAssociationsApiServer.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    nonAssociationsApiServer.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    nonAssociationsApiServer.stop()
  }
}

class NonAssociationsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8091
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

  fun stubGetNonAssociation(id: Long, response: String) {
    stubFor(
      get("/legacy/api/non-associations/$id").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200),
      ),
    )
  }

  fun stubGetNonAssociationWithError(id: Long, status: Int = 500) {
    stubFor(
      get("/legacy/api/non-associations/$id").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "error": "some error" }""")
          .withStatus(status),
      ),
    )
  }

  fun stubGetNonAssociationWithErrorFollowedBySlowSuccess(id: Long, response: String) {
    stubFor(
      get("/legacy/api/non-associations/$id")
        .inScenario("Retry NonAssociations Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause NonAssociations Success"),
    )

    stubFor(
      get("/legacy/api/non-associations/$id")
        .inScenario("Retry NonAssociations Scenario")
        .whenScenarioStateIs("Cause NonAssociations Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(response)
            .withStatus(200)
            .withFixedDelay(500),
        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetNonAssociationsBetween(offender1: String, offender2: String, response: String) {
    stubFor(
      post(urlPathEqualTo("/non-associations/between"))
        .withRequestBody(WireMock.equalToJson("""["$offender1","$offender2"]"""))
        .willReturn(okJson(response)),
    )
  }

  fun stubGetNonAssociationsBetweenWithError(offender1: String, offender2: String, status: Int = 500) {
    stubFor(
      post(urlPathEqualTo("/non-associations/between"))
        .withRequestBody(WireMock.equalToJson("""["$offender1","$offender2"]"""))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("""{ "error": "some error" }""")
            .withStatus(status),
        ),
    )
  }

  fun stubGetNonAssociationsPage(pageNumber: Long, pageSize: Long = 100, response: String) {
    stubFor(
      get(urlPathEqualTo("/non-associations"))
        .withQueryParam("page", WireMock.equalTo(pageNumber.toString()))
        .withQueryParam("size", WireMock.equalTo(pageSize.toString()))
        .willReturn(okJson(response)),
    )
  }

  fun getCountFor(url: String) = this.findAll(WireMock.getRequestedFor(WireMock.urlEqualTo(url))).count()
}
