package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class LocationsApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val locationsApi = LocationsApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    locationsApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    locationsApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    locationsApi.stop()
  }
}

class LocationsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8093
  }

  // WIP /////////////////////////////////////////////////

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(WireMock.okJson(if (status == 200) "pong" else "some error")),
    )
  }

  fun stubGetLocation(id: String, response: String) {
    stubFor(
      get("/locations/$id").willReturn(WireMock.okJson(response)),
    )
  }

  fun stubGetLocationWithError(id: String, status: Int = 500) {
    stubFor(
      get("/locations/$id").willReturn(
        WireMock.jsonResponse("""{ "error": "some error" }""", status),
      ),
    )
  }

  fun stubGetLocationWithErrorFollowedBySlowSuccess(id: String, response: String) {
    stubFor(
      get("/locations/$id")
        .inScenario("Retry Locations Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause Locations Success"),
    )

    stubFor(
      get("/locations/$id")
        .inScenario("Retry Locations Scenario")
        .whenScenarioStateIs("Cause Locations Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(response)
            .withStatus(200)
            .withFixedDelay(500),
        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetLocationsPage(pageNumber: Long, pageSize: Long = 100, response: String) {
    stubFor(
      get(WireMock.urlPathEqualTo("/locations"))
        .withQueryParam("page", WireMock.equalTo(pageNumber.toString()))
        .withQueryParam("size", WireMock.equalTo(pageSize.toString()))
        .willReturn(WireMock.okJson(response)),
    )
  }

  fun stubGetLocationsPageWithError(pageNumber: Long, pageSize: Long = 100, status: Int = 500) {
    stubFor(
      get(WireMock.urlPathEqualTo("/locations"))
        .withQueryParam("page", WireMock.equalTo(pageNumber.toString()))
        .withQueryParam("size", WireMock.equalTo(pageSize.toString()))
        .willReturn(WireMock.jsonResponse("""{ "error": "some error" }""", status)),
    )
  }

  fun getCountFor(url: String) = this.findAll(WireMock.getRequestedFor(WireMock.urlEqualTo(url))).count()
}
