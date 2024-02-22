package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension

class CourtSentencingApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val courtSentencingApi = CourtSentencingApiMockServer()
    lateinit var objectMapper: ObjectMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
    courtSentencingApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    courtSentencingApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    courtSentencingApi.stop()
  }
}

class CourtSentencingApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8094
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

  fun stubCourtCaseGet(courtCaseId: String, offenderNo: String = "A6160DZ") {
    stubFor(
      get(WireMock.urlPathMatching("/court-case/$courtCaseId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            // language=json
            """
           {
    "prisonerId": "$offenderNo",
    "courtCaseUuid": "$courtCaseId",
    "latestAppearance": {
        "appearanceUuid": "9c591b18-642a-484a-a967-2d17b5c9c5a1",
        "outcome": "Remand in Custody (Bail Refused)",
        "courtCode": "Doncaster Magistrates Court",
        "courtCaseReference": "G123456789",
        "appearanceDate": "2024-09-23",
        "warrantId": "7e15b408-4f97-453e-98b7-24791978221c",
        "warrantType": "REMAND",
        "taggedBail": null,
        "nextCourtAppearance": {
            "appearanceDate": "2024-12-10",
            "courtCode": "Doncaster Magistrates Court",
            "appearanceType": "Court appearance"
        },
        "charges": [
            {
                "chargeUuid": "3dc522fb-167d-44b2-932a-d075f8816fae",
                "offenceCode": "PS90037",
                "offenceStartDate": "2024-01-15",
                "offenceEndDate": null,
                "outcome": "Remand in Custody (Bail Refused)",
                "terrorRelated": null,
                "sentence": null
            }
        ]
    },
    "appearances": [
        {
            "appearanceUuid": "9c591b18-642a-484a-a967-2d17b5c9c5a1",
            "outcome": "Remand in Custody (Bail Refused)",
            "courtCode": "Doncaster Magistrates Court",
            "courtCaseReference": "G123456789",
            "appearanceDate": "2024-09-23",
            "warrantId": "7e15b408-4f97-453e-98b7-24791978221c",
            "warrantType": "REMAND",
            "taggedBail": null,
            "nextCourtAppearance": {
                "appearanceDate": "2024-12-10",
                "courtCode": "Doncaster Magistrates Court",
                "appearanceType": "Court appearance"
            },
            "charges": [
                {
                    "chargeUuid": "3dc522fb-167d-44b2-932a-d075f8816fae",
                    "offenceCode": "PS90037",
                    "offenceStartDate": "2024-01-15",
                    "offenceEndDate": null,
                    "outcome": "Remand in Custody (Bail Refused)",
                    "terrorRelated": null,
                    "sentence": null
                }
            ]
        }
    ]
}   
            """.trimIndent(),
          )
          .withStatus(200),
      ),
    )
  }

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder {
    this.withBody(NomisApiExtension.objectMapper.writeValueAsString(body))
    return this
  }

  fun getCountFor(url: String) = this.findAll(WireMock.getRequestedFor(WireMock.urlEqualTo(url))).count()
}
