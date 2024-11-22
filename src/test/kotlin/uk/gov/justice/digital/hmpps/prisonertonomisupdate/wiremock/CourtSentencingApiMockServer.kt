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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCharge
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.objectMapper
import java.time.LocalDate
import java.util.UUID

const val COURT_CHARGE_1_OFFENCE_CODE = "PS90031"
const val COURT_CHARGE_2_OFFENCE_CODE = "PS90032"
const val COURT_CHARGE_3_OFFENCE_CODE = "PS90033"
const val COURT_CHARGE_4_OFFENCE_CODE = "PS90034"
const val COURT_CHARGE_1_RESULT_CODE = "4531"
const val COURT_CHARGE_2_RESULT_CODE = "4531"
const val COURT_CHARGE_3_RESULT_CODE = "1002"
const val COURT_CHARGE_4_RESULT_CODE = "1002"
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

  fun stubCourtCaseGet(courtCaseId: String, courtAppearanceId: String, courtCharge1Id: String, courtCharge2Id: String, caseReference: String = "G123456789", courtId: String = "DRBYYC", offenderNo: String = "A6160DZ") {
    stubFor(
      get(WireMock.urlPathMatching("/legacy/court-case/$courtCaseId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            // language=json
            """
           {
    "prisonerId": "$offenderNo",
    "courtCaseUuid": "$courtCaseId",
    "latestAppearance": {
        "appearanceUuid": "6c591b18-642a-484a-a967-2d17b5c9c5a1",
        "lifetimeUuid": "9c591b18-642a-484a-a967-2d17b5c9c5a1",
        "outcome": {
            "outcomeUuid": "8b28aa1b-8e2c-4c77-ad32-6feca8b0e459",
            "relatedChargeOutcomeUuid": "9928aa1b-8e2c-4c77-ad32-6feca8b0e459",
            "outcomeName": "Remanded in custody",
            "nomisCode": "4531",
            "outcomeType": "UNKNOWN",
            "displayOrder": 0
          },
        "courtCode": "$courtId",
        "courtCaseReference": "$caseReference",
        "appearanceDate": "2024-09-23",
        "warrantId": "7e15b408-4f97-453e-98b7-24791978221c",
        "warrantType": "REMAND",
        "taggedBail": null,
        "nextCourtAppearance": {
            "appearanceDate": "2024-12-10",
            "courtCode": "$courtId",
            "appearanceType": "Court appearance"
        },
        "charges": [
            {
                "chargeUuid": "6c591b18-642a-484a-a967-2d17b5c9c5a1",
                "lifetimeUuid": "$courtCharge1Id",
                "offenceCode": "PS90037",
                "offenceStartDate": "2024-01-15",
                "offenceEndDate": null,
                "outcome": {
                  "outcomeUuid": "8b28aa1b-8e2c-4c77-ad32-6feca8b0e459",
                  "outcomeName": "description of outcome",
                  "nomisCode": "$COURT_CHARGE_1_RESULT_CODE",
                  "outcomeType": "UNKNOWN",
                  "displayOrder": 0
                },
                "terrorRelated": null,
                "sentence": null
            },
            {
                "chargeUuid": "7c591b18-642a-484a-a967-2d17b5c9c5a1",
                "lifetimeUuid": "$courtCharge2Id",
                "offenceCode": "PS90090",
                "offenceStartDate": "2024-01-17",
                "offenceEndDate": "2024-01-19",
                "outcome": {
                  "outcomeUuid": "8b28aa1b-8e2c-4c77-ad32-6feca8b0e459",
                  "outcomeName": "description of outcome",
                  "nomisCode": "$COURT_CHARGE_2_RESULT_CODE",
                  "outcomeType": "UNKNOWN",
                  "displayOrder": 0
                },
                "terrorRelated": null,
                "sentence": null
            }
        ]
    },
    "appearances": [
        {
            "appearanceUuid": "6c591b18-642a-484a-a967-2d17b5c9c5a1",
            "lifetimeUuid": "$courtAppearanceId",
            "outcome": {
              "outcomeUuid": "8b28aa1b-8e2c-4c77-ad32-6feca8b0e459",
              "relatedChargeOutcomeUuid": "9928aa1b-8e2c-4c77-ad32-6feca8b0e459",
              "outcomeName": "Remanded in custody",
              "nomisCode": "4531",
              "outcomeType": "UNKNOWN",
              "displayOrder": 0
            },
            "courtCode": "$courtId",
            "courtCaseReference": "G123456789",
            "appearanceDate": "2024-09-23",
            "warrantId": "7e15b408-4f97-453e-98b7-24791978221c",
            "warrantType": "REMAND",
            "taggedBail": null,
            "nextCourtAppearance": {
                "appearanceDate": "2024-12-10",
                "courtCode": "$courtId",
                "appearanceType": "Court appearance"
            },
            "charges": [
              {
                "chargeUuid": "6c591b18-642a-484a-a967-2d17b5c9c5a1",
                "lifetimeUuid": "$courtCharge1Id",
                "offenceCode": "PS90037",
                "offenceStartDate": "2024-01-15",
                "offenceEndDate": null,
                  "outcome": {
                    "outcomeUuid": "8b28aa1b-8e2c-4c77-ad32-6feca8b0e459",
                    "outcomeName": "description of outcome",
                    "nomisCode": "$COURT_CHARGE_1_RESULT_CODE",
                    "outcomeType": "UNKNOWN",
                    "displayOrder": 0
                  },
                "terrorRelated": null,
                "sentence": null
              },
              {
                  "chargeUuid": "7c591b18-642a-484a-a967-2d17b5c9c5a1",
                  "lifetimeUuid": "$courtCharge2Id",
                  "offenceCode": "PS90090",
                  "offenceStartDate": "2024-01-17",
                  "offenceEndDate": "2024-01-19",
                  "outcome": {
                    "outcomeUuid": "8b28aa1b-8e2c-4c77-ad32-6feca8b0e459",
                    "outcomeName": "description of outcome",
                    "nomisCode": "$COURT_CHARGE_2_RESULT_CODE",
                    "outcomeType": "UNKNOWN",
                    "displayOrder": 0
                  },
                  "terrorRelated": null,
                  "sentence": null
              }
            ]
        }
    ],
    "legacyData": {
        "caseReferences": [
          {
            "updatedDate": "2024-10-14T10:27:42Z",
            "offenderCaseReference": "VB12345677"
          },
          {
            "updatedDate": "2024-10-14T10:37:00Z",
            "offenderCaseReference": "NN12345666"
          }
        ]
      }
}   
            """.trimIndent(),
          )
          .withStatus(200),
      ),
    )
  }

  fun getCharge(courtCaseId: String, courtAppearanceId: String, courtChargeId: String, offenderNo: String) = LegacyCharge(
    lifetimeUuid = UUID.fromString(courtChargeId),
    courtCaseUuid = courtCaseId,
    offenceCode = COURT_CHARGE_1_OFFENCE_CODE,
    offenceStartDate = LocalDate.parse(COURT_CHARGE_1_OFFENCE_DATE),
    offenceEndDate = LocalDate.parse(COURT_CHARGE_1_OFFENCE_END_DATE),
    prisonerId = offenderNo,
    nomisOutcomeCode = COURT_CHARGE_1_RESULT_CODE,
  )
  fun stubCourtAppearanceGetWithFourCharges(courtCaseId: String, courtAppearanceId: String, courtCharge1Id: String, courtCharge2Id: String, courtCharge3Id: String, courtCharge4Id: String, offenderNo: String = "A6160DZ") {
    val courtAppearance = LegacyCourtAppearance(
      lifetimeUuid = UUID.fromString(courtAppearanceId),
      courtCaseUuid = courtCaseId,
      prisonerId = offenderNo,
      courtCode = "DDOC",
      appearanceDate = LocalDate.parse("2024-09-23"),
      charges = listOf(
        LegacyCharge(
          lifetimeUuid = UUID.fromString(courtCharge1Id),
          courtCaseUuid = courtCaseId,
          offenceCode = COURT_CHARGE_1_OFFENCE_CODE,
          offenceStartDate = LocalDate.parse(COURT_CHARGE_1_OFFENCE_DATE),
          offenceEndDate = LocalDate.parse(COURT_CHARGE_1_OFFENCE_END_DATE),
          prisonerId = offenderNo,
          nomisOutcomeCode = COURT_CHARGE_1_RESULT_CODE,
        ),
        LegacyCharge(
          lifetimeUuid = UUID.fromString(courtCharge2Id),
          courtCaseUuid = courtCaseId,
          offenceCode = COURT_CHARGE_2_OFFENCE_CODE,
          offenceStartDate = LocalDate.parse(COURT_CHARGE_2_OFFENCE_DATE),
          offenceEndDate = LocalDate.parse(COURT_CHARGE_2_OFFENCE_END_DATE),
          prisonerId = offenderNo,
          nomisOutcomeCode = COURT_CHARGE_2_RESULT_CODE,
        ),
        LegacyCharge(
          lifetimeUuid = UUID.fromString(courtCharge3Id),
          courtCaseUuid = courtCaseId,
          offenceCode = COURT_CHARGE_3_OFFENCE_CODE,
          offenceStartDate = LocalDate.parse(COURT_CHARGE_3_OFFENCE_DATE),
          prisonerId = offenderNo,
          nomisOutcomeCode = COURT_CHARGE_3_RESULT_CODE,
        ),
        LegacyCharge(
          lifetimeUuid = UUID.fromString(courtCharge4Id),
          courtCaseUuid = courtCaseId,
          offenceCode = COURT_CHARGE_4_OFFENCE_CODE,
          offenceStartDate = LocalDate.parse(COURT_CHARGE_4_OFFENCE_DATE),
          prisonerId = offenderNo,
          nomisOutcomeCode = COURT_CHARGE_4_RESULT_CODE,
        ),
      ),
      nomisOutcomeCode = "4531",
    )

    stubFor(
      get(WireMock.urlPathMatching("/legacy/court-appearance/$courtAppearanceId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            objectMapper().writeValueAsString(courtAppearance),
          )
          .withStatus(200),
      ),
    )
  }
  fun stubCourtAppearanceGetWitOneCharge(courtCaseId: String, courtAppearanceId: String, courtCharge1Id: String, offenderNo: String = "A6160DZ") {
    val courtAppearance = LegacyCourtAppearance(
      lifetimeUuid = UUID.fromString(courtAppearanceId),
      courtCaseUuid = courtCaseId,
      prisonerId = offenderNo,
      courtCode = "DDOC",
      appearanceDate = LocalDate.parse("2024-09-23"),
      charges = listOf(
        LegacyCharge(
          lifetimeUuid = UUID.fromString(courtCharge1Id),
          courtCaseUuid = courtCaseId,
          offenceCode = COURT_CHARGE_1_OFFENCE_CODE,
          offenceStartDate = LocalDate.parse(COURT_CHARGE_1_OFFENCE_DATE),
          offenceEndDate = LocalDate.parse(COURT_CHARGE_1_OFFENCE_END_DATE),
          prisonerId = offenderNo,
          nomisOutcomeCode = COURT_CHARGE_1_RESULT_CODE,
        ),
      ),
      nomisOutcomeCode = "4531",
    )
    stubFor(
      get(WireMock.urlPathMatching("/legacy/court-appearance/$courtAppearanceId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            objectMapper().writeValueAsString(courtAppearance),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubGetCourtCharge(courtChargeId: String, caseID: String, offenderNo: String = "A6160DZ") {
    val courtCase = LegacyCharge(
      lifetimeUuid = UUID.fromString(courtChargeId),
      courtCaseUuid = caseID,
      offenceCode = COURT_CHARGE_1_OFFENCE_CODE,
      offenceStartDate = LocalDate.parse(COURT_CHARGE_1_OFFENCE_DATE),
      offenceEndDate = LocalDate.parse(COURT_CHARGE_1_OFFENCE_END_DATE),
      prisonerId = offenderNo,
      nomisOutcomeCode = COURT_CHARGE_1_RESULT_CODE,
    )
    stubFor(
      get(WireMock.urlPathMatching("/legacy/charge/$courtChargeId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            objectMapper().writeValueAsString(courtCase),
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
