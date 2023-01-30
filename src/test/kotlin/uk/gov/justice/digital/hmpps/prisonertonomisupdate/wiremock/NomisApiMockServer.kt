package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.stubbing.Scenario
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
    private const val WIREMOCK_PORT = 8082
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

  fun stubVisitCreate(prisonerId: String, response: String = CREATE_VISIT_RESPONSE) {
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
          .withBody(ERROR_RESPONSE)
          .withStatus(status)
      )
    )
  }

  fun stubVisitCancel(prisonerId: String, visitId: String = "1234") {
    stubFor(
      put("/prisoners/$prisonerId/visits/$visitId/cancel").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200)
      )
    )
  }

  fun stubVisitUpdate(prisonerId: String, visitId: String = "1234") {
    stubFor(
      put("/prisoners/$prisonerId/visits/$visitId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200)
      )
    )
  }

  fun stubVisitCancelWithError(prisonerId: String, visitId: String, status: Int = 500) {
    stubFor(
      put("/prisoners/$prisonerId/visits/$visitId/cancel").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status)
      )
    )
  }

  fun stubVisitUpdateWithError(prisonerId: String, visitId: String, status: Int = 500) {
    stubFor(
      put("/prisoners/$prisonerId/visits/$visitId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status)
      )
    )
  }

  fun stubIncentiveCreate(bookingId: Long, response: String = CREATE_INCENTIVE_RESPONSE) {
    stubFor(
      post("/prisoners/booking-id/$bookingId/incentives").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(201)
      )
    )
  }

  fun stubIncentiveCreateWithError(bookingId: Long, status: Int = 500) {
    stubFor(
      post("/prisoners/booking-id/$bookingId/incentives").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status)
      )
    )
  }

  fun stubActivityCreate(response: String) {
    stubFor(
      post("/activities").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(201)
      )
    )
  }

  fun stubActivityCreateWithError(status: Int = 500) {
    stubFor(
      post("/activities").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status)
      )
    )
  }

  fun stubAllocationCreate(courseActivityId: Long) {
    stubFor(
      post("/activities/$courseActivityId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "offenderProgramReferenceId": 1234 }""")
          .withStatus(201)
      )
    )
  }

  fun stubAllocationCreateWithError(courseActivityId: Long, status: Int = 500) {
    stubFor(
      post("/activities/$courseActivityId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status)
      )
    )
  }

  private val ERROR_RESPONSE = """{ "error": "some error" }"""

  fun stubSentenceAdjustmentCreate(bookingId: Long, sentenceSequence: Long, adjustmentId: Long = 99L) {
    stubFor(
      post("/prisoners/booking-id/$bookingId/sentences/$sentenceSequence/adjustments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{"id": $adjustmentId}""")
          .withStatus(201)
      )
    )
  }

  fun stubSentenceAdjustmentCreateWithError(bookingId: Long, sentenceSequence: Long, status: Int) {
    stubFor(
      post("/prisoners/booking-id/$bookingId/sentences/$sentenceSequence/adjustments").willReturn(
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

  fun stubSentenceAdjustmentCreateWithErrorFollowedBySlowSuccess(
    bookingId: Long,
    sentenceSequence: Long,
    adjustmentId: Long = 99L
  ) {
    stubFor(
      post("/prisoners/booking-id/$bookingId/sentences/$sentenceSequence/adjustments")
        .inScenario("Retry NOMIS Adjustments Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json")
        )
        .willSetStateTo("Cause NOMIS Adjustments Success")
    )

    stubFor(
      post("/prisoners/booking-id/$bookingId/sentences/$sentenceSequence/adjustments")
        .inScenario("Retry NOMIS Adjustments Scenario")
        .whenScenarioStateIs("Cause NOMIS Adjustments Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
                { "id": $adjustmentId }
                """
            )
            .withStatus(200)
            .withFixedDelay(1500)

        ).willSetStateTo(Scenario.STARTED)
    )
  }

  fun stubKeyDateAdjustmentCreate(bookingId: Long, adjustmentId: Long = 99L) {
    stubFor(
      post("/prisoners/booking-id/$bookingId/adjustments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{"id": $adjustmentId}""")
          .withStatus(201)
      )
    )
  }

  fun stubKeyDateAdjustmentCreateWithError(bookingId: Long, status: Int) {
    stubFor(
      post("/prisoners/booking-id/$bookingId/adjustments").willReturn(
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

  private val CREATE_VISIT_RESPONSE = """
    {
      "visitId": "12345"
    }
    """

  private val CREATE_INCENTIVE_RESPONSE = """
    {
      "bookingId": 456,
      "sequence": 1
    }
    """

  fun postCountFor(url: String) = this.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo(url))).count()
  fun putCountFor(url: String) = this.findAll(WireMock.putRequestedFor(WireMock.urlEqualTo(url))).count()
}
