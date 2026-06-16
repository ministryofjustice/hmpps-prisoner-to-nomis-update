package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.StaffDpsApiExtension.Companion.dpsStaffServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.ErrorResponse

class StaffDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val dpsStaffServer = StaffDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsStaffServer.start()
    jsonMapper = SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper
  }

  override fun beforeEach(context: ExtensionContext) {
    dpsStaffServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsStaffServer.stop()
  }
}

class StaffDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    const val WIREMOCK_PORT = 8106
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

  fun stubGetStaff(
    staffId: String = "4321",
    response: DpsStaffDetails? = dpsStaffDetails(staffId),
  ) {
    if (response == null) {
      dpsStaffServer.stubFor(
        get(urlPathEqualTo("/prison-users/$staffId"))
          .willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.NOT_FOUND.value())
              .withBody(
                StaffDpsApiExtension.jsonMapper.writeValueAsString(ErrorResponse(status = 404)),
              ),
          ),
      )
    } else {
      dpsStaffServer.stubFor(
        get(urlPathEqualTo("/prison-users/$staffId"))
          .willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withStatus(HttpStatus.OK.value())
              .withBody(
                StaffDpsApiExtension.jsonMapper.writeValueAsString(response),
              ),
          ),
      )
    }
  }
}
