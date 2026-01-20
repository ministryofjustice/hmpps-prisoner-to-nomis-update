package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.model.AdjustmentDto

class SentencingAdjustmentsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val sentencingAdjustmentsApi = SentencingAdjustmentsApiMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
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

  fun stubAdjustmentsGet() {
    stubFor(
      get(urlPathMatching("/adjustments")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            // language=json
            """
            [
                {
                    "id": "9883834b-224d-4865-8e2d-8041773a3285",
                    "bookingId": 1204935,
                    "person": "A1032DZ",
                    "adjustmentType": "RESTORATION_OF_ADDITIONAL_DAYS_AWARDED",
                    "toDate": "2023-06-01",
                    "fromDate": "2023-06-01",
                    "days": null,
                    "remand": null,
                    "additionalDaysAwarded": null,
                    "unlawfullyAtLarge": null,
                    "prisonId": null,
                    "prisonName": null,
                    "lastUpdatedBy": "NOMIS",
                    "status": "ACTIVE",
                    "lastUpdatedDate": "2023-10-26T09:57:38.520753",
                    "effectiveDays": 1,
                    "sentenceSequence": null,
                    "daysBetween": 1
                },
                {
                    "id": "29044a3c-6261-4681-af9e-ef2a84c51a22",
                    "bookingId": 1204935,
                    "person": "A1032DZ",
                    "adjustmentType": "REMAND",
                    "toDate": "2023-01-20",
                    "fromDate": "2023-01-10",
                    "days": null,
                    "remand": null,
                    "additionalDaysAwarded": null,
                    "unlawfullyAtLarge": null,
                    "prisonId": null,
                    "prisonName": null,
                    "lastUpdatedBy": "NOMIS",
                    "status": "ACTIVE",
                    "lastUpdatedDate": "2023-10-26T09:57:36.319803",
                    "effectiveDays": 11,
                    "sentenceSequence": 1,
                    "daysBetween": 11
                },
                {
                    "id": "3334132c-5cea-470c-ab73-e875c5a103aa",
                    "bookingId": 1204935,
                    "person": "A1032DZ",
                    "adjustmentType": "RESTORATION_OF_ADDITIONAL_DAYS_AWARDED",
                    "toDate": "2023-06-01",
                    "fromDate": "2023-06-01",
                    "days": null,
                    "remand": null,
                    "additionalDaysAwarded": null,
                    "unlawfullyAtLarge": null,
                    "prisonId": null,
                    "prisonName": null,
                    "lastUpdatedBy": "NOMIS",
                    "status": "ACTIVE",
                    "lastUpdatedDate": "2023-10-26T10:00:46.464528",
                    "effectiveDays": 1,
                    "sentenceSequence": null,
                    "daysBetween": 1
                },
                {
                    "id": "4fe99df7-733b-47e2-84ea-6e5494a3902b",
                    "bookingId": 1204935,
                    "person": "A1032DZ",
                    "adjustmentType": "RESTORATION_OF_ADDITIONAL_DAYS_AWARDED",
                    "toDate": "2023-09-06",
                    "fromDate": "2023-09-01",
                    "days": null,
                    "remand": null,
                    "additionalDaysAwarded": null,
                    "unlawfullyAtLarge": null,
                    "prisonId": null,
                    "prisonName": null,
                    "lastUpdatedBy": "NOMIS",
                    "status": "ACTIVE",
                    "lastUpdatedDate": "2023-10-26T10:01:24.393739",
                    "effectiveDays": 6,
                    "sentenceSequence": null,
                    "daysBetween": 6
                },
                {
                    "id": "a4cef857-cb1e-43e4-b905-13e85fee7538",
                    "bookingId": 1204935,
                    "person": "A1032DZ",
                    "adjustmentType": "TAGGED_BAIL",
                    "toDate": null,
                    "fromDate": null,
                    "days": null,
                    "remand": null,
                    "additionalDaysAwarded": null,
                    "unlawfullyAtLarge": null,
                    "prisonId": null,
                    "prisonName": null,
                    "lastUpdatedBy": "NOMIS",
                    "status": "ACTIVE",
                    "lastUpdatedDate": "2023-10-26T10:01:30.042687",
                    "effectiveDays": 24,
                    "sentenceSequence": 1,
                    "daysBetween": null
                },
                {
                    "id": "30d70bdd-5763-4478-b5ec-5b3f89b52b12",
                    "bookingId": 1204935,
                    "person": "A1032DZ",
                    "adjustmentType": "TAGGED_BAIL",
                    "toDate": "2022-03-28",
                    "fromDate": "2022-03-27",
                    "days": null,
                    "remand": null,
                    "additionalDaysAwarded": null,
                    "unlawfullyAtLarge": null,
                    "prisonId": null,
                    "prisonName": null,
                    "lastUpdatedBy": "NOMIS",
                    "status": "ACTIVE",
                    "lastUpdatedDate": "2023-10-26T10:01:57.619679",
                    "effectiveDays": 2,
                    "sentenceSequence": 1,
                    "daysBetween": 2
                },
                {
                    "id": "4f6e1f38-abdb-49cb-ac6d-cd5adbf1b1b1",
                    "bookingId": 1204935,
                    "person": "A1032DZ",
                    "adjustmentType": "RESTORATION_OF_ADDITIONAL_DAYS_AWARDED",
                    "toDate": "2023-04-19",
                    "fromDate": "2023-04-19",
                    "days": null,
                    "remand": null,
                    "additionalDaysAwarded": null,
                    "unlawfullyAtLarge": null,
                    "prisonId": null,
                    "prisonName": null,
                    "lastUpdatedBy": "NOMIS",
                    "status": "ACTIVE",
                    "lastUpdatedDate": "2023-10-26T10:04:56.76475",
                    "effectiveDays": 1,
                    "sentenceSequence": null,
                    "daysBetween": 1
                },
                {
                    "id": "b5ef0241-921d-4044-acdd-01c263b06761",
                    "bookingId": 1204935,
                    "person": "A1032DZ",
                    "adjustmentType": "RESTORATION_OF_ADDITIONAL_DAYS_AWARDED",
                    "toDate": "2023-07-14",
                    "fromDate": "2023-07-09",
                    "days": null,
                    "remand": null,
                    "additionalDaysAwarded": null,
                    "unlawfullyAtLarge": null,
                    "prisonId": null,
                    "prisonName": null,
                    "lastUpdatedBy": "NOMIS",
                    "status": "ACTIVE",
                    "lastUpdatedDate": "2023-10-26T10:09:04.461454",
                    "effectiveDays": 6,
                    "sentenceSequence": null,
                    "daysBetween": 6
                },
                {
                    "id": "1d0aceb9-7bb2-4004-9c7f-1777782c074b",
                    "bookingId": 1204935,
                    "person": "A1032DZ",
                    "adjustmentType": "TAGGED_BAIL",
                    "toDate": null,
                    "fromDate": "2022-03-07",
                    "days": 10,
                    "remand": null,
                    "additionalDaysAwarded": null,
                    "unlawfullyAtLarge": null,
                    "prisonId": "KMI",
                    "prisonName": "Kirkham (HMP)",
                    "lastUpdatedBy": "CRD_TEST_USER",
                    "status": "ACTIVE",
                    "lastUpdatedDate": "2023-11-22T16:40:06.926652",
                    "effectiveDays": 10,
                    "sentenceSequence": null,
                    "daysBetween": null
                },
                {
                    "id": "ce817cb8-4d52-4d3c-99d7-3edce5185a19",
                    "bookingId": 1204935,
                    "person": "A1032DZ",
                    "adjustmentType": "UNLAWFULLY_AT_LARGE",
                    "toDate": "2023-05-01",
                    "fromDate": "2023-04-01",
                    "days": null,
                    "remand": null,
                    "additionalDaysAwarded": null,
                    "unlawfullyAtLarge": {
                        "type": "SENTENCED_IN_ABSENCE"
                    },
                    "prisonId": "KMI",
                    "prisonName": "Kirkham (HMP)",
                    "lastUpdatedBy": "CRD_TEST_USER",
                    "status": "ACTIVE",
                    "lastUpdatedDate": "2023-11-23T13:15:32.723471",
                    "effectiveDays": 31,
                    "sentenceSequence": null,
                    "daysBetween": 31
                },
                {
                    "id": "0102ab0f-69b0-4292-84d9-bc5fd9f46e66",
                    "bookingId": 1204935,
                    "person": "A1032DZ",
                    "adjustmentType": "RESTORATION_OF_ADDITIONAL_DAYS_AWARDED",
                    "toDate": "2023-07-13",
                    "fromDate": "2023-07-09",
                    "days": null,
                    "remand": null,
                    "additionalDaysAwarded": null,
                    "unlawfullyAtLarge": null,
                    "prisonId": null,
                    "prisonName": null,
                    "lastUpdatedBy": "NOMIS",
                    "status": "ACTIVE",
                    "lastUpdatedDate": "2023-10-26T10:09:32.028372",
                    "effectiveDays": 5,
                    "sentenceSequence": null,
                    "daysBetween": 5
                }
            ]            
            """.trimIndent(),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubAdjustmentsGet(adjustments: List<AdjustmentDto>) {
    stubFor(
      get(urlPathMatching("/adjustments")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(adjustments)
          .withStatus(200),
      ),
    )
  }

  fun stubAdjustmentsGet(offenderNo: String, adjustments: List<AdjustmentDto>) {
    stubFor(
      get(urlEqualTo("/adjustments?person=$offenderNo")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(adjustments)
          .withStatus(200),
      ),
    )
  }

  fun stubAdjustmentsGetWithError(status: Int) {
    stubFor(
      get(urlPathMatching("/adjustments")).willReturn(
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

  fun stubAdjustmentsGetWithError(offenderNo: String, status: Int) {
    stubFor(
      get(urlEqualTo("/adjustments?person=$offenderNo")).willReturn(
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
              "bookingReleased": false,
              "currentTerm": false,
              $startPeriodJson
              "bookingId": $bookingId,
              "offenderNo": "A1234AA", 
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
              "bookingReleased": false,
              "currentTerm": false,
              "bookingId": $bookingId,
              "offenderNo": "A1234AA", 
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

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder {
    this.withBody(NomisApiExtension.jsonMapper.writeValueAsString(body))
    return this
  }
}
