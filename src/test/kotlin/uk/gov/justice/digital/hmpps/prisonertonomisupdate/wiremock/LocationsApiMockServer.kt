package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.jsonResponse
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.patch
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import java.util.UUID

class LocationsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
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

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(okJson(if (status == 200) "pong" else "some error")),
    )
  }

  fun stubGetLocationSync(id: String, includeHistory: Boolean, response: String) {
    stubFor(
      get(urlPathEqualTo("/sync/id/$id"))
        .withQueryParam("includeHistory", equalTo(includeHistory.toString()))
        .willReturn(okJson(response)),
    )
  }

  fun stubGetLocationSlowThenQuick(id: String, response: String) {
    stubFor(
      get(urlPathEqualTo("/sync/id/$id"))
        .inScenario("Timeout Locations Scenario")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withFixedDelay(1100),
        )
        .willSetStateTo("Cause Locations 1"),
    )

    stubFor(
      get(urlPathEqualTo("/sync/id/$id"))
        .inScenario("Timeout Locations Scenario")
        .whenScenarioStateIs("Cause Locations 1")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withFixedDelay(1100),
        )
        .willSetStateTo("Cause Locations Success"),
    )

    stubFor(
      get(urlPathEqualTo("/sync/id/$id"))
        .inScenario("Timeout Locations Scenario")
        .whenScenarioStateIs("Cause Locations Success")
        .willReturn(okJson(response))
        .willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetLocationWithError(id: String, includeHistory: Boolean, status: Int = 500) {
    stubFor(
      get(urlPathEqualTo("/sync/id/$id"))
        .withQueryParam("includeHistory", equalTo(includeHistory.toString()))
        .willReturn(jsonResponse("""{ "error": "some error" }""", status)),
    )
  }

  fun stubGetLocationWithErrorFollowedBySlowSuccess(id: String, includeHistory: Boolean, response: String) {
    stubFor(
      get(urlPathEqualTo("/sync/id/$id"))
        .withQueryParam("includeHistory", equalTo(includeHistory.toString()))
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
      get(urlPathEqualTo("/sync/id/$id"))
        .withQueryParam("includeHistory", equalTo(includeHistory.toString()))
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

  fun stubGetLocationDPS(id: String, includeChildren: Boolean, response: String) {
    stubFor(
      get(urlPathEqualTo("/locations/$id"))
        .withQueryParam("includeChildren", equalTo(includeChildren.toString()))
        .willReturn(okJson(response)),
    )
  }

  fun stubGetLocationsPage(pageNumber: Long, pageSize: Long = 100, response: String) {
    stubFor(
      get(urlPathEqualTo("/locations"))
        .withQueryParam("page", equalTo(pageNumber.toString()))
        .withQueryParam("size", equalTo(pageSize.toString()))
        .willReturn(okJson(response)),
    )
  }

  fun stubGetLocationsPageWithError(pageNumber: Long, pageSize: Long = 100, status: Int = 500) {
    stubFor(
      get(urlPathEqualTo("/locations"))
        .withQueryParam("page", equalTo(pageNumber.toString()))
        .withQueryParam("size", equalTo(pageSize.toString()))
        .willReturn(jsonResponse("""{ "error": "some error" }""", status)),
    )
  }

  fun stubPatchNonResidentialLocation(id: UUID) {
    stubFor(
      patch(urlPathEqualTo("/locations/non-residential/$id"))
        .willReturn(okJson("""{ "dummy": "value" }""")),
    )
  }

  fun stubPatchNonResidentialLocationWithError(id: UUID, status: Int = 500) {
    stubFor(
      patch(urlPathEqualTo("/locations/non-residential/$id"))
        .willReturn(jsonResponse("""{ "error": "some error" }""", status)),
    )
  }

  fun getCountFor(url: String) = this.findAll(getRequestedFor(urlEqualTo(url))).count()
}
