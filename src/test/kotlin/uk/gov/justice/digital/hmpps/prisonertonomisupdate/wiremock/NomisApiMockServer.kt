package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class NomisApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val nomisApi = NomisApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    nomisApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    nomisApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    nomisApi.stop()
  }
}

class NomisApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8081
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status)
      )
    )
  }

  fun stubVisitCreate(prisonerId: String, response: String = CREATE_RESPONSE) {
    stubFor(
      post("/prisoners/$prisonerId/visits").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(201)
      )
    )
  }

  fun stubVisitCreateWithError(prisonerId: String, status: Int = 500) {
    stubFor(
      post("/prisoners/$prisonerId/visits").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              {
                "error": "some error"
              }
            """.trimIndent()
          )
          .withStatus(status)
      )
    )
  }

  fun stubVisitCancel(prisonerId: String, visitId: String = "1234") {
    stubFor(
      put("/prisoners/$prisonerId/visits/vsipVisitId/$visitId/cancel").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200)
      )
    )
  }

  fun stubVisitCancelWithError(prisonerId: String, visitId: String, status: Int = 500) {
    stubFor(
      put("/prisoners/$prisonerId/visits/vsipVisitId/$visitId/cancel").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              {
                "error": "some error"
              }
            """.trimIndent()
          )
          .withStatus(status)
      )
    )
  }

  private val CREATE_RESPONSE = """
    {
      "visitId": "12345"
     }
     """

  fun postCountFor(url: String) = this.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo(url))).count()
  fun putCountFor(url: String) = this.findAll(WireMock.putRequestedFor(WireMock.urlEqualTo(url))).count()
}
