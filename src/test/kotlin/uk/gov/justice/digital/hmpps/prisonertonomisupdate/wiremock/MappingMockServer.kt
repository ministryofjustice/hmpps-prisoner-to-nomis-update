package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class MappingExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val mappingServer = MappingMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    mappingServer.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    mappingServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    mappingServer.stop()
  }
}

class MappingMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8084
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

  fun stubCreate() {
    stubFor(
      post("/mapping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201)
      )
    )
  }

  fun stubCreateWithError(status: Int = 500) {
    stubFor(
      post("/mapping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id already exists" }""")
          .withStatus(status)
      )
    )
  }

  fun stubGetNomis(nomisId: String, response: String) {
    stubFor(
      get("/mapping/nomisId/$nomisId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200)
      )
    )
  }

  fun stubGetNomisWithError(nomisId: String, status: Int = 500) {
    stubFor(
      get("/mapping/nomisId/$nomisId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id does not exist" }""")
          .withStatus(status)
      )
    )
  }

  fun stubGetVsip(vsipId: String, response: String) {
    stubFor(
      get("/mapping/vsipId/$vsipId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200)
      )
    )
  }

  fun stubGetVsipWithError(vsipId: String, status: Int = 500) {
    stubFor(
      get("/mapping/vsipId/$vsipId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id does not exist" }""")
          .withStatus(status)
      )
    )
  }

  fun postCountFor(url: String) = this.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo(url))).count()
  fun getCountFor(url: String) = this.findAll(WireMock.getRequestedFor(WireMock.urlEqualTo(url))).count()
}
