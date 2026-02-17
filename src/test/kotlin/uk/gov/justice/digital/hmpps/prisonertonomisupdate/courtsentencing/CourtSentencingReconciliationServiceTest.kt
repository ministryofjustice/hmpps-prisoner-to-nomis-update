@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.description
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.activities.NOMIS_BOOKING_ID
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.CaseReferenceLegacyData
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationCharge
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationPeriodLength
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationSentence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.SentenceLegacyData
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingApiExtension.Companion.reconciliationCharge
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingApiExtension.Companion.reconciliationCourtAppearance
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingApiExtension.Companion.reconciliationCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingApiExtension.Companion.reconciliationPeriodLength
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingApiExtension.Companion.reconciliationSentence
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private const val NOMIS_COURT_CASE_ID = 7L
private const val DPS_COURT_CASE_ID = "4321"
private const val DPS_COURT_APPEARANCE_ID = "9c591b18-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_COURT_APPEARANCE_2_ID = "45591b18-642a-484a-a967-2d17b5c9c5a1"
private const val NOMIS_COURT_APPEARANCE_ID = 3L
private const val NOMIS_COURT_APPEARANCE_2_ID = 4L
private const val NOMIS_COURT_CHARGE_ID = 11L
private const val NOMIS_COURT_CHARGE_2_ID = 12L
private const val NOMIS_COURT_CHARGE_3_ID = 13L
private const val NOMIS_COURT_CHARGE_4_ID = 14L
private const val DPS_COURT_CHARGE_ID = "8576aa44-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_COURT_CHARGE_2_ID = "4576aa44-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_COURT_CHARGE_3_ID = "2376aa44-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_COURT_CHARGE_4_ID = "2176aa44-642a-484a-a967-2d17b5c9c5a1"
private const val OFFENDER_NO = "AB12345"
private const val BOOKING_ID = 123456L
private const val PRISON_MDI = "MDI"
private const val PRISON_LEI = "LEI"
private const val CASE_REFERENCE = "ABC4999"
private const val OFFENCE_CODE_1 = "TR11017"
private const val OFFENCE_CODE_2 = "PR52028A"
private const val OFFENCE_CODE_3 = "VV52028A"
private const val OFFENCE_CODE_4 = "AA52028A"
private const val OUTCOME_1 = "4001"
private const val OUTCOME_2 = "3001"
private const val YEARS = 6
private const val MONTHS = 5
private const val WEEKS = 4
private const val DAYS = 3
private const val DPS_PERIOD_LENGTH_ID = "87591b18-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_PERIOD_LENGTH_2_ID = "11591b18-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_PERIOD_LENGTH_3_ID = "21591b18-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_SENTENCE_ID = "1c591b18-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_SENTENCE_2_ID = "2c591b18-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_SENTENCE_3_ID = "3c591b18-642a-484a-a967-2d17b5c9c5a1"
private const val DPS_SENTENCE_4_ID = "9a591b18-642a-484a-a967-2d17b5c9c5a1"
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
  CourtSentencingMappingService::class,
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
          service.checkCase(nomisCaseId = NOMIS_COURT_CASE_ID, dpsCaseId = DPS_COURT_CASE_ID, offenderNo = OFFENDER_NO),
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
          offenderNo = OFFENDER_NO,
        )?.differences,
      ).isEqualTo(listOf(Difference(property = "case.active", dps = true, nomis = false, id = DPS_COURT_CASE_ID)))
    }

    @Test
    fun `will not report a case status mismatch when DPS case is a DUPLICATE`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse().copy(caseStatus = CodeDescription("A", "Active")),
        dpsCase = dpsCourtCaseResponse().copy(active = false, status = ReconciliationCourtCase.Status.DUPLICATE),
      )

      assertThat(
        service.checkCase(
          nomisCaseId = NOMIS_COURT_CASE_ID,
          dpsCaseId = DPS_COURT_CASE_ID,
          offenderNo = OFFENDER_NO,
        )?.differences,
      ).isNull()
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
          offenderNo = OFFENDER_NO,
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
          offenderNo = OFFENDER_NO,
        )?.differences,
      ).isEqualTo(listOf(Difference(property = "case.appearances", dps = 1, nomis = 2)))
    }

    @Test
    fun `will cope with appearances received in different order from nomis and dps`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse().copy(
          courtEvents = listOf(
            nomisAppearanceResponseWithoutCharges().copy(
              eventDateTime = LocalDateTime.of(2024, 2, 2, 4, 0, 0),
              outcomeReasonCode = nomisOffenceResult(OUTCOME_2),
              id = NOMIS_COURT_APPEARANCE_2_ID,
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
              appearanceUuid = UUID.fromString(DPS_COURT_APPEARANCE_2_ID),
            ),
          ),
        ),
      )
      assertThat(
        service.checkCase(
          nomisCaseId = NOMIS_COURT_CASE_ID,
          dpsCaseId = DPS_COURT_CASE_ID,
          offenderNo = OFFENDER_NO,
        ),
      ).isNull()
    }

    @Test
    fun `will reorder appearances correctly for comparison when charge outcome is the only difference`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse().copy(
          courtEvents = listOf(
            nomisAppearanceResponse().copy(
              courtEventCharges = listOf(nomisChargeResponse().copy(resultCode1 = nomisOffenceResult(OUTCOME_2))),
            ),
            nomisAppearanceResponse(id = NOMIS_COURT_APPEARANCE_2_ID),
          ),
        ),
        dpsCase = dpsCourtCaseResponse().copy(
          appearances = listOf(
            dpsAppearanceResponse(
              appearanceUuid = UUID.fromString(DPS_COURT_APPEARANCE_2_ID),
              charges = listOf(dpsChargeResponse(sentenceResponse = null)),
            ),
            dpsAppearanceResponse().copy(
              charges = listOf(dpsChargeResponse().copy(nomisOutcomeCode = OUTCOME_2)),
            ),
          ),
        ),
      )
      assertThat(
        service.checkCase(
          nomisCaseId = NOMIS_COURT_CASE_ID,
          dpsCaseId = DPS_COURT_CASE_ID,
          offenderNo = OFFENDER_NO,
        ),
      ).isNull()
    }

    @Test
    fun `will reorder appearances correctly for comparison when charge offence end date is the only difference`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse().copy(
          courtEvents = listOf(
            nomisAppearanceResponse().copy(
              courtEventCharges = listOf(nomisChargeResponse().copy(offenceEndDate = null)),
            ),
            nomisAppearanceResponse(id = NOMIS_COURT_APPEARANCE_2_ID),
          ),
        ),
        dpsCase = dpsCourtCaseResponse().copy(
          appearances = listOf(
            dpsAppearanceResponse(
              appearanceUuid = UUID.fromString(DPS_COURT_APPEARANCE_2_ID),
              charges = listOf(dpsChargeResponse(sentenceResponse = null)),
            ),
            dpsAppearanceResponse().copy(
              charges = listOf(dpsChargeResponse().copy(offenceEndDate = null)),
            ),
          ),
        ),
      )
      assertThat(
        service.checkCase(
          nomisCaseId = NOMIS_COURT_CASE_ID,
          dpsCaseId = DPS_COURT_CASE_ID,
          offenderNo = OFFENDER_NO,
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
          offenderNo = OFFENDER_NO,
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
    fun `will handle dodgy appearance dates`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse().copy(
          courtEvents = listOf(
            nomisAppearanceResponse().copy(
              eventDateTime = LocalDateTime.of(9, 1, 1, 10, 10, 0),
            ),
          ),
        ),
        dpsCase = dpsCourtCaseResponse().copy(
          appearances = listOf(
            dpsAppearanceResponse(
              appearanceDate = LocalDate.of(10, 1, 1),
              charges = listOf(
                dpsChargeResponse(
                  sentenceResponse = reconciliationSentence().copy(
                    sentenceStartDate = LocalDate.of(10, 1, 1),
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
          offenderNo = OFFENDER_NO,
        ),
      ).isNull()
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
          offenderNo = OFFENDER_NO,
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
              ),
            ),
          ),
        ),
        dpsCase = dpsCourtCaseResponse().copy(
          appearances = listOf(
            dpsAppearanceResponse().copy(
              charges = listOf(
                dpsChargeResponse().copy(
                  offenceStartDate = LocalDate.of(2021, 3, 3),
                  chargeUuid = UUID.fromString(DPS_COURT_CHARGE_ID),
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
          offenderNo = OFFENDER_NO,
        )?.differences,
      ).isEqualTo(
        listOf(
          Difference(
            property = "case.appearances[0].charges[0].offenceDate",
            dps = "2021-03-03",
            nomis = "2022-03-03",
            id = DPS_COURT_CHARGE_ID,
          ),
        ),
      )
    }

    @Test
    fun `will ignore bad offence date data`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse().copy(
          courtEvents = listOf(
            nomisAppearanceResponse().copy(
              courtEventCharges = listOf(
                nomisChargeResponse().copy(offenceDate = LocalDate.of(1900, 3, 3)),
              ),
            ),
          ),
        ),
        dpsCase = dpsCourtCaseResponse().copy(
          appearances = listOf(
            dpsAppearanceResponse().copy(
              charges = listOf(
                dpsChargeResponse().copy(offenceStartDate = LocalDate.of(1900, 4, 3)),
              ),
            ),
          ),
        ),
      )
      assertThat(
        service.checkCase(
          nomisCaseId = NOMIS_COURT_CASE_ID,
          dpsCaseId = DPS_COURT_CASE_ID,
          offenderNo = OFFENDER_NO,
        )?.differences,
      ).isNull()
    }

    @Test
    fun `will compare offence date data when in valid date range`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse().copy(
          courtEvents = listOf(
            nomisAppearanceResponse().copy(
              courtEventCharges = listOf(
                nomisChargeResponse().copy(offenceDate = LocalDate.of(1921, 3, 3)),
              ),
            ),
          ),
        ),
        dpsCase = dpsCourtCaseResponse().copy(
          appearances = listOf(
            dpsAppearanceResponse().copy(
              charges = listOf(
                dpsChargeResponse().copy(
                  offenceStartDate = LocalDate.of(1921, 4, 3),
                  chargeUuid = UUID.fromString(DPS_COURT_CHARGE_ID),
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
          offenderNo = OFFENDER_NO,
        )?.differences,
      ).isEqualTo(
        listOf(
          Difference(
            property = "case.appearances[0].charges[0].offenceDate",
            dps = "1921-04-03",
            nomis = "1921-03-03",
            id = DPS_COURT_CHARGE_ID,
          ),
        ),
      )
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
          sentences = listOf(
            nomisSentenceResponse(),
            nomisSentenceResponse(charges = listOf(nomisOffenderChargeResponse(offenderChargeId = NOMIS_COURT_CHARGE_2_ID))),
          ),
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
          offenderNo = OFFENDER_NO,
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
          sentences = listOf(
            nomisSentenceResponse(
              charges = listOf(
                nomisOffenderChargeResponse(),
                nomisOffenderChargeResponse(offenderChargeId = NOMIS_COURT_CHARGE_2_ID, offenceCode = OFFENCE_CODE_2),
                nomisOffenderChargeResponse(offenderChargeId = NOMIS_COURT_CHARGE_3_ID),
              ),
            ),
          ),
        ),
        dpsCase = dpsCourtCaseResponse().copy(
          appearances = listOf(
            dpsAppearanceResponse().copy(
              // will create 3 sentences with the same id (the same sentence)
              charges = listOf(
                dpsChargeResponse(
                  sentenceResponse = dpsSentenceResponse().copy(
                    sentenceUuid = UUID.fromString(
                      DPS_SENTENCE_ID,
                    ),
                  ),
                ),
                dpsChargeResponse(
                  sentenceResponse = dpsSentenceResponse().copy(
                    sentenceUuid = UUID.fromString(
                      DPS_SENTENCE_ID,
                    ),
                  ),
                  offenceCode = OFFENCE_CODE_2,
                ),
                dpsChargeResponse(
                  sentenceResponse = dpsSentenceResponse().copy(
                    sentenceUuid = UUID.fromString(
                      DPS_SENTENCE_ID,
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
          offenderNo = OFFENDER_NO,
        ),
      ).isNull()
    }

    @Test
    fun `will detect sentence differences`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse().copy(
          courtEvents = listOf(
            nomisAppearanceResponse().copy(
              courtEventCharges = listOf(
                nomisChargeResponse(offenceCode = OFFENCE_CODE_1),
                nomisChargeResponse(offenderChargeId = NOMIS_COURT_CHARGE_2_ID, offenceCode = OFFENCE_CODE_2),
                nomisChargeResponse(offenderChargeId = NOMIS_COURT_CHARGE_3_ID, offenceCode = OFFENCE_CODE_3),
                nomisChargeResponse(offenderChargeId = NOMIS_COURT_CHARGE_4_ID, offenceCode = OFFENCE_CODE_4),
              ),
            ),
          ),
          sentences = listOf(
            nomisSentenceResponse(),
            nomisSentenceResponse(
              charges = listOf(
                nomisOffenderChargeResponse(
                  offenceCode = OFFENCE_CODE_2,
                  offenderChargeId = NOMIS_COURT_CHARGE_2_ID,
                ),
              ),
            ),
            // will reorder on offence code when comparing
            nomisSentenceResponse(
              charges = listOf(
                nomisOffenderChargeResponse(
                  offenceCode = OFFENCE_CODE_4,
                  offenderChargeId = NOMIS_COURT_CHARGE_3_ID,
                ),
              ),
            ),
            nomisSentenceResponse(
              charges = listOf(
                nomisOffenderChargeResponse(
                  offenceCode = OFFENCE_CODE_3,
                  offenderChargeId = NOMIS_COURT_CHARGE_4_ID,
                ),
              ),
            ),
          ),
        ),
        dpsCase = dpsCourtCaseResponse().copy(
          appearances = listOf(
            dpsAppearanceResponse().copy(
              charges = listOf(
                dpsChargeResponse(),
                dpsChargeResponse(offenceCode = OFFENCE_CODE_2).copy(
                  chargeUuid = UUID.fromString(DPS_COURT_CHARGE_2_ID),
                  sentence = dpsSentenceResponse().copy(
                    sentenceUuid = UUID.fromString(DPS_SENTENCE_2_ID),
                  ),
                ),
                dpsChargeResponse(offenceCode = OFFENCE_CODE_3).copy(
                  chargeUuid = UUID.fromString(DPS_COURT_CHARGE_3_ID),
                  sentence = dpsSentenceResponse().copy(
                    sentenceUuid = UUID.fromString(DPS_SENTENCE_3_ID),
                  ),
                ),
                dpsChargeResponse(offenceCode = OFFENCE_CODE_4).copy(
                  chargeUuid = UUID.fromString(DPS_COURT_CHARGE_4_ID),
                  sentence = dpsSentenceResponse().copy(
                    sentenceUuid = UUID.fromString(DPS_SENTENCE_4_ID),
                    sentenceCategory = "New Category",
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
          offenderNo = OFFENDER_NO,
        )?.differences,
      ).isEqualTo(
        listOf(
          Difference(
            property = "case.sentences[0].sentenceCategory",
            dps = "New Category",
            nomis = "2020",
            id = DPS_SENTENCE_4_ID,
          ),
        ),
      )
    }

    @Test
    fun `will check DPS legacy sentence data for UNKNOWN recall sentences`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse().copy(
          sentences = listOf(
            nomisSentenceResponse().copy(
              calculationType = CodeDescription(code = "LR_ORA", description = "ORA Licence Recall"),
              category = CodeDescription(code = "2020", description = "2020"),
            ),
          ),
        ),
        dpsCase = dpsCourtCaseResponse().copy(
          appearances = listOf(
            dpsAppearanceResponse().copy(
              charges = listOf(
                dpsChargeResponse().copy(
                  sentence = dpsSentenceResponse().copy(
                    sentenceCalcType = "UNKNOWN",
                    sentenceCategory = "UNKNOWN",
                    legacyData = SentenceLegacyData(
                      sentenceCalcType = "LR_ORA",
                      sentenceCategory = "2020",
                      postedDate = "2025/01/01",
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
          offenderNo = OFFENDER_NO,
        ),
      ).isNull()
    }

    @Test
    fun `will detect invalid eventId on court order when processing nomis sentence`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse().copy(
          sentences = listOf(
            nomisSentenceResponse(eventId = 123),
          ),
        ),
        dpsCase = dpsCourtCaseResponse().copy(
          appearances = listOf(
            dpsAppearanceResponse().copy(
              charges = listOf(
                dpsChargeResponse().copy(
                  sentence = null,
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
          offenderNo = OFFENDER_NO,
        )?.differences,
      ).isEqualTo(
        listOf(
          Difference(
            property = "case.sentences",
            dps = 0,
            nomis = 1,
            id = null,
          ),
        ),
      )
    }

    @Test
    fun `will compare fines correctly`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse().copy(
          sentences = listOf(
            nomisSentenceResponse().copy(
              fineAmount = BigDecimal("8.10"),
            ),
          ),
        ),
        dpsCase = dpsCourtCaseResponse().copy(
          appearances = listOf(
            dpsAppearanceResponse().copy(
              charges = listOf(
                dpsChargeResponse().copy(
                  sentence = dpsSentenceResponse().copy(fineAmount = BigDecimal("8.1")),
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
          offenderNo = OFFENDER_NO,
        ),
      ).isNull()
    }

    @Test
    fun `will order sentences using terms when only difference is the terms`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse().copy(
          courtEvents = listOf(
            nomisAppearanceResponse().copy(
              courtEventCharges = listOf(nomisChargeResponse(), nomisChargeResponse()),
            ),
          ),
          sentences = listOf(
            nomisSentenceResponse().copy(
              sentenceSeq = 2,
              sentenceTerms = listOf(
                nomisSentenceTermResponse().copy(termSequence = 9),
                nomisSentenceTermResponse().copy(termSequence = 8),
              ),
            ),
            nomisSentenceResponse().copy(offenderCharges = listOf(nomisOffenderChargeResponse(offenderChargeId = NOMIS_COURT_CHARGE_2_ID))),
          ),
        ),
        dpsCase = dpsCourtCaseResponse().copy(
          appearances = listOf(
            dpsAppearanceResponse().copy(
              charges = listOf(
                dpsChargeResponse(),
                dpsChargeResponse().copy(
                  sentence = dpsSentenceResponse().copy(
                    sentenceUuid = UUID.fromString(DPS_SENTENCE_2_ID),
                    periodLengths = listOf(
                      dpsPeriodLengthResponse().copy(periodLengthUuid = UUID.fromString(DPS_PERIOD_LENGTH_2_ID)),
                      dpsPeriodLengthResponse().copy(periodLengthUuid = UUID.fromString(DPS_PERIOD_LENGTH_3_ID)),
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
          offenderNo = OFFENDER_NO,
        ),
      ).isNull()
    }

    @Test
    fun `will reorder on fines when only difference`() = runTest {
      stubCase(
        nomisCase = nomisCaseResponse().copy(
          courtEvents = listOf(
            nomisAppearanceResponse().copy(
              courtEventCharges = listOf(nomisChargeResponse(), nomisChargeResponse()),
            ),
          ),
          sentences = listOf(
            nomisSentenceResponse().copy(
              fineAmount = BigDecimal("8.10"),
            ),
            nomisSentenceResponse().copy(
              fineAmount = BigDecimal("2.3"),
              offenderCharges = listOf(nomisOffenderChargeResponse(offenderChargeId = NOMIS_COURT_CHARGE_2_ID)),
            ),
          ),
        ),
        dpsCase = dpsCourtCaseResponse().copy(
          appearances = listOf(
            dpsAppearanceResponse().copy(
              charges = listOf(
                dpsChargeResponse().copy(
                  sentence = dpsSentenceResponse().copy(fineAmount = BigDecimal("2.3")),
                ),
                dpsChargeResponse().copy(
                  sentence = dpsSentenceResponse().copy(
                    sentenceUuid = UUID.fromString(DPS_SENTENCE_2_ID),
                    fineAmount = BigDecimal("8.1"),
                  ),
                  chargeUuid = UUID.fromString(DPS_COURT_CHARGE_2_ID),
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
          offenderNo = OFFENDER_NO,
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
          offenderNo = OFFENDER_NO,
        )?.differences,
      ).isEqualTo(listOf(Difference(property = "case.sentences[0].terms", dps = 2, nomis = 3)))
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
                        periodLengthUuid = UUID.fromString(DPS_PERIOD_LENGTH_ID),
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
          offenderNo = OFFENDER_NO,
        )?.differences,
      ).isEqualTo(
        listOf(
          Difference(
            property = "case.sentences[0].terms[2].years",
            dps = 20,
            nomis = 6,
            id = DPS_PERIOD_LENGTH_ID,
          ),
        ),
      )
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
) = reconciliationCourtCase(
  active = active,
  appearances = appearances,
  caseReferences = caseReferences,
)

fun dpsAppearanceResponse(
  appearanceUuid: UUID = UUID.fromString(DPS_COURT_APPEARANCE_ID),
  outcome: String = OUTCOME_1,
  charges: List<ReconciliationCharge> = listOf(dpsChargeResponse()),
  appearanceDate: LocalDate = LocalDate.of(2024, 1, 1),
) = reconciliationCourtAppearance(
  appearanceUuid = appearanceUuid,
  appearanceDate = appearanceDate,
  outcome = outcome,
  charges = charges,
)

fun dpsAppearanceResponseWithoutCharges() = dpsAppearanceResponse(charges = emptyList())

fun nomisCaseResponse(
  id: Long = NOMIS_COURT_CASE_ID,
  beginDate: LocalDate = LocalDate.of(2024, 1, 1),
  events: List<CourtEventResponse> = listOf(nomisAppearanceResponse()),
  sentences: List<SentenceResponse> = listOf(nomisSentenceResponse()),
  offenderNo: String = OFFENDER_NO,
  bookingId: Long = BOOKING_ID,
) = CourtCaseResponse(
  id = id,
  offenderNo = offenderNo,
  courtEvents = events,
  courtId = PRISON_LEI,
  createdDateTime = LocalDateTime.now(),
  createdByUsername = "Q1251T",
  caseStatus = CodeDescription("A", "Active"),
  legalCaseType = CodeDescription("CRT", "Court Appearance"),
  beginDate = beginDate,
  bookingId = bookingId,
  offenderCharges = emptyList(),
  caseSequence = 1,
  caseInfoNumbers = emptyList(),
  sentences = sentences,
  sourceCombinedCaseIds = emptyList(),
)

fun nomisAppearanceResponse(
  id: Long = NOMIS_COURT_APPEARANCE_ID,
  outcomeCode: String? = OUTCOME_1,
  caseId: Long = NOMIS_COURT_CASE_ID,
  eventDateTime: LocalDateTime = LocalDateTime.of(2024, 1, 1, 10, 10, 0),
  nextEventDateTime: LocalDateTime = LocalDateTime.of(2024, 2, 1, 10, 10, 0),
  charges: List<CourtEventChargeResponse> = listOf(nomisChargeResponse()),
) = CourtEventResponse(
  id = id,
  offenderNo = OFFENDER_NO,
  caseId = caseId,
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
    createdByUsername = "msmith",
  ),
  createdByUsername = "msmith",
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
  createdByUsername = "msmith",
)

fun nomisSentenceResponse(
  eventId: Long = NOMIS_COURT_APPEARANCE_ID,
  terms: List<SentenceTermResponse> = listOf(nomisSentenceTermResponse()),
  charges: List<OffenderChargeResponse> = listOf(nomisOffenderChargeResponse()),
  sentenceSeq: Long = NOMIS_SENTENCE_SEQ,
) = SentenceResponse(
  sentenceSeq = sentenceSeq,
  bookingId = NOMIS_BOOKING_ID,
  category = CodeDescription(SENTENCE_CATEGORY, "desc"),
  calculationType = CodeDescription(SENTENCE_CALC_TYPE, "desc"),
  startDate = LocalDate.of(2023, 1, 1),
  status = "A",
  sentenceTerms = terms,
  fineAmount = BigDecimal.valueOf(750),
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
  createdByUsername = "msmith",
)

fun dpsChargeResponse(
  offenceCode: String = OFFENCE_CODE_1,
  offenceStartDate: LocalDate = LocalDate.of(2023, 1, 1),
  sentenceResponse: ReconciliationSentence? = dpsSentenceResponse(),
) = reconciliationCharge(
  offenceCode = offenceCode,
  offenceStartDate = offenceStartDate,
  sentenceResponse = sentenceResponse,
)

fun dpsChargeResponseWithoutSentence() = dpsChargeResponse(sentenceResponse = null)

fun dpsSentenceResponse(periodLengths: List<ReconciliationPeriodLength> = listOf(dpsPeriodLengthResponse())) = reconciliationSentence(
  periodLengths = periodLengths,
)

fun dpsPeriodLengthResponse() = reconciliationPeriodLength()
