package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class SentencingAdjustmentsApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val sentencingAdjustmentsApi = SentencingAdjustmentsApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    sentencingAdjustmentsApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    sentencingAdjustmentsApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    sentencingAdjustmentsApi.stop()
  }
}

class SentencingAdjustmentsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8087
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

  fun stubAdjustmentGet(
    adjustmentId: String,
    adjustmentDate: String = "2021-01-01",
    adjustmentStartPeriod: String? = null,
    adjustmentDays: Long = 20,
    bookingId: Long = 1234,
    sentenceSequence: Long? = null,
    adjustmentType: String = "ADA",
    comment: String? = null,
    creatingSystem: String = "SENTENCE_ADJUSTMENTS",
  ) {
    val startPeriodJson = adjustmentStartPeriod?.let { """ "adjustmentStartPeriod": "$it",  """ } ?: ""
    val sentenceSequenceJson = sentenceSequence?.let { """ "sentenceSequence": $it,  """ } ?: ""
    val commentJson = comment?.let { """ "comment": "$it",  """ } ?: ""

    stubFor(
      get("/adjustments/$adjustmentId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            {
              "adjustmentId": "$adjustmentId",
              "adjustmentDate": "$adjustmentDate",
              "adjustmentDays": $adjustmentDays,
              $startPeriodJson
              "bookingId": $bookingId,
              $sentenceSequenceJson
              $commentJson
              "adjustmentType": "$adjustmentType",
              "creatingSystem": "$creatingSystem"
            }
            """.trimIndent()
          )
          .withStatus(200)
      )
    )
  }

  fun stubAdjustmentGetWithError(adjustmentId: String, status: Int) {
    stubFor(
      get("/adjustments/$adjustmentId").willReturn(
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

  fun stubAdjustmentGetWithErrorFollowedBySlowSuccess(
    adjustmentId: String,
    sentenceSequence: Long,
    bookingId: Long,
    adjustmentType: String = "RX",
    adjustmentDate: String = "2021-01-01",
    adjustmentDays: Long = 20,
  ) {
    stubFor(
      get("/adjustments/$adjustmentId")
        .inScenario("Retry Adjustments Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json")
        )
        .willSetStateTo("Cause Adjustments Success")
    )

    stubFor(
      get("/adjustments/$adjustmentId")
        .inScenario("Retry Adjustments Scenario")
        .whenScenarioStateIs("Cause Adjustments Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
            {
              "adjustmentId": "$adjustmentId",
              "adjustmentDate": "$adjustmentDate",
              "adjustmentDays": $adjustmentDays,
              "bookingId": $bookingId,
              "sentenceSequence": $sentenceSequence,
              "adjustmentType": "$adjustmentType",
              "creatingSystem": "SENTENCE_ADJUSTMENTS"
            }
              """.trimIndent()
            )
            .withStatus(200)
            .withFixedDelay(500)

        ).willSetStateTo(Scenario.STARTED)
    )
  }
}
