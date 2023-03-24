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
          .withStatus(status),
      ),
    )
  }

  fun stubAdjustmentGet(
    adjustmentId: String,
    adjustmentDate: String? = null,
    adjustmentFromDate: String? = null,
    adjustmentDays: Long = 20,
    bookingId: Long = 1234,
    sentenceSequence: Long? = null,
    adjustmentType: String = "ADA",
    comment: String? = null,
    active: Boolean = true,
  ) {
    val startPeriodJson = adjustmentFromDate?.let { """ "adjustmentFromDate": "$it",  """ } ?: ""
    val sentenceSequenceJson = sentenceSequence?.let { """ "sentenceSequence": $it,  """ } ?: ""
    val commentJson = comment?.let { """ "comment": "$it",  """ } ?: ""
    val adjustmentDateJson = adjustmentDate?.let { """ "adjustmentDate": "$it",  """ } ?: """ "adjustmentDate": null,  """

    stubFor(
      get("/legacy/adjustments/$adjustmentId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            {
              $adjustmentDateJson
              "adjustmentDays": $adjustmentDays,
              $startPeriodJson
              "bookingId": $bookingId,
              $sentenceSequenceJson
              $commentJson
              "adjustmentType": "$adjustmentType",
              "active": $active
            }
            """.trimIndent(),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubAdjustmentGetWithError(adjustmentId: String, status: Int) {
    stubFor(
      get("/legacy/adjustments/$adjustmentId").willReturn(
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

  fun stubAdjustmentGetWithErrorFollowedBySlowSuccess(
    adjustmentId: String,
    sentenceSequence: Long,
    bookingId: Long,
    adjustmentType: String = "RX",
    adjustmentDate: String = "2021-01-01",
    adjustmentDays: Long = 20,
  ) {
    stubFor(
      get("/legacy/adjustments/$adjustmentId")
        .inScenario("Retry Adjustments Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause Adjustments Success"),
    )

    stubFor(
      get("/legacy/adjustments/$adjustmentId")
        .inScenario("Retry Adjustments Scenario")
        .whenScenarioStateIs("Cause Adjustments Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
            {
              "adjustmentDate": "$adjustmentDate",
              "adjustmentDays": $adjustmentDays,
              "bookingId": $bookingId,
              "sentenceSequence": $sentenceSequence,
              "adjustmentType": "$adjustmentType",
              "active": true
            }
              """.trimIndent(),
            )
            .withStatus(200)
            .withFixedDelay(500),

        ).willSetStateTo(Scenario.STARTED),
    )
  }
}
