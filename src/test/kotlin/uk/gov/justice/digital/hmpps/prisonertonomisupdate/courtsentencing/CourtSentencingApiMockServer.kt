package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CaseReferenceLegacyData
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CourtCaseLegacyData
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCharge
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyCourtCaseUuids
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyNextCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyPeriodLength
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacyRecall
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacySearchSentence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.LegacySentence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationCharge
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationNextCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationPeriodLength
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationSentence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
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
    lateinit var jsonMapper: JsonMapper

    fun legacySentence(sentenceId: String, sentenceCalcType: String, sentenceCategory: String = "2020", active: Boolean = true) = LegacySentence(
      prisonerId = "A6160DZ",
      chargeLifetimeUuid = UUID.randomUUID(),
      lifetimeUuid = UUID.fromString(sentenceId),
      sentenceCalcType = sentenceCalcType,
      sentenceCategory = sentenceCategory,
      consecutiveToLifetimeUuid = null,
      chargeNumber = null,
      fineAmount = null,
      courtCaseId = UUID.randomUUID().toString(),
      sentenceStartDate = LocalDate.now().minusYears(1),
      active = active,
      appearanceUuid = UUID.randomUUID(),
    )

    fun reconciliationCourtCase(
      active: Boolean = true,
      appearances: List<ReconciliationCourtAppearance> = listOf(reconciliationCourtAppearance()),
      caseReferences: List<CaseReferenceLegacyData> = listOf(
        CaseReferenceLegacyData(
          "ABC4999",
          updatedDate = LocalDateTime.now(),
        ),
      ),
    ) = ReconciliationCourtCase(
      courtCaseUuid = UUID.randomUUID().toString(),
      prisonerId = "A1234KT",
      active = active,
      appearances = appearances,
      courtCaseLegacyData = CourtCaseLegacyData(caseReferences),
      merged = false,
      status = if (active) ReconciliationCourtCase.Status.ACTIVE else ReconciliationCourtCase.Status.INACTIVE,
    )

    fun reconciliationCourtAppearance(
      appearanceUuid: UUID = UUID.randomUUID(),
      outcome: String = "4001",
      charges: List<ReconciliationCharge> = listOf(reconciliationChargeWithoutSentence()),
      appearanceDate: LocalDate = LocalDate.parse("2024-01-01"),
    ) = ReconciliationCourtAppearance(
      appearanceUuid = appearanceUuid,
      courtCode = "LEI",
      appearanceDate = appearanceDate,
      appearanceTime = "10:10",
      nomisOutcomeCode = outcome,
      charges = charges,
      nextCourtAppearance = ReconciliationNextCourtAppearance(
        appearanceDate = LocalDate.parse("2024-02-01"),
        appearanceTime = "10:10",
        courtId = "MDI",
      ),
      appearanceTypeUuid = UUID.fromString("1da09b6e-55cb-4838-a157-ee6944f2094c"),
    )

    fun reconciliationCharge(
      offenceCode: String = "TR11017",
      offenceStartDate: LocalDate = LocalDate.parse("2021-01-01"),
      sentenceResponse: ReconciliationSentence? = reconciliationSentence(),
    ) = ReconciliationCharge(
      chargeUuid = UUID.randomUUID(),
      offenceCode = offenceCode,
      offenceStartDate = offenceStartDate,
      offenceEndDate = offenceStartDate.plusDays(1),
      nomisOutcomeCode = "4001",
      sentence = sentenceResponse,
    )

    fun reconciliationChargeWithoutSentence() = reconciliationCharge(sentenceResponse = null)

    fun reconciliationSentence(periodLengths: List<ReconciliationPeriodLength> = listOf(reconciliationPeriodLength())) = ReconciliationSentence(
      sentenceUuid = UUID.randomUUID(),
      sentenceCategory = "2020",
      sentenceCalcType = "ADIMP_ORA",
      sentenceStartDate = LocalDate.of(2024, 1, 1),
      active = true,
      periodLengths = periodLengths,
      fineAmount = BigDecimal.valueOf(750.00),
    )

    fun reconciliationPeriodLength() = ReconciliationPeriodLength(
      periodYears = 6,
      periodMonths = 5,
      periodWeeks = 4,
      periodDays = 3,
      sentenceTermCode = "IMP",
      lifeSentence = false,
      periodLengthUuid = UUID.randomUUID(),
    )
  }

  override fun beforeAll(context: ExtensionContext) {
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
    courtSentencingApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    courtSentencingApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    courtSentencingApi.stop()
  }
}

private const val APPEARANCE_TYPE_CRT = "63e8fce0-033c-46ad-9edf-391b802d547a"

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
            jsonMapper.writeValueAsString(courtCaseResponse),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubCourtCaseGetError(courtCaseId: String, status: Int = 404) {
    stubGetWithError("/legacy/court-case/$courtCaseId", status)
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
    appearanceType: String = APPEARANCE_TYPE_CRT,
    nextCourtAppearance: LegacyNextCourtAppearance? = null,
  ) {
    val courtAppearance = LegacyCourtAppearance(
      lifetimeUuid = UUID.fromString(courtAppearanceId),
      courtCaseUuid = courtCaseId,
      prisonerId = offenderNo,
      courtCode = "DDOC",
      appearanceDate = LocalDate.parse("2024-09-23"),
      appearanceTime = "10:00",
      nextCourtAppearance = nextCourtAppearance,
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
      appearanceTypeUuid = UUID.fromString(appearanceType),
    )

    stubFor(
      get(WireMock.urlPathMatching("/legacy/court-appearance/$courtAppearanceId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            jsonMapper.writeValueAsString(courtAppearance),
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
      appearanceTypeUuid = UUID.fromString(APPEARANCE_TYPE_CRT),
    )
    stubFor(
      get(WireMock.urlPathMatching("/legacy/court-appearance/$courtAppearanceId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            jsonMapper.writeValueAsString(courtAppearance),
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
            jsonMapper.writeValueAsString(courtCase),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubGetCourtChargeByAppearance(courtChargeId: String, courtAppearanceId: String, caseID: String, offenderNo: String = "A6160DZ") {
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
      get(WireMock.urlPathMatching("/legacy/court-appearance/$courtAppearanceId/charge/$courtChargeId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            jsonMapper.writeValueAsString(courtCase),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubGetSentence(
    sentenceId: String,
    caseID: String,
    chargeUuid: String? = UUID.randomUUID().toString(),
    offenderNo: String = "A6160DZ",
    fineAmount: BigDecimal = BigDecimal("1000.00"),
    startDate: LocalDate = LocalDate.now(),
    active: Boolean = true,
    consecutiveToLifetimeUuid: UUID? = null,
  ) {
    val sentence = LegacySentence(
      prisonerId = offenderNo,
      chargeLifetimeUuid = UUID.fromString(chargeUuid),
      lifetimeUuid = UUID.fromString(sentenceId),
      sentenceCalcType = "CALC",
      sentenceCategory = "CAT",
      consecutiveToLifetimeUuid = consecutiveToLifetimeUuid,
      chargeNumber = chargeUuid,
      fineAmount = fineAmount,
      courtCaseId = caseID,
      sentenceStartDate = startDate,
      active = active,
      appearanceUuid = UUID.randomUUID(),
    )
    stubFor(
      get(WireMock.urlPathMatching("/legacy/sentence/$sentenceId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            jsonMapper.writeValueAsString(sentence),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubGetSentences(
    request: LegacySearchSentence,
    sentences: List<LegacySentence>,
  ) {
    stubFor(
      WireMock.post(WireMock.urlPathMatching("/legacy/sentence/search"))
        .withRequestBody(equalToJson(MappingExtension.jsonMapper.writeValueAsString(request)))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              jsonMapper.writeValueAsString(sentences),
            )
            .withStatus(200),
        ),
    )
  }

  fun stubGetPeriodLength(
    periodLengthId: String,
    sentenceId: String,
    chargeId: String,
    appearanceId: String,
    offenderNo: String = "A6160DZ",
    caseId: String,
    periodLength: LegacyPeriodLength = LegacyPeriodLength(
      periodYears = 2,
      periodMonths = 6,
      periodWeeks = 4,
      periodDays = 15,
      sentenceTermCode = "TERM",
      periodLengthUuid = UUID.randomUUID(),
      courtChargeId = UUID.fromString(chargeId),
      prisonerId = offenderNo,
      courtCaseId = caseId,
      courtAppearanceId = UUID.fromString(appearanceId),
      sentenceUuid = UUID.fromString(sentenceId),
    ),
  ) {
    stubFor(
      get(WireMock.urlPathMatching("/legacy/period-length/$periodLengthId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            jsonMapper.writeValueAsString(periodLength),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubGetCourtCaseForReconciliation(courtCaseId: String, courtCaseResponse: ReconciliationCourtCase) {
    stubFor(
      get(WireMock.urlPathMatching("/legacy/court-case/$courtCaseId/reconciliation")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            jsonMapper.writeValueAsString(courtCaseResponse),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubGetRecall(recallId: String, recall: LegacyRecall) {
    stubFor(
      get(WireMock.urlPathMatching("/legacy/recall/$recallId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            jsonMapper.writeValueAsString(recall),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubGetCourtCaseIdsForReconciliation(offenderNo: String, courtCaseUuids: LegacyCourtCaseUuids) {
    stubFor(
      get(WireMock.urlPathMatching("/legacy/prisoner/$offenderNo/court-case-uuids")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            jsonMapper.writeValueAsString(courtCaseUuids),
          )
          .withStatus(200),
      ),
    )
  }

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder {
    this.withBody(NomisApiExtension.jsonMapper.writeValueAsString(body))
    return this
  }

  fun getCountFor(url: String) = this.findAll(WireMock.getRequestedFor(WireMock.urlEqualTo(url))).count()

  private fun stubGetWithError(url: String, status: Int = 500) = stubFor(
    get(url).willReturn(
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
