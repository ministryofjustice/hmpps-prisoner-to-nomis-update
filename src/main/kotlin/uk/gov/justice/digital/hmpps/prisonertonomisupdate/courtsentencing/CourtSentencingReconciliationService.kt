package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.court.sentencing.model.ReconciliationCourtCase
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.generateReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth
import java.math.BigDecimal
import java.time.LocalDate

@Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
@Service
class CourtSentencingReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val dpsApiService: CourtSentencingApiService,
  private val nomisApiService: CourtSentencingNomisApiService,
  private val nomisPrisonerApiService: NomisApiService,
  private val mappingService: CourtSentencingMappingService,
  @Value("\${reports.court-case.prisoner.reconciliation.page-size:10}") private val prisonerPageSize: Int = 10,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TELEMETRY_COURT_CASE_PRISONER_PREFIX = "court-case-prisoner-reconciliation"
    const val TELEMETRY_COURT_CASE_ACTIVE_PRISONER_PREFIX = "court-case-active-prisoner-reconciliation"

    val excludedCaseIds = CourtSentencingReconciliationService::class.java
      .getResource("/excludedCaseIdsReconciliation.txt")
      .readText()
      .split(",")
      .mapNotNull { it.trim().toLongOrNull() }
  }

  suspend fun generateCourtCasePrisonerReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_COURT_CASE_PRISONER_PREFIX-requested",
      mapOf(),
    )

    runCatching { generatePrisonerCourtCasesReconciliationReport() }
      .onSuccess {
        log.info("Prisoner court cases reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_COURT_CASE_PRISONER_PREFIX-report",
          mapOf(
            "prisoners-count" to it.itemsChecked.toString(),
            "pages-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) +
            it.mismatches.take(5).asPrisonerMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_COURT_CASE_PRISONER_PREFIX-report", mapOf("success" to "false", "error" to (it.message ?: "unknown")))
        log.error("Prisoner court case reconciliation report failed", it)
      }
  }

  suspend fun generateCourtCaseActivePrisonerReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_COURT_CASE_ACTIVE_PRISONER_PREFIX-requested",
      mapOf(),
    )

    runCatching { generateActivePrisonerCourtCasesReconciliationReport() }
      .onSuccess {
        log.info("Active prisoner court cases reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_COURT_CASE_ACTIVE_PRISONER_PREFIX-report",
          mapOf(
            "prisoners-count" to it.itemsChecked.toString(),
            "pages-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) +
            it.mismatches.take(5).asPrisonerMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_COURT_CASE_ACTIVE_PRISONER_PREFIX-report", mapOf("success" to "false", "error" to (it.message ?: "unknown")))
        log.error("Active prisoner court case active prisoner reconciliation report failed", it)
      }
  }

  private fun List<MismatchPrisonerCasesResponse>.asPrisonerMap(): Map<String, String> = this.associate { it.offenderNo to "cases=${it.mismatches.size}" }

  suspend fun generateActivePrisonerCourtCasesReconciliationReport(): ReconciliationResult<MismatchPrisonerCasesResponse> = generateReconciliationReport(
    threadCount = prisonerPageSize,
    checkMatch = ::checkActivePrisonerCasesMatch,
    nextPage = ::getNextBookingsForPageActiveOnly,
  )

  suspend fun generatePrisonerCourtCasesReconciliationReport(): ReconciliationResult<MismatchPrisonerCasesResponse> = generateReconciliationReport(
    threadCount = prisonerPageSize,
    checkMatch = ::checkPrisonerCasesMatch,
    nextPage = ::getNextBookingsForPageAll,
  )

  suspend fun manualCheckCaseDps(offenderNo: String, dpsCaseId: String): MismatchCaseResponse = mappingService.getMappingGivenCourtCaseId(dpsCourtCaseId = dpsCaseId).let {
    val caseResult = checkCase(dpsCaseId = it.dpsCourtCaseId, nomisCaseId = it.nomisCourtCaseId, offenderNo = offenderNo)
    MismatchCaseResponse(
      offenderNo = offenderNo,
      nomisCaseId = it.nomisCourtCaseId,
      dpsCaseId = dpsCaseId,
      nomisBookingId = caseResult?.nomisBookingId,
      mismatch = caseResult,
    )
  }

  suspend fun manualCheckCaseNomis(offenderNo: String, nomisCaseId: Long): MismatchCaseResponse = mappingService.getMappingGivenNomisCourtCaseId(nomisCourtCaseId = nomisCaseId).let {
    val caseResult = checkCase(dpsCaseId = it.dpsCourtCaseId, nomisCaseId = it.nomisCourtCaseId, offenderNo = offenderNo)
    MismatchCaseResponse(
      offenderNo = offenderNo,
      nomisCaseId = nomisCaseId,
      dpsCaseId = it.dpsCourtCaseId,
      nomisBookingId = caseResult?.nomisBookingId,
      mismatch = caseResult,
    )
  }

  suspend fun checkCasesNomis(
    offenderNo: String,
    nomisCaseIds: List<Long>,
    telemetryPrefix: String? = TELEMETRY_COURT_CASE_PRISONER_PREFIX,
  ): List<MismatchCaseResponse> = mappingService.getMappingsGivenNomisCourtCaseIds(nomisCourtCaseIds = nomisCaseIds).map {
    val caseResult = checkCase(
      dpsCaseId = it.dpsCourtCaseId,
      nomisCaseId = it.nomisCourtCaseId,
      offenderNo = offenderNo,
      telemetryPrefix = telemetryPrefix,
    )
    MismatchCaseResponse(
      offenderNo = offenderNo,
      nomisCaseId = it.nomisCourtCaseId,
      dpsCaseId = it.dpsCourtCaseId,
      nomisBookingId = caseResult?.nomisBookingId,
      mismatch = caseResult,
    )
  }

  suspend fun checkCaseCount(
    dpsCaseIds: List<String>,
    nomisCaseIds: List<Long>,
    offenderNo: String,
    telemetryPrefix: String? = TELEMETRY_COURT_CASE_PRISONER_PREFIX,
  ): MismatchCaseResponse? = if (nomisCaseIds.size != dpsCaseIds.size) {
    val nomisCount = nomisCaseIds.size.toLong()
    val dpsCount = dpsCaseIds.size.toString()
    telemetryClient.trackEvent(
      "$telemetryPrefix-mismatch",
      mapOf(
        "nomisCaseCount" to nomisCount.toString(),
        "dpsCaseCount" to dpsCount,
        "caseCountMismatch" to "true",
        "offenderNo" to offenderNo,
      ),
      null,
    )
    MismatchCaseResponse(
      offenderNo = offenderNo,
      caseCountMismatch = true,
      dpsCaseId = dpsCount,
      nomisCaseId = nomisCount,
      mismatch = null,
    )
  } else {
    null
  }

  suspend fun checkActivePrisonerCasesMatch(prisonerId: PrisonerIds): MismatchPrisonerCasesResponse? = checkPrisonerCasesMatch(
    prisonerId,
    telemetryPrefix = TELEMETRY_COURT_CASE_ACTIVE_PRISONER_PREFIX,
  )

  suspend fun checkPrisonerCasesMatch(prisonerId: PrisonerIds, telemetryPrefix: String? = TELEMETRY_COURT_CASE_PRISONER_PREFIX): MismatchPrisonerCasesResponse? = runCatching {
    manualCheckCaseOffenderNo(prisonerId.offenderNo, telemetryPrefix = telemetryPrefix)
      .filter { it.mismatch != null }
      .takeIf { it.isNotEmpty() }?.let {
        MismatchPrisonerCasesResponse(
          offenderNo = prisonerId.offenderNo,
          mismatches = it,
        )
      }?.also {
        it.mismatches.forEach { mismatch ->
          telemetryClient.trackEvent(
            "$telemetryPrefix-mismatch",
            mapOf(
              "offenderNo" to it.offenderNo,
              "nomisBookingId" to mismatch.nomisBookingId.toString(),
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
      "$telemetryPrefix-error",
      mapOf(
        "offenderNo" to prisonerId.offenderNo,
        "reason" to (it.message ?: "unknown"),
      ),
      null,
    )
  }.getOrNull()

  suspend fun manualCheckCaseOffenderNo(offenderNo: String, telemetryPrefix: String? = TELEMETRY_COURT_CASE_PRISONER_PREFIX): List<MismatchCaseResponse> {
    val caseIds = nomisApiService.getCourtCaseIdsByOffender(offenderNo)
    val dpsCaseIds = dpsApiService.getCourtCaseIdsForOffender(offenderNo).courtCaseUuids
    val checkCaseCount =
      checkCaseCount(dpsCaseIds, caseIds, offenderNo = offenderNo, telemetryPrefix = TELEMETRY_COURT_CASE_PRISONER_PREFIX)
    return if (checkCaseCount != null) {
      listOf(checkCaseCount)
    } else {
      if (caseIds.isNotEmpty()) {
        checkCasesNomis(
          offenderNo = offenderNo,
          nomisCaseIds = caseIds,
          telemetryPrefix = telemetryPrefix,
        )
      } else {
        emptyList()
      }
    }
  }

  suspend fun manualCheckCaseOffenderNoList(offenderNoList: List<String>): List<List<MismatchCaseResponse>> = offenderNoList.map {
    val caseIds = nomisApiService.getCourtCaseIdsByOffender(it)
    val dpsCaseIds = dpsApiService.getCourtCaseIdsForOffender(it).courtCaseUuids
    val checkCaseCount =
      checkCaseCount(dpsCaseIds, caseIds, offenderNo = it, telemetryPrefix = TELEMETRY_COURT_CASE_PRISONER_PREFIX)
    if (checkCaseCount != null) {
      listOf(checkCaseCount)
    } else {
      if (caseIds.isNotEmpty()) {
        checkCasesNomis(
          offenderNo = it,
          nomisCaseIds = caseIds,
        ).filter { caseResponse -> caseResponse.mismatch != null }
      } else {
        emptyList()
      }
    }
  }.also {
    log.info(it.toString())
  }

  private suspend fun getNextBookingsForPageActiveOnly(lastBookingId: Long): ReconciliationPageResult<PrisonerIds> = getNextBookingsForPage(lastBookingId, activeOnly = true)

  private suspend fun getNextBookingsForPageAll(lastBookingId: Long): ReconciliationPageResult<PrisonerIds> = getNextBookingsForPage(lastBookingId, activeOnly = false)

  private suspend fun getNextBookingsForPage(lastBookingId: Long, activeOnly: Boolean = false): ReconciliationPageResult<PrisonerIds> = runCatching {
    nomisPrisonerApiService.getAllLatestBookings(
      lastBookingId = lastBookingId,
      activeOnly = activeOnly,
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
    mismatch = checkCase(dpsCaseId = dpsCaseId, nomisCaseId = nomisCaseId, offenderNo = offenderNo),
  )

  suspend fun checkCase(dpsCaseId: String, nomisCaseId: Long, offenderNo: String, telemetryPrefix: String? = TELEMETRY_COURT_CASE_PRISONER_PREFIX): MismatchCase? = runCatching {
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
        // for recalls fallback to legacy data when DPS records a NOMIS recall as UNKNOWN
        sentenceCategory = it.sentenceCategory.takeUnless { it == "UNKNOWN" } ?: it.legacyData?.sentenceCategory,
        sentenceCalcType = it.sentenceCalcType.takeUnless { it == "UNKNOWN" } ?: it.legacyData?.sentenceCalcType,
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

    // when duplicate - do not compare status since DPS does not show case and can be inactive when active in NOMIS
    val isClonedDuplicate = dpsResponse.status == ReconciliationCourtCase.Status.DUPLICATE
    val dpsFields = CaseFields(
      active = dpsResponse.active.takeUnless { isClonedDuplicate },
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
      active = (nomisResponse.caseStatus.code == "A").takeUnless { isClonedDuplicate },
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
      sentences = nomisResponse.sentences.map { sentenceResponse ->
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
        // All sentences in prod with a case_id have a court order, all orders have an event id, all appearances have an event date
        SentenceFields(
          sentencingAppearanceDate = nomisResponse.courtEvents.find { appearance -> appearance.id == sentenceResponse.courtOrder?.eventId }?.eventDateTime?.toLocalDate()
            ?: LocalDate.MIN,
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

    val excludedCase = excludedCaseIds.contains(nomisCaseId)
    return if (differenceList.isNotEmpty()) {
      if (!excludedCase) {
        MismatchCase(
          nomisCase = nomisFields,
          dpsCase = dpsFields,
          nomisBookingId = nomisResponse.bookingId,
          differences = differenceList,
        )
      } else {
        telemetryClient.trackEvent(
          "$telemetryPrefix-excluded-case",
          mapOf(
            "offenderNo" to offenderNo,
            "nomisCaseId" to nomisCaseId.toString(),
            "dpsCaseId" to dpsCaseId,
            "reason" to ("Excluding reconciliation mismatches for case id $nomisCaseId"),
          ),
          null,
        )
        null
      }
    } else {
      if (excludedCase) {
        telemetryClient.trackEvent(
          "$telemetryPrefix-excluded-case-resolved",
          mapOf(
            "offenderNo" to offenderNo,
            "nomisCaseId" to nomisCaseId.toString(),
            "dpsCaseId" to dpsCaseId,
            "reason" to ("No reconciliation mismatches found for excluded case id $nomisCaseId. Remove from exclusion file."),
          ),
          null,
        )
      }
      null
    }
  }.onFailure {
    log.error("Unable to match case with ids: dps:$dpsCaseId and nomis:$nomisCaseId", it)
    telemetryClient.trackEvent(
      "$telemetryPrefix-error",
      mapOf(
        "offenderNo" to offenderNo,
        "nomisCaseId" to nomisCaseId.toString(),
        "dpsCaseId" to dpsCaseId,
        "reason" to (it.message ?: "unknown"),
      ),
      null,
    )
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
        if (dpsObj.offenceDate?.asValidDate() != nomisObj.offenceDate?.asValidDate()) {
          differences.add(
            Difference(
              "$parentProperty.offenceDate",
              dpsObj.offenceDate?.toString(),
              nomisObj.offenceDate?.toString(),
              dpsObj.id,
            ),
          )
        }

        if (dpsObj.offenceEndDate?.asValidDate() != nomisObj.offenceEndDate?.asValidDate()) {
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

  private fun LocalDate.asValidDate(): LocalDate? = if (this.isAfter(LocalDate.parse("1920-01-01")).and(this.isBefore(LocalDate.now().plusDays(1)))) {
    this
  } else {
    null
  }
}

data class MismatchCaseResponse(
  val offenderNo: String,
  val dpsCaseId: String,
  val nomisCaseId: Long,
  val nomisBookingId: Long? = null,
  val caseCountMismatch: Boolean = false,
  val mismatch: MismatchCase?,
)

data class MismatchPrisonerCasesResponse(
  val offenderNo: String,
  val mismatches: List<MismatchCaseResponse>,
)

data class CaseFields(
  val active: Boolean?,
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
  val differences: List<Difference> = emptyList(),
  val nomisCase: CaseFields,
  val dpsCase: CaseFields,
  val nomisBookingId: Long,
)

data class Difference(val property: String, val dps: Any?, val nomis: Any?, val id: String? = null)
