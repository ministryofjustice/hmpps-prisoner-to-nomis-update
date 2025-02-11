package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReference
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReferenceList
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.doApiCallWithRetries
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class CourtSentencingReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val dpsApiService: CourtSentencingApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: CourtCaseMappingService,
  private val courtSentencingService: CourtSentencingService,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun manualCheckCaseDps(dpsCaseId: String): MismatchCaseResponse = mappingService.getMappingGivenCourtCaseId(dpsCourtCaseId = dpsCaseId).let {
    MismatchCaseResponse(nomisCaseId = it.nomisCourtCaseId, dpsCaseId = dpsCaseId, mismatch = checkCase(dpsCaseId = it.dpsCourtCaseId, nomisCaseId = it.nomisCourtCaseId))
  }

  suspend fun manualCheckCaseNomis(nomisCaseId: Long): MismatchCaseResponse = mappingService.getMappingGivenNomisCourtCaseId(nomisCourtCaseId = nomisCaseId).let {
    MismatchCaseResponse(nomisCaseId = nomisCaseId, dpsCaseId = it.dpsCourtCaseId, mismatch = checkCase(dpsCaseId = it.dpsCourtCaseId, nomisCaseId = it.nomisCourtCaseId))
  }

  suspend fun manualCheckCaseOffenderNo(offenderNo: String): List<MismatchCaseResponse> = nomisApiService.getCourtCasesByOffender(offenderNo).map {
    manualCheckCaseNomis(nomisCaseId = it.id)
  }

  suspend fun checkCase(dpsCaseId: String, nomisCaseId: Long): MismatchCase? = runCatching {
    val nomisResponse = doApiCallWithRetries { nomisApiService.getCourtCaseForMigration(nomisCaseId) }
    val dpsResponse = doApiCallWithRetries { dpsApiService.getCourtCaseForReconciliation(dpsCaseId) }

    val dpsFields = CaseFields(
      active = dpsResponse.active,
      id = dpsCaseId,
      appearances = dpsResponse.appearances.map {
        AppearanceFields(
          date = it.appearanceDate,
          court = it.courtCode,
          outcome = it.nomisOutcomeCode,
          id = it.lifetimeUuid.toString(),
          charges = it.charges.map {
            ChargeFields(
              offenceCode = it.offenceCode,
              offenceDate = it.offenceStartDate,
              offenceEndDate = it.offenceEndDate,
              // TODO consider if offendercharge.resultCode1 can be reconciled with dps
              outcome = it.nomisOutcomeCode,
              id = it.lifetimeUuid.toString(),
            )
          },
        )
      },
      caseReferences = dpsResponse.caseReferences.map { it.offenderCaseReference },
    )
    val nomisFields = CaseFields(
      active = nomisResponse.caseStatus.code == "A",
      id = nomisResponse.id.toString(),
      appearances = nomisResponse.courtEvents.map {
        AppearanceFields(
          date = LocalDateTime.parse(it.eventDateTime).toLocalDate(),
          court = it.courtId,
          outcome = it.outcomeReasonCode?.code,
          id = it.id.toString(),
          charges = it.courtEventCharges.map {
            ChargeFields(
              offenceCode = it.offenderCharge.offence.offenceCode,
              offenceDate = it.offenceDate,
              offenceEndDate = it.offenceEndDate,
              outcome = it.resultCode1?.code,
              id = it.offenderCharge.id.toString(),
            )
          },
        )
      },
      caseReferences = nomisResponse.caseInfoNumbers.map { it.reference },
    )

    val differenceList = compareObjects(dpsFields, nomisFields)
    if (differenceList.isNotEmpty()) {
      log.info("Differences: $differenceList")
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

data class MismatchCase(
  val nomisCase: CaseFields,
  val dpsCase: CaseFields,
  val differences: List<Difference> = emptyList(),
)

data class Difference(val property: String, val dps: Any?, val nomis: Any?, val id: String? = null)
