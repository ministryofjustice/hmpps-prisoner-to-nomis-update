package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ReconciliationPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ReconciliationResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.generateReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.collections.map

@Service
class CourtSentencingReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val dpsApiService: CourtSentencingApiService,
  private val nomisApiService: CourtSentencingNomisApiService,
  private val nomisPrisonerApiService: NomisApiService,
  private val mappingService: CourtCaseMappingService,
  private val objectMapper: ObjectMapper,
  @Value("\${reports.court-case.prisoner.reconciliation.page-size:10}") private val prisonerPageSize: Int = 10,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TELEMETRY_COURT_CASE_PRISONER_PREFIX = "court-case-prisoner-reconciliation"
  }

  suspend fun generatePrisonerCourtCasesReconciliationReport(): ReconciliationResult<MismatchPrisonerCasesResponse> = generateReconciliationReport(
    threadCount = prisonerPageSize,
    checkMatch = ::checkPrisonerCasesMatch,
    nextPage = ::getNextBookingsForPage,
  )

  suspend fun manualCheckCaseDps(offenderNo: String, dpsCaseId: String): MismatchCaseResponse = mappingService.getMappingGivenCourtCaseId(dpsCourtCaseId = dpsCaseId).let {
    MismatchCaseResponse(
      offenderNo = offenderNo,
      nomisCaseId = it.nomisCourtCaseId,
      dpsCaseId = dpsCaseId,
      mismatch = checkCase(dpsCaseId = it.dpsCourtCaseId, nomisCaseId = it.nomisCourtCaseId),
    )
  }

  suspend fun manualCheckCaseNomis(offenderNo: String, nomisCaseId: Long): MismatchCaseResponse = mappingService.getMappingGivenNomisCourtCaseId(nomisCourtCaseId = nomisCaseId).let {
    MismatchCaseResponse(
      offenderNo = offenderNo,
      nomisCaseId = nomisCaseId,
      dpsCaseId = it.dpsCourtCaseId,
      mismatch = checkCase(dpsCaseId = it.dpsCourtCaseId, nomisCaseId = it.nomisCourtCaseId),
    )
  }

  suspend fun checkCasesNomis(offenderNo: String, nomisCaseIds: List<Long>): List<MismatchCaseResponse> = mappingService.getMappingsGivenNomisCourtCaseIds(nomisCourtCaseIds = nomisCaseIds).map {
    MismatchCaseResponse(
      offenderNo = offenderNo,
      nomisCaseId = it.nomisCourtCaseId,
      dpsCaseId = it.dpsCourtCaseId,
      mismatch = checkCase(dpsCaseId = it.dpsCourtCaseId, nomisCaseId = it.nomisCourtCaseId),
    )
  }

  suspend fun checkPrisonerCasesMatch(prisonerId: PrisonerIds): MismatchPrisonerCasesResponse? = runCatching {
    manualCheckCaseOffenderNo(prisonerId.offenderNo)
      .filter { it.mismatch != null }
      .takeIf { it.isNotEmpty() }?.let {
        MismatchPrisonerCasesResponse(
          offenderNo = prisonerId.offenderNo,
          mismatches = it,
        )
      }?.also {
        it.mismatches.forEach { mismatch ->
          telemetryClient.trackEvent(
            "$TELEMETRY_COURT_CASE_PRISONER_PREFIX-mismatch",
            mapOf(
              "offenderNo" to it.offenderNo,
              "dpsCaseId" to mismatch.dpsCaseId,
              "nomisCaseId" to mismatch.nomisCaseId.toString(),
              "mismatchCount" to mismatch.mismatch!!.differences.size.toString(),
            ),
            null,
          )
        }
      }
  }.onFailure {
    log.error("Unable to match prisoner: ${prisonerId.offenderNo}", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_COURT_CASE_PRISONER_PREFIX-error",
      mapOf(
        "offenderNo" to prisonerId.offenderNo,
        "reason" to (it.message ?: "unknown"),
      ),
      null,
    )
  }.getOrNull()

  suspend fun manualCheckCaseOffenderNo(offenderNo: String): List<MismatchCaseResponse> = checkCasesNomis(offenderNo = offenderNo, nomisCaseIds = nomisApiService.getCourtCaseIdsByOffender(offenderNo))

  suspend fun manualCheckCaseOffenderNoList(offenderNoList: List<String>): List<List<MismatchCaseResponse>> = offenderNoList.map {
    checkCasesNomis(offenderNo = it, nomisCaseIds = nomisApiService.getCourtCaseIdsByOffender(it))
  }.also {
    log.info(it.toString())
  }

  private suspend fun getNextBookingsForPage(lastBookingId: Long): ReconciliationPageResult<PrisonerIds> = runCatching {
    nomisPrisonerApiService.getAllLatestBookings(
      lastBookingId = lastBookingId,
      activeOnly = false,
      pageSize = prisonerPageSize,
    )
  }.onFailure {
    telemetryClient.trackEvent(
      "$TELEMETRY_COURT_CASE_PRISONER_PREFIX-mismatch-page-error",
      mapOf(
        "booking" to lastBookingId.toString(),
      ),
    )
    log.error("Unable to match entire page of bookings from booking: $lastBookingId", it)
  }
    .map {
      ReconciliationSuccessPageResult(
        ids = it.prisonerIds,
        last = it.lastBookingId,
      )
    }
    .getOrElse { ReconciliationErrorPageResult(it) }
    .also { log.info("Page requested from booking: $lastBookingId, with $prisonerPageSize bookings") }

  suspend fun manualCheckCase(offenderNo: String, nomisCaseId: Long, dpsCaseId: String): MismatchCaseResponse = MismatchCaseResponse(
    offenderNo = offenderNo,
    nomisCaseId = nomisCaseId,
    dpsCaseId = dpsCaseId,
    mismatch = checkCase(dpsCaseId = dpsCaseId, nomisCaseId = nomisCaseId),
  )

  suspend fun checkCase(dpsCaseId: String, nomisCaseId: Long): MismatchCase? = runCatching {
    val (nomisResponse, dpsResponse) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getCourtCaseForReconciliation(nomisCaseId) } to
        async { dpsApiService.getCourtCaseForReconciliation(dpsCaseId) }
    }.awaitBoth()

    // DPS hierarchy view of sentencing means that sentences can be repeated when associated with multiple charges
    val appearanceCharges = dpsResponse.appearances.flatMap { appearance -> appearance.charges }
    val sentences = appearanceCharges.mapNotNull { charge -> charge.sentence }.map {
      val offenceCodeString =
        appearanceCharges.filter { charge -> charge.sentence?.sentenceUuid == it.sentenceUuid }.map { charge ->
          charge.offenceCode
        }.sorted().joinToString(",")
      val terms = it.periodLengths.map { term ->
        SentenceTermFields(
          sentenceTermCode = term.sentenceTermCode,
          lifeSentenceFlag = term.lifeSentence,
          years = term.periodYears,
          months = term.periodMonths,
          weeks = term.periodWeeks,
          days = term.periodDays,
          id = term.periodLengthUuid.toString(),
        )
      }
      SentenceFields(
        // sentence start date is actually the sentence appearance date which can be different to the start date eg consecutive sentences
        sentencingAppearanceDate = it.sentenceStartDate,
        sentenceCategory = it.sentenceCategory,
        sentenceCalcType = it.sentenceCalcType,
        fine = it.fineAmount?.stripTrailingZeros(),
        status = if (it.active) {
          "A"
        } else {
          "I"
        },
        id = it.sentenceUuid.toString(),
        offenceCodes = offenceCodeString,
        terms = terms,
        // this can't have the id in it for sorting
        termsAsString = sortTerms(terms).map { term -> term.toSortString() }.toString(),
      )
    }.distinctBy { it.id }

    val dpsFields = CaseFields(
      active = dpsResponse.active,
      id = dpsCaseId,
      appearances = dpsResponse.appearances.map { appearanceResponse ->
        val charges = appearanceResponse.charges.map { appearanceChargeResponse ->
          ChargeFields(
            offenceCode = appearanceChargeResponse.offenceCode,
            offenceDate = appearanceChargeResponse.offenceStartDate,
            offenceEndDate = appearanceChargeResponse.offenceEndDate,
            outcome = appearanceChargeResponse.nomisOutcomeCode,
            id = appearanceChargeResponse.chargeUuid.toString(),
          )
        }
        AppearanceFields(
          date = appearanceResponse.appearanceDate,
          court = appearanceResponse.courtCode,
          outcome = appearanceResponse.nomisOutcomeCode,
          id = appearanceResponse.appearanceUuid.toString(),
          charges = charges,
          chargeAsString = sortCharges(charges).map { it.toSortString() }.toString(),
        )
      },
      sentences = sentences,
      caseReferences = dpsResponse.courtCaseLegacyData?.caseReferences?.map { it.offenderCaseReference } ?: emptyList(),
    )
    val nomisFields = CaseFields(
      active = nomisResponse.caseStatus.code == "A",
      id = nomisResponse.id.toString(),
      appearances = nomisResponse.courtEvents.map { eventResponse ->
        val charges = eventResponse.courtEventCharges.map { chargeResponse ->
          ChargeFields(
            offenceCode = chargeResponse.offenderCharge.offence.offenceCode,
            offenceDate = chargeResponse.offenceDate,
            offenceEndDate = chargeResponse.offenceEndDate,
            outcome = chargeResponse.resultCode1?.code,
            id = chargeResponse.offenderCharge.id.toString(),
          )
        }
        AppearanceFields(
          date = eventResponse.eventDateTime.toLocalDate(),
          court = eventResponse.courtId,
          outcome = eventResponse.outcomeReasonCode?.code,
          id = eventResponse.id.toString(),
          charges = charges,
          chargeAsString = sortCharges(charges).map { it.toSortString() }.toString(),
        )
      },
      // TODO remove this temp fix for nomis sentences without any charges (bad data)
      // TODO this includes a second work around for duplicate sentences (same offenderCharge record)
      sentences = nomisResponse.sentences.filter { it.offenderCharges.isNotEmpty() }.distinctBy { it.offenderCharges.first().id }.map { sentenceResponse ->
        val offenderCodeString =
          sentenceResponse.offenderCharges.map { chargeResponse -> chargeResponse.offence.offenceCode }.sorted()
            .joinToString(",")
        val terms = sentenceResponse.sentenceTerms.map { term ->
          SentenceTermFields(
            years = term.years,
            months = term.months,
            weeks = term.weeks,
            days = term.days,
            sentenceTermCode = term.sentenceTermType?.code,
            lifeSentenceFlag = term.lifeSentenceFlag,
            id = term.termSequence.toString(),
          )
        }
        SentenceFields(
          sentencingAppearanceDate = nomisResponse.courtEvents.find { appearance -> appearance.id == sentenceResponse.courtOrder?.eventId }?.eventDateTime!!.toLocalDate(),
          sentenceCategory = sentenceResponse.category.code,
          sentenceCalcType = sentenceResponse.calculationType.code,
          fine = sentenceResponse.fineAmount?.stripTrailingZeros(),
          status = sentenceResponse.status,
          id = sentenceResponse.sentenceSeq.toString(),
          offenceCodes = offenderCodeString,
          terms = terms,
          termsAsString = sortTerms(terms).map { it.toSortString() }.toString(),
        )
      },
      caseReferences = nomisResponse.caseInfoNumbers.map { it.reference },
    )

    val differenceList = compareObjects(dpsFields, nomisFields)

    return if (differenceList.isNotEmpty()) {
      // log.info("Differences: ${objectMapper.writeValueAsString(differenceList)}")
      MismatchCase(
        nomisCase = nomisFields,
        dpsCase = dpsFields,
        differences = differenceList,
      )
    } else {
      null
    }
  }.onFailure {
    log.error("Unable to match case with ids: dps:$dpsCaseId and nomis:$nomisCaseId", it)
  }.getOrNull()

  fun compareObjects(dpsObj: Any?, nomisObj: Any?, parentProperty: String = "case"): List<Difference> {
    if (dpsObj == null && nomisObj == null) return emptyList()
    if (dpsObj == null || nomisObj == null) return listOf(Difference(parentProperty, dpsObj, nomisObj))
    if (dpsObj::class != nomisObj::class) return listOf(Difference(parentProperty, dpsObj, nomisObj))

    val differences = mutableListOf<Difference>()

    when (dpsObj) {
      is CaseFields -> {
        if (dpsObj.active != (nomisObj as CaseFields).active) {
          differences.add(Difference("$parentProperty.active", dpsObj.active, nomisObj.active, id = dpsObj.id))
        }
        val sortedDpsAppearances = dpsObj.appearances.sortedWith(
          compareBy<AppearanceFields> { it.date }
            .thenBy { it.court }
            .thenBy { it.outcome }
            .thenBy { it.charges.size }
            .thenBy { it.chargeAsString },
        )

        val sortedNomisAppearances = nomisObj.appearances.sortedWith(
          compareBy<AppearanceFields> { it.date }
            .thenBy { it.court }
            .thenBy { it.outcome }
            .thenBy { it.charges.size }
            .thenBy { it.chargeAsString },
        )

        if (!dpsObj.caseReferences.containsAll(nomisObj.caseReferences)) {
          differences.add(
            Difference(
              "$parentProperty.caseReferences",
              dpsObj.caseReferences,
              nomisObj.caseReferences,
              id = dpsObj.id,
            ),
          )
        }

        differences.addAll(compareLists(sortedDpsAppearances, sortedNomisAppearances, "$parentProperty.appearances"))

        val sortedDpsSentences = dpsObj.sentences.sortedWith(
          compareBy<SentenceFields> { it.sentencingAppearanceDate }
            .thenBy { it.offenceCodes }
            .thenBy { it.sentenceCategory }
            .thenBy { it.sentenceCalcType }
            .thenBy { it.status }
            .thenBy { it.termsAsString }
            .thenBy { it.fine },
        )

        val sortedNomisSentences = nomisObj.sentences.sortedWith(
          compareBy<SentenceFields> { it.sentencingAppearanceDate }
            .thenBy { it.offenceCodes }
            .thenBy { it.sentenceCategory }
            .thenBy { it.sentenceCalcType }
            .thenBy { it.status }
            .thenBy { it.termsAsString }
            .thenBy { it.fine },

        )

        differences.addAll(compareLists(sortedDpsSentences, sortedNomisSentences, "$parentProperty.sentences"))
      }

      is AppearanceFields -> {
        if (dpsObj.date != (nomisObj as AppearanceFields).date) {
          differences.add(Difference("$parentProperty.date", dpsObj.date, nomisObj.date, dpsObj.id))
        }
        if (dpsObj.court != nomisObj.court) {
          differences.add(Difference("$parentProperty.court", dpsObj.court, nomisObj.court, dpsObj.id))
        }
        if (dpsObj.outcome != nomisObj.outcome) {
          differences.add(Difference("$parentProperty.outcome", dpsObj.outcome, nomisObj.outcome, dpsObj.id))
        }
        differences.addAll(
          compareLists(
            sortCharges(dpsObj.charges),
            sortCharges(nomisObj.charges),
            "$parentProperty.charges",
          ),
        )
      }

      is ChargeFields -> {
        if (dpsObj.offenceCode != (nomisObj as ChargeFields).offenceCode) {
          differences.add(
            Difference(
              "$parentProperty.offenceCode",
              dpsObj.offenceCode,
              nomisObj.offenceCode,
              dpsObj.id,
            ),
          )
        }
        if (dpsObj.offenceDate != nomisObj.offenceDate) {
          differences.add(
            Difference(
              "$parentProperty.offenceDate",
              dpsObj.offenceDate?.toString(),
              nomisObj.offenceDate?.toString(),
              dpsObj.id,
            ),
          )
        }

        if (dpsObj.offenceEndDate != nomisObj.offenceEndDate) {
          differences.add(
            Difference(
              "$parentProperty.offenceEndDate",
              dpsObj.offenceEndDate?.toString(),
              nomisObj.offenceEndDate?.toString(),
              dpsObj.id,
            ),
          )
        }
        if (dpsObj.outcome != nomisObj.outcome) {
          differences.add(Difference("$parentProperty.outcome", dpsObj.outcome, nomisObj.outcome, dpsObj.id))
        }
      }

      is SentenceFields -> {
        if (dpsObj.sentenceCategory != (nomisObj as SentenceFields).sentenceCategory) {
          differences.add(
            Difference(
              "$parentProperty.sentenceCategory",
              dpsObj.sentenceCategory,
              nomisObj.sentenceCategory,
              dpsObj.id,
            ),
          )
        }
        if (dpsObj.offenceCodes != nomisObj.offenceCodes) {
          differences.add(
            Difference(
              "$parentProperty.offenceCodes",
              dpsObj.offenceCodes,
              nomisObj.offenceCodes,
              dpsObj.id,
            ),
          )
        }
        if (dpsObj.sentenceCalcType != nomisObj.sentenceCalcType) {
          differences.add(
            Difference(
              "$parentProperty.sentenceCalcType",
              dpsObj.sentenceCalcType,
              nomisObj.sentenceCalcType,
              dpsObj.id,
            ),
          )
        }
        if (dpsObj.fine != nomisObj.fine) {
          differences.add(Difference("$parentProperty.fine", dpsObj.fine, nomisObj.fine, dpsObj.id))
        }
        if (dpsObj.sentencingAppearanceDate != nomisObj.sentencingAppearanceDate) {
          differences.add(
            Difference(
              "$parentProperty.sentencingAppearanceDate",
              dpsObj.sentencingAppearanceDate,
              nomisObj.sentencingAppearanceDate,
              dpsObj.id,
            ),
          )
        }
        if (dpsObj.status != nomisObj.status) {
          differences.add(Difference("$parentProperty.status", dpsObj.status, nomisObj.status, dpsObj.id))
        }

        differences.addAll(
          compareLists(
            sortTerms(dpsObj.terms),
            sortTerms(nomisObj.terms),
            "$parentProperty.terms",
          ),
        )
      }

      is SentenceTermFields -> {
        if (dpsObj.years != (nomisObj as SentenceTermFields).years) {
          differences.add(Difference("$parentProperty.years", dpsObj.years, nomisObj.years, dpsObj.id))
        }
        if (dpsObj.months != nomisObj.months) {
          differences.add(Difference("$parentProperty.months", dpsObj.months, nomisObj.months, dpsObj.id))
        }
        if (dpsObj.weeks != nomisObj.weeks) {
          differences.add(Difference("$parentProperty.weeks", dpsObj.weeks, nomisObj.weeks, dpsObj.id))
        }
        if (dpsObj.days != nomisObj.days) {
          differences.add(Difference("$parentProperty.days", dpsObj.days, nomisObj.days, dpsObj.id))
        }
        if (dpsObj.lifeSentenceFlag != nomisObj.lifeSentenceFlag) {
          differences.add(
            Difference(
              "$parentProperty.lifeSentenceFlag",
              dpsObj.lifeSentenceFlag,
              nomisObj.lifeSentenceFlag,
              dpsObj.id,
            ),
          )
        }
        if (dpsObj.sentenceTermCode != nomisObj.sentenceTermCode) {
          differences.add(
            Difference(
              "$parentProperty.sentenceTermCode",
              dpsObj.sentenceTermCode,
              nomisObj.sentenceTermCode,
              dpsObj.id,
            ),
          )
        }
      }
    }

    return differences
  }

  fun sortTerms(terms: List<SentenceTermFields>): List<SentenceTermFields> = terms.sortedWith(
    compareBy<SentenceTermFields> { it.sentenceTermCode }
      .thenBy { it.years }
      .thenBy { it.months }
      .thenBy { it.weeks }
      .thenBy { it.days }
      .thenBy { it.lifeSentenceFlag },

  )

  fun sortCharges(charges: List<ChargeFields>): List<ChargeFields> = charges.sortedWith(
    compareBy<ChargeFields> { it.offenceCode }
      .thenBy { it.offenceDate }
      .thenBy { it.outcome }
      .thenBy { it.offenceEndDate },
  )

  fun <T> compareLists(dpsList: List<T>, nomisList: List<T>, parentProperty: String): List<Difference> {
    val differences = mutableListOf<Difference>()
    val maxSize = maxOf(dpsList.size, nomisList.size)
    if (dpsList.size != nomisList.size) {
      differences.add(Difference(parentProperty, dpsList.size, nomisList.size))
    } else {
      for (i in 0 until maxSize) {
        val dpsObj = dpsList.getOrNull(i)
        val nomisObj = nomisList.getOrNull(i)
        differences.addAll(compareObjects(dpsObj, nomisObj, "$parentProperty[$i]"))
      }
    }
    return differences
  }
}

data class MismatchCaseResponse(
  val offenderNo: String,
  val dpsCaseId: String,
  val nomisCaseId: Long,
  val mismatch: MismatchCase?,
)

data class MismatchPrisonerCasesResponse(
  val offenderNo: String,
  val mismatches: List<MismatchCaseResponse>,
)

data class CaseFields(
  val active: Boolean,
  val id: String,
  val appearances: List<AppearanceFields> = emptyList(),
  val caseReferences: List<String> = emptyList(),
  val sentences: List<SentenceFields> = emptyList(),
  /* omissions of data that is not consistent: court is not at case level in DPS
     date at Case level is only relevant to nomis */
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    other as CaseFields
    return active == other.active
  }
}

data class AppearanceFields(
  /* legal case type omitted */
  /* appearance times omitted */
  /* next appearance time is checked on generated appearance rather than this field (which can diverge in nomis) */
  val date: LocalDate?,
  val court: String,
  val outcome: String?,
  val id: String,
  val charges: List<ChargeFields> = emptyList(),
  // for sorting purposes, sometimes charge outcome is required to differentiate between appearances on the same day with the same charge
  val chargeAsString: String? = null,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    other as AppearanceFields
    return date == other.date &&
      court == other.court &&
      outcome == other.outcome
  }
}

data class ChargeFields(
  val offenceCode: String,
  val offenceDate: LocalDate?,
  val offenceEndDate: LocalDate?,
  val outcome: String?,
  val id: String,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    other as ChargeFields
    return offenceCode == other.offenceCode &&
      offenceDate == other.offenceDate &&
      offenceEndDate == other.offenceEndDate &&
      outcome == other.outcome
  }

  fun toSortString(): String = "ChargeFields(outcome=$outcome, offenceDate=$offenceDate, offenceCode='$offenceCode', offenceEndDate=$offenceEndDate,)"
}

data class SentenceFields(
  val sentencingAppearanceDate: LocalDate,
  val sentenceCategory: String?,
  val sentenceCalcType: String?,
  val fine: BigDecimal?,
  val status: String,
  val id: String,
  val offenceCodes: String? = null,
  // for sorting purposes, multiple sentences can be very similar
  val termsAsString: String? = null,
  val terms: List<SentenceTermFields> = emptyList(),
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    other as SentenceFields
    return sentencingAppearanceDate == other.sentencingAppearanceDate &&
      sentenceCategory == other.sentenceCategory &&
      sentenceCalcType == other.sentenceCalcType &&
      fine == other.fine &&
      status == other.status
  }
}

data class SentenceTermFields(
  val sentenceTermCode: String?,
  val years: Int?,
  val months: Int?,
  val weeks: Int?,
  val days: Int?,
  val lifeSentenceFlag: Boolean?,
  val id: String,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    other as SentenceTermFields
    return sentenceTermCode == other.sentenceTermCode &&
      years == other.years &&
      months == other.months &&
      days == other.days &&
      weeks == other.weeks &&
      lifeSentenceFlag == other.lifeSentenceFlag
  }

  fun toSortString(): String = "SentenceTermFields(sentenceTermCode=$sentenceTermCode, years=$years, months=$months, weeks=$weeks, days=$days, lifeSentenceFlag=$lifeSentenceFlag)"
}

data class MismatchCase(
  val nomisCase: CaseFields,
  val dpsCase: CaseFields,
  val differences: List<Difference> = emptyList(),
)

data class Difference(val property: String, val dps: Any?, val nomis: Any?, val id: String? = null)
