@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.reset
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.NOMIS_BOOKING_ID
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CaseReferenceLegacyData
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CourtCaseLegacyData
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationCharge
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationNextCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationPeriodLength
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationSentence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtEventChargeResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtOrderResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenceResultCodeResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderChargeResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.SentenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.SentenceTermResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.contactPerson
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private const val NOMIS_COURT_CASE_ID = 7L
private const val NOMIS_COURT_CASE_2_ID = 8L
private const val DPS_COURT_CASE_ID = "4321"
private const val DPS_COURT_CASE_2_ID = "4321"
private const val DPS_COURT_APPEARANCE_ID = "9c591b18-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_COURT_APPEARANCE_2_ID = "45591b18-642a-484a-a967-2d17b5c9c5a1"
private const val NOMIS_COURT_APPEARANCE_ID = 3L
private const val NOMIS_COURT_APPEARANCE_2_ID = 4L
private const val NOMIS_COURT_CHARGE_ID = 11L
private const val NOMIS_COURT_CHARGE_2_ID = 12L
private const val NOMIS_COURT_CHARGE_3_ID = 13L
private const val DPS_COURT_CHARGE_ID = "8576aa44-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_COURT_CHARGE_2_ID = "4576aa44-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_COURT_CHARGE_3_ID = "2376aa44-642a-484a-a967-2d17b5c9c5a1"
private const val OFFENDER_NO = "AB12345"
private const val PRISON_MDI = "MDI"
private const val PRISON_LEI = "LEI"
private const val CASE_REFERENCE = "ABC4999"
private const val CASE_REFERENCE2 = "ABC4888"
private const val OFFENCE_CODE_1 = "TR11017"
private const val OFFENCE_CODE_2 = "PR52028A"
private const val OUTCOME_1 = "4001"
private const val OUTCOME_2 = "3001"
private const val YEARS = 6
private const val MONTHS = 5
private const val WEEKS = 4
private const val DAYS = 3
private const val DPS_PERIOD_LENGTH_ID = "87591b18-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_SENTENCE_ID = "1c591b18-642a-484a-a967-2d17b5c9c5a1"
private const val SENTENCE_CATEGORY = "2020"
private const val SENTENCE_CALC_TYPE = "ADIMP_ORA"
private const val NOMIS_SENTENCE_SEQ = 3L
private const val NOMIS_TERM_SEQ = 4L
private const val SENTENCE_TERM_TYPE = "IMP"

@SpringAPIServiceTest
@Import(
  CourtSentencingReconciliationService::class,
  CourtSentencingNomisApiService::class,
  CourtSentencingApiService::class,
  NomisApiService::class,
  CourtSentencingNomisApiMockServer::class,
  CourtSentencingApiMockServer::class,
  RetryApiService::class,
  CourtSentencingConfiguration::class,
  CourtCaseMappingService::class,
)
internal class CourtSentencingReconciliationServiceTest {
  @MockitoBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  private lateinit var nomisApi: CourtSentencingNomisApiMockServer

  private val dpsApi = CourtSentencingApiExtension.courtSentencingApi

  @Autowired
  private lateinit var service: CourtSentencingReconciliationService

  @Nested
  inner class CheckCourtCasesMatch {
    val personId = 1L
    val nomisPerson = contactPerson(personId = personId).copy(
      firstName = "KWEKU",
      lastName = "KOFI",
      phoneNumbers = emptyList(),
      employments = emptyList(),
      identifiers = emptyList(),
      addresses = emptyList(),
      emailAddresses = emptyList(),
      contacts = emptyList(),
      restrictions = emptyList(),
    )

    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
    }

    private fun stubCase(nomisCase: CourtCaseResponse, dpsCase: ReconciliationCourtCase) {
      nomisApi.stubGetCourtCaseForReconciliation(NOMIS_COURT_CASE_ID, nomisCase)
      dpsApi.stubGetCourtCaseForReconciliation(DPS_COURT_CASE_ID, dpsCase)
    }

    @Nested
    inner class WhenCaseHasMinimalDataThatMatches {

      @BeforeEach
      fun setUp() {
        stubCase(nomisCase = nomisCaseResponse(), dpsCase = dpsCourtCaseResponse())
      }

      @Test
      fun `will not report a mismatch when no differences found`() = runTest {
        assertThat(
          service.checkCase(nomisCaseId = NOMIS_COURT_CASE_ID, dpsCaseId = DPS_COURT_CASE_ID),
        ).isNull()
      }
    }

    @Test
    fun `will report an case status mismatch`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse().copy(caseStatus = CodeDescription("I", "Inactive")),
        dpsCase = dpsCourtCaseResponse(),
      )
      assertThat(
        service.checkCase(
          nomisCaseId = NOMIS_COURT_CASE_ID,
          dpsCaseId = DPS_COURT_CASE_ID,
        )?.differences,
      ).isEqualTo(listOf(Difference(property = "case.active", dps = true, nomis = false, id = DPS_COURT_CASE_ID)))
    }

    @Test
    fun `will report an extra appearance in dps`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse(),
        dpsCase = dpsCourtCaseResponse().copy(
          appearances = listOf(
            dpsAppearanceResponse(),
            dpsAppearanceResponseWithoutCharges().copy(
              appearanceUuid = UUID.fromString(DPS_COURT_APPEARANCE_2_ID),
              nomisOutcomeCode = OUTCOME_2,
            ),
          ),
        ),
      )
      assertThat(
        service.checkCase(
          nomisCaseId = NOMIS_COURT_CASE_ID,
          dpsCaseId = DPS_COURT_CASE_ID,
        )?.differences,
      ).isEqualTo(listOf(Difference(property = "case.appearances", dps = 2, nomis = 1)))
    }

    @Test
    fun `will report an extra appearance in nomis`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse().copy(
          courtEvents = listOf(
            nomisAppearanceResponse(),
            nomisAppearanceResponseWithoutCharges().copy(
              id = NOMIS_COURT_APPEARANCE_2_ID,
              outcomeReasonCode = nomisOffenceResult(OUTCOME_2),
            ),
          ),
        ),
        dpsCase = dpsCourtCaseResponse(),
      )
      assertThat(
        service.checkCase(
          nomisCaseId = NOMIS_COURT_CASE_ID,
          dpsCaseId = DPS_COURT_CASE_ID,
        )?.differences,
      ).isEqualTo(listOf(Difference(property = "case.appearances", dps = 1, nomis = 2)))
    }

    @Test
    fun `will cope with appearances received in different orders`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse().copy(
          courtEvents = listOf(
            nomisAppearanceResponseWithoutCharges().copy(
              eventDateTime = LocalDateTime.of(2024, 2, 2, 4, 0, 0),
              outcomeReasonCode = nomisOffenceResult(OUTCOME_2),
            ),
            nomisAppearanceResponse(
              eventDateTime = LocalDateTime.of(2024, 1, 1, 4, 0, 0),
            ),
          ),
        ),
        dpsCase = dpsCourtCaseResponse().copy(
          appearances = listOf(
            dpsAppearanceResponse(appearanceDate = LocalDate.of(2024, 1, 1)),
            dpsAppearanceResponseWithoutCharges().copy(
              appearanceDate = LocalDate.of(2024, 2, 2),
              nomisOutcomeCode = OUTCOME_2,
            ),
          ),
        ),
      )
      assertThat(
        service.checkCase(
          nomisCaseId = NOMIS_COURT_CASE_ID,
          dpsCaseId = DPS_COURT_CASE_ID,
        ),
      ).isNull()
    }

    @Test
    fun `will report an appearance outcome difference`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse().copy(
          courtEvents = listOf(
            nomisAppearanceResponse().copy(
              outcomeReasonCode = nomisOffenceResult(OUTCOME_2),
            ),
          ),
        ),
        dpsCase = dpsCourtCaseResponse().copy(
          appearances = listOf(
            dpsAppearanceResponse(appearanceDate = LocalDate.of(2024, 1, 1)),
          ),
        ),
      )
      assertThat(
        service.checkCase(
          nomisCaseId = NOMIS_COURT_CASE_ID,
          dpsCaseId = DPS_COURT_CASE_ID,
        )?.differences,
      ).isEqualTo(
        listOf(
          Difference(
            property = "case.appearances[0].outcome",
            dps = OUTCOME_1,
            nomis = OUTCOME_2,
            id = DPS_COURT_APPEARANCE_ID,
          ),
        ),
      )
    }

    @Test
    fun `will report an extra charge in dps`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse(),
        dpsCase = dpsCourtCaseResponse().copy(
          appearances = listOf(
            dpsAppearanceResponse().copy(
              charges = listOf(dpsChargeResponse(), dpsChargeResponseWithoutSentence()),
            ),
          ),
        ),
      )
      assertThat(
        service.checkCase(
          nomisCaseId = NOMIS_COURT_CASE_ID,
          dpsCaseId = DPS_COURT_CASE_ID,
        )?.differences,
      ).isEqualTo(listOf(Difference(property = "case.appearances[0].charges", dps = 2, nomis = 1)))
    }

    @Test
    fun `will report a charge difference`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse().copy(
          courtEvents = listOf(
            nomisAppearanceResponse().copy(
              courtEventCharges = listOf(
                nomisChargeResponse().copy(offenceDate = LocalDate.of(2022, 3, 3)),
                nomisChargeResponse(),
              ),
            ),
          ),
        ),
        dpsCase = dpsCourtCaseResponse().copy(
          appearances = listOf(
            dpsAppearanceResponse().copy(
              charges = listOf(
                dpsChargeResponse(),
                dpsChargeResponse().copy(offenceStartDate = LocalDate.of(2021, 3, 3)),
              ),
            ),
          ),
        ),
      )
      assertThat(
        service.checkCase(
          nomisCaseId = NOMIS_COURT_CASE_ID,
          dpsCaseId = DPS_COURT_CASE_ID,
        )?.differences,
      ).isEqualTo(listOf(Difference(property = "case.appearances[0].charges", dps = "2021-03-03", nomis = "2022-03-03", id = DPS_COURT_CHARGE_ID)))
    }

    @Test
    fun `will report an extra sentence in nomis`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse().copy(
          courtEvents = listOf(
            nomisAppearanceResponse().copy(
              courtEventCharges = listOf(nomisChargeResponse(), nomisChargeResponse()),
            ),
          ),
          sentences = listOf(nomisSentenceResponse(), nomisSentenceResponse()),
        ),
        dpsCase = dpsCourtCaseResponse().copy(
          appearances = listOf(
            dpsAppearanceResponse().copy(
              charges = listOf(dpsChargeResponse(), dpsChargeResponseWithoutSentence()),
            ),
          ),
        ),
      )
      assertThat(
        service.checkCase(
          nomisCaseId = NOMIS_COURT_CASE_ID,
          dpsCaseId = DPS_COURT_CASE_ID,
        )?.differences,
      ).isEqualTo(listOf(Difference(property = "case.sentences", dps = 1, nomis = 2)))
    }

    @Test
    fun `will process the same sentence appearing multiple times in the DPS response`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse().copy(
          courtEvents = listOf(
            nomisAppearanceResponse().copy(
              courtEventCharges = listOf(
                nomisChargeResponse(),
                nomisChargeResponse(offenderChargeId = NOMIS_COURT_CHARGE_2_ID, offenceCode = OFFENCE_CODE_2),
                nomisChargeResponse(offenderChargeId = NOMIS_COURT_CHARGE_3_ID),
              ),
            ),
          ),
          // 1 sentence from nomis
          sentences = listOf(nomisSentenceResponse()),
        ),
        dpsCase = dpsCourtCaseResponse().copy(
          appearances = listOf(
            dpsAppearanceResponse().copy(
              // will create 3 sentences with the same id (the same sentence)
              charges = listOf(
                dpsChargeResponse(),
                dpsChargeResponse(offenceCode = OFFENCE_CODE_2),
                dpsChargeResponse(),
              ),
            ),
          ),
        ),
      )
      assertThat(
        service.checkCase(
          nomisCaseId = NOMIS_COURT_CASE_ID,
          dpsCaseId = DPS_COURT_CASE_ID,
        ),
      ).isNull()
    }

    @Test
    fun `will report an extra sentence term in nomis`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse().copy(
          sentences = listOf(
            nomisSentenceResponse().copy(
              sentenceTerms = listOf(
                nomisSentenceTermResponse(),
                nomisSentenceTermResponse(),
                nomisSentenceTermResponse(),
              ),
            ),
          ),
        ),
        dpsCase = dpsCourtCaseResponse().copy(
          appearances = listOf(
            dpsAppearanceResponse().copy(
              charges = listOf(
                dpsChargeResponse().copy(
                  sentence = dpsSentenceResponse().copy(
                    periodLengths = listOf(
                      dpsPeriodLengthResponse(),
                      dpsPeriodLengthResponse(),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      )
      assertThat(
        service.checkCase(
          nomisCaseId = NOMIS_COURT_CASE_ID,
          dpsCaseId = DPS_COURT_CASE_ID,
        )?.differences,
      ).isEqualTo(listOf(Difference(property = "case.sentences[0].sentenceTerms", dps = 2, nomis = 3)))
    }

    @Test
    fun `will report an sentence term difference`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse().copy(
          sentences = listOf(
            nomisSentenceResponse().copy(
              sentenceTerms = listOf(
                nomisSentenceTermResponse(),
                nomisSentenceTermResponse(),
                nomisSentenceTermResponse(),
              ),
            ),
          ),
        ),
        dpsCase = dpsCourtCaseResponse().copy(
          appearances = listOf(
            dpsAppearanceResponse().copy(
              charges = listOf(
                dpsChargeResponse().copy(
                  sentence = dpsSentenceResponse().copy(
                    periodLengths = listOf(
                      dpsPeriodLengthResponse(),
                      dpsPeriodLengthResponse(),
                      dpsPeriodLengthResponse().copy(
                        periodYears = 20,
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      )
      assertThat(
        service.checkCase(
          nomisCaseId = NOMIS_COURT_CASE_ID,
          dpsCaseId = DPS_COURT_CASE_ID,
        )?.differences,
      ).isEqualTo(listOf(Difference(property = "case.sentences[0].sentenceTerms[2].years", dps = 20, nomis = 6, id = DPS_PERIOD_LENGTH_ID)))
    }
  }
}

fun dpsCourtCaseResponse(
  active: Boolean = true,
  appearances: List<ReconciliationCourtAppearance> = listOf(dpsAppearanceResponse()),
  caseReferences: List<CaseReferenceLegacyData> = listOf(
    CaseReferenceLegacyData(
      CASE_REFERENCE,
      updatedDate = LocalDateTime.now(),
    ),
  ),
) = ReconciliationCourtCase(
  courtCaseUuid = DPS_COURT_CASE_ID,
  prisonerId = OFFENDER_NO,
  active = active,
  appearances = appearances,
  courtCaseLegacyData = CourtCaseLegacyData(caseReferences),
  merged = false,
)

fun dpsAppearanceResponse(
  appearanceUuid: UUID = UUID.fromString(DPS_COURT_APPEARANCE_ID),
  outcome: String = OUTCOME_1,
  charges: List<ReconciliationCharge> = listOf(dpsChargeResponse()),
  appearanceDate: LocalDate = LocalDate.of(2024, 1, 1),
) = ReconciliationCourtAppearance(
  appearanceUuid = appearanceUuid,
  // courtCaseUuid = DPS_COURT_CASE_ID,
  courtCode = PRISON_LEI,
  appearanceDate = appearanceDate,
  appearanceTime = "10:10",
  nomisOutcomeCode = outcome,
  charges = charges,
  // prisonerId = OFFENDER_NO,
  nextCourtAppearance = ReconciliationNextCourtAppearance(
    appearanceDate = LocalDate.of(2024, 2, 1),
    appearanceTime = "10:10",
    courtId = PRISON_MDI,
  ),
)

fun dpsAppearanceResponseWithoutCharges() = dpsAppearanceResponse(charges = emptyList())

fun nomisCaseResponse(
  id: Long = NOMIS_COURT_CASE_ID,
  beginDate: LocalDate = LocalDate.of(2024, 1, 1),
  events: List<CourtEventResponse> = listOf(nomisAppearanceResponse()),
  sentences: List<SentenceResponse> = listOf(nomisSentenceResponse()),
) = CourtCaseResponse(
  id = id,
  offenderNo = OFFENDER_NO,
  courtEvents = events,
  courtId = PRISON_LEI,
  createdDateTime = LocalDateTime.now(),
  createdByUsername = "Q1251T",
  caseStatus = CodeDescription("A", "Active"),
  legalCaseType = CodeDescription("CRT", "Court Appearance"),
  beginDate = beginDate,
  bookingId = 1,
  lidsCaseNumber = 1,
  offenderCharges = emptyList(),
  caseSequence = 1,
  caseInfoNumbers = emptyList(),
  sentences = sentences,
)

fun nomisAppearanceResponse(
  id: Long = NOMIS_COURT_APPEARANCE_ID,
  outcomeCode: String? = OUTCOME_1,
  eventDateTime: LocalDateTime = LocalDateTime.of(2024, 1, 1, 10, 10, 0),
  nextEventDateTime: LocalDateTime = LocalDateTime.of(2024, 2, 1, 10, 10, 0),
  charges: List<CourtEventChargeResponse> = listOf(nomisChargeResponse()),
) = CourtEventResponse(
  id = id,
  offenderNo = OFFENDER_NO,
  caseId = NOMIS_COURT_CASE_ID,
  courtId = PRISON_LEI,
  courtEventCharges = charges,
  createdDateTime = LocalDateTime.now(),
  createdByUsername = "Q1251T",
  courtEventType = CodeDescription("CRT", "Court Appearance"),
  outcomeReasonCode = outcomeCode?.let {
    OffenceResultCodeResponse(
      code = outcomeCode,
      description = "Outcome text",
      dispositionCode = "F",
      chargeStatus = "A",
      conviction = true,
    )
  },
  eventStatus = CodeDescription("SCH", "Scheduled (Approved)"),
  eventDateTime = eventDateTime,
  courtOrders = emptyList(),
  nextEventDateTime = nextEventDateTime,
)

fun nomisAppearanceResponseWithoutCharges() = nomisAppearanceResponse(charges = emptyList())

fun nomisOffenceResult(code: String) = OffenceResultCodeResponse(
  code = code,
  description = "Outcome text",
  dispositionCode = "F",
  chargeStatus = "A",
  conviction = true,
)

fun nomisChargeResponse(
  eventId: Long = NOMIS_COURT_APPEARANCE_ID,
  offenderChargeId: Long = NOMIS_COURT_CHARGE_ID,
  offenceCode: String = OFFENCE_CODE_1,
  offenceStartDate: LocalDate = LocalDate.of(2023, 1, 1),
) = CourtEventChargeResponse(
  eventId = eventId,
  offenderCharge = OffenderChargeResponse(
    id = offenderChargeId,
    offence = OffenceResponse(
      offenceCode = offenceCode,
      statuteCode = "RR84",
      description = "Offence text",
    ),
    mostSeriousFlag = false,
    offenceDate = offenceStartDate,
    offenceEndDate = offenceStartDate.plusDays(1),
    resultCode1 = OffenceResultCodeResponse(
      code = OUTCOME_1,
      description = "Outcome text",
      dispositionCode = "F",
      chargeStatus = "A",
      conviction = true,
    ),
  ),
  offenceDate = offenceStartDate,
  offenceEndDate = offenceStartDate.plusDays(1),
  mostSeriousFlag = false,
  resultCode1 = OffenceResultCodeResponse(
    code = OUTCOME_1,
    description = "Outcome text",
    dispositionCode = "F",
    chargeStatus = "A",
    conviction = true,
  ),
)

fun nomisOffenderChargeResponse(
  offenderChargeId: Long = NOMIS_COURT_CHARGE_ID,
  offenceCode: String = OFFENCE_CODE_1,
  offenceStartDate: LocalDate = LocalDate.of(2023, 1, 1),
) = OffenderChargeResponse(
  id = offenderChargeId,
  offence = OffenceResponse(
    offenceCode = offenceCode,
    statuteCode = "RR84",
    description = "Offence text",
  ),
  mostSeriousFlag = false,
  offenceDate = offenceStartDate,
  offenceEndDate = offenceStartDate.plusDays(1),
  resultCode1 = OffenceResultCodeResponse(
    code = OUTCOME_1,
    description = "Outcome text",
    dispositionCode = "F",
    chargeStatus = "A",
    conviction = true,
  ),
)

fun nomisSentenceResponse(
  eventId: Long = NOMIS_COURT_APPEARANCE_ID,
  terms: List<SentenceTermResponse> = listOf(nomisSentenceTermResponse()),
  charges: List<OffenderChargeResponse> = listOf(nomisOffenderChargeResponse()),
) = SentenceResponse(
  sentenceSeq = NOMIS_SENTENCE_SEQ,
  bookingId = NOMIS_BOOKING_ID,
  category = CodeDescription(SENTENCE_CATEGORY, "desc"),
  calculationType = CodeDescription(SENTENCE_CALC_TYPE, "desc"),
  startDate = LocalDate.of(2023, 1, 1),
  status = "A",
  sentenceTerms = terms,
  fineAmount = BigDecimal.TEN,
  missingCourtOffenderChargeIds = emptyList(),
  createdByUsername = "Q1251T",
  createdDateTime = LocalDateTime.now(),
  offenderCharges = charges,
  prisonId = PRISON_MDI,
  courtOrder = CourtOrderResponse(
    eventId = eventId,
    id = 1234,
    courtDate = LocalDate.now(),
    issuingCourt = "TFG",
    sentencePurposes = emptyList(),
    orderType = "type",
    orderStatus = "status",
  ),
)

fun nomisSentenceTermResponse(
  months: Int = MONTHS,
  termType: String = SENTENCE_TERM_TYPE,
  lifeSentence: Boolean = false,
) = SentenceTermResponse(
  years = YEARS,
  months = MONTHS,
  weeks = WEEKS,
  days = DAYS,
  sentenceTermType = CodeDescription(termType, "desc"),
  lifeSentenceFlag = lifeSentence,
  termSequence = NOMIS_TERM_SEQ,
  prisonId = PRISON_MDI,
  startDate = LocalDate.of(2023, 1, 1),
)

fun dpsChargeResponse(
  offenceCode: String = OFFENCE_CODE_1,
  offenceStartDate: LocalDate = LocalDate.of(2023, 1, 1),
  sentenceResponse: ReconciliationSentence? = dpsSentenceResponse(),
) = ReconciliationCharge(
  chargeUuid = UUID.fromString(DPS_COURT_CHARGE_ID),
  offenceCode = offenceCode,
  offenceStartDate = offenceStartDate,
  offenceEndDate = offenceStartDate.plusDays(1),
  nomisOutcomeCode = OUTCOME_1,
  sentence = sentenceResponse,
  // = DPS_COURT_CASE_ID,
  // prisonerId = OFFENDER_NO,
)

fun dpsChargeResponseWithoutSentence() = dpsChargeResponse(sentenceResponse = null)

fun dpsSentenceResponse(periodLengths: List<ReconciliationPeriodLength> = listOf(dpsPeriodLengthResponse())) = ReconciliationSentence(
  sentenceUuid = UUID.fromString(DPS_SENTENCE_ID),
  sentenceCategory = SENTENCE_CATEGORY,
  sentenceCalcType = SENTENCE_CALC_TYPE,
  sentenceStartDate = LocalDate.of(2023, 1, 1),
  active = true,
  periodLengths = periodLengths,
  fineAmount = BigDecimal.TEN,
)

fun dpsPeriodLengthResponse() = ReconciliationPeriodLength(
  periodYears = YEARS,
  periodMonths = MONTHS,
  periodWeeks = WEEKS,
  periodDays = DAYS,
  sentenceTermCode = SENTENCE_TERM_TYPE,
  lifeSentence = false,
  periodLengthUuid = UUID.fromString(DPS_PERIOD_LENGTH_ID),
)
