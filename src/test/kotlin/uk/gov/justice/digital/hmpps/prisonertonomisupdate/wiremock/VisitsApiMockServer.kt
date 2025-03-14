package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class VisitsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val visitsApi = VisitsApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    visitsApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    visitsApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    visitsApi.stop()
  }
}

class VisitsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8083
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

  fun stubVisitGet(visitId: String, response: String) {
    stubFor(
      get("/visits/$visitId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200),
      ),
    )
  }

  fun getCountFor(url: String) = this.findAll(WireMock.getRequestedFor(WireMock.urlEqualTo(url))).count()

  fun stubVisitGetWithError(visitId: String, status: Int = 500) {
    stubFor(
      get("/visits/$visitId").willReturn(
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
