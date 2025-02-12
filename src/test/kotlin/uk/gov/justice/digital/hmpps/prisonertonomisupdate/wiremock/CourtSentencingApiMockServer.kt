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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyPeriodLength
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacySentence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.TestCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.objectMapper
import java.math.BigDecimal
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

class CourtSentencingApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
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

  fun stubCourtCaseGet(courtCaseId: String, courtCaseResponse: LegacyCourtCase) {
    stubFor(
      get(WireMock.urlPathMatching("/legacy/court-case/$courtCaseId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            objectMapper().writeValueAsString(courtCaseResponse),
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

  fun stubCourtAppearanceGetWithFourCharges(
    courtCaseId: String,
    courtAppearanceId: String,
    courtCharge1Id: String,
    courtCharge2Id: String,
    courtCharge3Id: String,
    courtCharge4Id: String,
    offenderNo: String = "A6160DZ",
  ) {
    val courtAppearance = LegacyCourtAppearance(
      lifetimeUuid = UUID.fromString(courtAppearanceId),
      courtCaseUuid = courtCaseId,
      prisonerId = offenderNo,
      courtCode = "DDOC",
      appearanceDate = LocalDate.parse("2024-09-23"),
      appearanceTime = "10:00",
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

  fun stubCourtAppearanceGetWitOneCharge(
    courtCaseId: String,
    courtAppearanceId: String,
    courtCharge1Id: String,
    offenderNo: String = "A6160DZ",
  ) {
    val courtAppearance = LegacyCourtAppearance(
      lifetimeUuid = UUID.fromString(courtAppearanceId),
      courtCaseUuid = courtCaseId,
      prisonerId = offenderNo,
      courtCode = "DDOC",
      appearanceDate = LocalDate.parse("2024-09-23"),
      appearanceTime = "10:00:00",
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

  fun stubGetSentence(
    sentenceId: String,
    caseID: String,
    chargeUuid: String? = UUID.randomUUID().toString(),
    periodLengths: List<LegacyPeriodLength> = listOf(
      LegacyPeriodLength(
        periodYears = 2,
        periodMonths = 6,
        periodDays = 15,
        sentenceTermCode = "TERM",
      ),
    ),
    offenderNo: String = "A6160DZ",
    fineAmount: BigDecimal = BigDecimal("1000.00"),
  ) {
    val sentence = LegacySentence(
      prisonerId = offenderNo,
      chargeLifetimeUuid = UUID.fromString(chargeUuid),
      lifetimeUuid = UUID.fromString(sentenceId),
      periodLengths = periodLengths,
      sentenceCalcType = "CALC",
      sentenceCategory = "CAT",
      consecutiveToLifetimeUuid = UUID.randomUUID(),
      chargeNumber = chargeUuid,
      fineAmount = fineAmount,
      legacyData = null,
      courtCaseId = caseID,
    )
    stubFor(
      get(WireMock.urlPathMatching("/legacy/sentence/$sentenceId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            objectMapper().writeValueAsString(sentence),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubGetCourtCaseForReconciliation2(courtCaseId: String, courtCaseResponse: TestCourtCase) {
    stubFor(
      get(WireMock.urlPathMatching("/legacy/court-case/$courtCaseId/test")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            objectMapper().writeValueAsString(courtCaseResponse),
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
