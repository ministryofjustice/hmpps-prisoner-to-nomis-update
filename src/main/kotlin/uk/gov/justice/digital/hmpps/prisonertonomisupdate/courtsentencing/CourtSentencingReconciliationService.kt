package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.SentenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReference
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReferenceList
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.doApiCallWithRetries
import java.math.BigDecimal
import java.time.LocalDate

@Service
class CourtSentencingReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val dpsApiService: CourtSentencingApiService,
  private val nomisApiService: CourtSentencingNomisApiService,
  private val mappingService: CourtCaseMappingService,
  private val courtSentencingService: CourtSentencingService,
  private val objectMapper: ObjectMapper,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun manualCheckCaseDps(dpsCaseId: String): MismatchCaseResponse = mappingService.getMappingGivenCourtCaseId(dpsCourtCaseId = dpsCaseId).let {
    MismatchCaseResponse(
      nomisCaseId = it.nomisCourtCaseId,
      dpsCaseId = dpsCaseId,
      mismatch = checkCase(dpsCaseId = it.dpsCourtCaseId, nomisCaseId = it.nomisCourtCaseId),
    )
  }

  suspend fun manualCheckCaseNomis(nomisCaseId: Long): MismatchCaseResponse = mappingService.getMappingGivenNomisCourtCaseId(nomisCourtCaseId = nomisCaseId).let {
    MismatchCaseResponse(
      nomisCaseId = nomisCaseId,
      dpsCaseId = it.dpsCourtCaseId,
      mismatch = checkCase(dpsCaseId = it.dpsCourtCaseId, nomisCaseId = it.nomisCourtCaseId),
    )
  }

  suspend fun manualCheckCaseOffenderNo(offenderNo: String): List<MismatchCaseResponse> = nomisApiService.getCourtCasesByOffender(offenderNo).map {
    manualCheckCaseNomis(nomisCaseId = it.id)
  }

  suspend fun checkCase(dpsCaseId: String, nomisCaseId: Long): MismatchCase? = runCatching {
    val nomisResponse = doApiCallWithRetries { nomisApiService.getCourtCaseForMigration(nomisCaseId) }
    val dpsResponse = doApiCallWithRetries { dpsApiService.getCourtCaseForReconciliation(dpsCaseId) }

    val cc = dpsResponse.appearances.flatMap { appearance -> appearance.charges }
    val sentences = cc.mapNotNull { charge -> charge.sentence }.map {
      SentenceFields(
        startDate = it.sentenceStartDate,
        sentenceCategory = it.sentenceCategory,
        sentenceCalcType = it.sentenceCalcType,
        fine = it.fineAmount,
        status = if (it.active) {
          "A"
        } else {
          "I"
        },
        id = it.sentenceUuid.toString(),
        terms = it.periodLengths.map { term ->
          SentenceTermFields(
            sentenceTermCode = term.sentenceTermCode,
            lifeSentenceFlag = term.lifeSentence,
            years = term.periodYears,
            months = term.periodMonths,
            weeks = term.periodWeeks,
            days = term.periodDays,
            id = term.periodLengthUuid.toString(),
          )
        },
      )
    }
    val dpsFields = CaseFields(
      active = dpsResponse.active,
      id = dpsCaseId,
      appearances = dpsResponse.appearances.map { appearanceResponse ->
        AppearanceFields(
          date = appearanceResponse.appearanceDate,
          court = appearanceResponse.courtCode,
          outcome = appearanceResponse.nomisOutcomeCode,
          id = appearanceResponse.appearanceUuid.toString(),
          charges = appearanceResponse.charges.map { appearanceChargeResponse ->
            ChargeFields(
              offenceCode = appearanceChargeResponse.offenceCode,
              offenceDate = appearanceChargeResponse.offenceStartDate,
              offenceEndDate = appearanceChargeResponse.offenceEndDate,
              outcome = appearanceChargeResponse.nomisOutcomeCode,
              id = appearanceChargeResponse.chargeUuid.toString(),
            )
          },
        )
      },
      sentences = sentences,
      caseReferences = dpsResponse.courtCaseLegacyData?.caseReferences?.map { it.offenderCaseReference } ?: emptyList(),
    )
    val nomisFields = CaseFields(
      active = nomisResponse.caseStatus.code == "A",
      id = nomisResponse.id.toString(),
      appearances = nomisResponse.courtEvents.map { eventResponse ->
        AppearanceFields(
          date = eventResponse.eventDateTime.toLocalDate(),
          court = eventResponse.courtId,
          outcome = eventResponse.outcomeReasonCode?.code,
          id = eventResponse.id.toString(),
          charges = eventResponse.courtEventCharges.map { chargeResponse ->
            ChargeFields(
              offenceCode = chargeResponse.offenderCharge.offence.offenceCode,
              offenceDate = chargeResponse.offenceDate,
              offenceEndDate = chargeResponse.offenceEndDate,
              outcome = chargeResponse.resultCode1?.code,
              id = chargeResponse.offenderCharge.id.toString(),
            )
          },
        )
      },
      sentences = nomisResponse.sentences.map { sentenceResponse ->
        SentenceFields(
          startDate = sentenceResponse.startDate,
          sentenceCategory = sentenceResponse.category.code,
          sentenceCalcType = sentenceResponse.calculationType.code,
          fine = sentenceResponse.fineAmount,
          status = sentenceResponse.status,
          id = sentenceResponse.sentenceSeq.toString(),
          terms = sentenceResponse.sentenceTerms.map { term ->
            SentenceTermFields(
              years = term.years,
              months = term.months,
              weeks = term.weeks,
              days = term.days,
              sentenceTermCode = term.sentenceTermType?.code,
              lifeSentenceFlag = term.lifeSentenceFlag,
              id = term.termSequence.toString(),
            )
          },
        )
      },
      caseReferences = nomisResponse.caseInfoNumbers.map { it.reference },
    )

    val differenceList = compareObjects(dpsFields, nomisFields)
    if (differenceList.isNotEmpty()) {
      log.info("Differences: ${objectMapper.writeValueAsString(differenceList)}")
      return MismatchCase(
        nomisCase = nomisFields,
        dpsCase = dpsFields,
        differences = differenceList,
      )
    } else {
      return null
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
          differences.add(Difference("$parentProperty.active", dpsObj.active, nomisObj.active))
        }
        val sortedDpsAppearances = dpsObj.appearances.sortedWith(
          compareBy<AppearanceFields> { it.date }
            .thenBy { it.court }
            .thenBy { it.outcome },
        )

        val sortedNomisAppearances = nomisObj.appearances.sortedWith(
          compareBy<AppearanceFields> { it.date }
            .thenBy { it.court }
            .thenBy { it.outcome },
        )

        if (!dpsObj.caseReferences.containsAll(nomisObj.caseReferences)) {
          differences.add(Difference("$parentProperty.caseReferences", dpsObj.caseReferences, nomisObj.caseReferences))
        }

        differences.addAll(compareLists(sortedDpsAppearances, sortedNomisAppearances, "$parentProperty.appearances"))

        val sortedDpsSentences = dpsObj.sentences.sortedWith(
          compareBy<SentenceFields> { it.sentenceCategory }
            .thenBy { it.startDate }
            .thenBy { it.status },
        )

        val sortedNomisSentences = nomisObj.sentences.sortedWith(
          compareBy<SentenceFields> { it.sentenceCategory }
            .thenBy { it.startDate }
            .thenBy { it.status },
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

        fun sortCharges(charges: List<ChargeFields>): List<ChargeFields> = charges.sortedWith(
          compareBy<ChargeFields> { it.offenceCode }
            .thenBy { it.offenceDate }
            .thenBy { it.outcome },
        )
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
              dpsObj.offenceDate,
              nomisObj.offenceDate,
              dpsObj.id,
            ),
          )
        }
        if (dpsObj.offenceEndDate != nomisObj.offenceEndDate) {
          differences.add(
            Difference(
              "$parentProperty.offenceEndDate",
              dpsObj.offenceEndDate,
              nomisObj.offenceEndDate,
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
        if (dpsObj.startDate != nomisObj.startDate) {
          differences.add(Difference("$parentProperty.startDate", dpsObj.startDate, nomisObj.startDate, dpsObj.id))
        }
        if (dpsObj.status != nomisObj.status) {
          differences.add(Difference("$parentProperty.status", dpsObj.status, nomisObj.status, dpsObj.id))
        }

        fun sortTerms(terms: List<SentenceTermFields>): List<SentenceTermFields> = terms.sortedWith(
          compareBy<SentenceTermFields> { it.sentenceTermCode }
            .thenBy { it.years }
            .thenBy { it.months }
            .thenBy { it.weeks }
            .thenBy { it.days },
        )
        differences.addAll(
          compareLists(
            sortTerms(dpsObj.terms),
            sortTerms(nomisObj.terms),
            "$parentProperty.sentenceTerms",
          ),
        )
      }
    }

    return differences
  }

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

  suspend fun chargeInsertedRepair(request: CourtChargeRequest) {
    val event: CourtSentencingService.CourtChargeCreatedEvent = CourtSentencingService.CourtChargeCreatedEvent(
      additionalInformation = CourtSentencingService.CourtChargeAdditionalInformation(
        courtChargeId = request.dpsChargeId,
        courtCaseId = request.dpsCaseId,
        source = "DPS",
        courtAppearanceId = null,
      ),
      personReference = PersonReferenceList(
        identifiers = listOf(
          PersonReference(
            type = "NOMS",
            value = request.offenderNo,
          ),
        ),
      ),
    )
    courtSentencingService.createCharge(event)
  }
}

private fun List<SentenceResponse>.findNomisSentenceForCharge(chargeId: Long, eventId: Long): SentenceResponse? {
  val sentencesForAppearance = this.filter { sentence -> sentence.courtOrder?.eventId == eventId }
  return sentencesForAppearance.find { sentence -> sentence.offenderCharges.any { it.id == chargeId } }
}

data class MismatchCaseResponse(
  val dpsCaseId: String,
  val nomisCaseId: Long,
  val mismatch: MismatchCase?,
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
}

data class SentenceFields(
  val startDate: LocalDate,
  val sentenceCategory: String?,
  val sentenceCalcType: String?,
  val fine: BigDecimal?,
  val status: String,
  val id: String,
  val terms: List<SentenceTermFields> = emptyList(),
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    other as SentenceFields
    return startDate == other.startDate &&
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
}

data class MismatchCase(
  val nomisCase: CaseFields,
  val dpsCase: CaseFields,
  val differences: List<Difference> = emptyList(),
)

data class Difference(val property: String, val dps: Any?, val nomis: Any?, val id: String? = null)
