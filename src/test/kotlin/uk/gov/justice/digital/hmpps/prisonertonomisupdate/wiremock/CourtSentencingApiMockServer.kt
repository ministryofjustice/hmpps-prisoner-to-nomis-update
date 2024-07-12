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

const val COURT_CHARGE_1_OFFENCE_CODE = "PS90031"
const val COURT_CHARGE_2_OFFENCE_CODE = "PS90032"
const val COURT_CHARGE_3_OFFENCE_CODE = "PS90033"
const val COURT_CHARGE_4_OFFENCE_CODE = "PS90034"
const val COURT_CHARGE_1_RESULT_CODE = "Remand in Custody 1 (Bail Refused)"
const val COURT_CHARGE_2_RESULT_CODE = "Remand in Custody 2 (Bail Refused)"
const val COURT_CHARGE_3_RESULT_CODE = "Imprisonment"
const val COURT_CHARGE_4_RESULT_CODE = "Imprisonment"
const val COURT_CHARGE_1_OFFENCE_DATE = "2024-01-11"
const val COURT_CHARGE_2_OFFENCE_DATE = "2024-01-12"
const val COURT_CHARGE_3_OFFENCE_DATE = "2024-01-13"
const val COURT_CHARGE_4_OFFENCE_DATE = "2024-01-14"
const val COURT_CHARGE_1_OFFENCE_END_DATE = "2024-01-11"
const val COURT_CHARGE_2_OFFENCE_END_DATE = "2024-01-12"

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

  fun stubCourtCaseGet(courtCaseId: String, courtAppearanceId: String, courtCharge1Id: String, courtCharge2Id: String, offenderNo: String = "A6160DZ") {
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
                "chargeUuid": "$courtCharge1Id",
                "offenceCode": "PS90037",
                "offenceStartDate": "2024-01-15",
                "offenceEndDate": null,
                "outcome": "Remand in Custody (Bail Refused)",
                "terrorRelated": null,
                "sentence": null
            },
            {
                "chargeUuid": "$courtCharge2Id",
                "offenceCode": "PS90090",
                "offenceStartDate": "2024-01-17",
                "offenceEndDate": "2024-01-19",
                "outcome": "Remand in Custody Different outcome",
                "terrorRelated": null,
                "sentence": null
            }
        ]
    },
    "appearances": [
        {
            "appearanceUuid": "$courtAppearanceId",
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
                "chargeUuid": "$courtCharge1Id",
                "offenceCode": "PS90037",
                "offenceStartDate": "2024-01-15",
                "offenceEndDate": null,
                "outcome": "Remand in Custody (Bail Refused)",
                "terrorRelated": null,
                "sentence": null
              },
              {
                  "chargeUuid": "$courtCharge2Id",
                  "offenceCode": "PS90090",
                  "offenceStartDate": "2024-01-17",
                  "offenceEndDate": "2024-01-19",
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

  fun stubCourtAppearanceGetWithFourCharges(courtCaseId: String, courtAppearanceId: String, courtCharge1Id: String, courtCharge2Id: String, courtCharge3Id: String, courtCharge4Id: String, offenderNo: String = "A6160DZ") {
    stubFor(
      get(WireMock.urlPathMatching("/court-appearance/$courtAppearanceId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            // language=json
            """
           {
            "appearanceUuid": "$courtAppearanceId",
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
                "chargeUuid": "$courtCharge1Id",
                "offenceCode": "$COURT_CHARGE_1_OFFENCE_CODE",
                "offenceStartDate": "$COURT_CHARGE_1_OFFENCE_DATE",
                "offenceEndDate": "$COURT_CHARGE_1_OFFENCE_END_DATE",
                "outcome": "$COURT_CHARGE_1_RESULT_CODE",
                "terrorRelated": null,
                "sentence": null
            },
            {
                "chargeUuid": "$courtCharge2Id",
                "offenceCode": "$COURT_CHARGE_2_OFFENCE_CODE",
                "offenceStartDate": "$COURT_CHARGE_2_OFFENCE_DATE",
                "offenceEndDate": "$COURT_CHARGE_2_OFFENCE_END_DATE",
                "outcome": "$COURT_CHARGE_2_RESULT_CODE",
                "terrorRelated": null,
                "sentence": null
            },
            {
                "chargeUuid": "$courtCharge3Id",
                "offenceCode": "$COURT_CHARGE_3_OFFENCE_CODE",
                "offenceStartDate": "$COURT_CHARGE_3_OFFENCE_DATE",
                "offenceEndDate": null,
                "outcome": "$COURT_CHARGE_3_RESULT_CODE",
                "terrorRelated": null,
                "sentence": null
            },
            {
                "chargeUuid": "$courtCharge4Id",
                "offenceCode": "$COURT_CHARGE_4_OFFENCE_CODE",
                "offenceStartDate": "$COURT_CHARGE_4_OFFENCE_DATE",
                "offenceEndDate": null,
                "outcome": "$COURT_CHARGE_4_RESULT_CODE",
                "terrorRelated": null,
                "sentence": null
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
