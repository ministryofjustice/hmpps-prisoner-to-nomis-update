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

class AppointmentsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val appointmentsApi = AppointmentsApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    appointmentsApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    appointmentsApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    appointmentsApi.stop()
  }
}

class AppointmentsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8088
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

  fun stubGetAppointmentInstance(id: Long, response: String) {
    stubFor(
      get("/appointment-instances/$id").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200),
      ),
    )
  }

  fun stubGetAppointmentInstanceWithError(id: Long, status: Int = 500) {
    stubFor(
      get("/appointment-instances/$id").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "error": "some error" }""")
          .withStatus(status),
      ),
    )
  }

  fun stubGetAppointmentInstanceWithErrorFollowedBySlowSuccess(id: Long, response: String, status: Int = 500) {
    stubFor(
      get("/appointment-instances/$id")
        .inScenario("Retry Appointment instance Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(status) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause Appointments Success"),
    )

    stubFor(
      get("/appointment-instances/$id")
        .inScenario("Retry Appointment instance Scenario")
        .whenScenarioStateIs("Cause Appointments Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(response)
            .withStatus(200)
            .withFixedDelay(500),
        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun getCountFor(url: String) = this.findAll(WireMock.getRequestedFor(WireMock.urlEqualTo(url))).count()
}
