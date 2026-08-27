package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.model.CsraCurrentRating
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.model.CsraReviewSummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.generateRangesReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AssessmentLevel
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.LocalDate

@Service
class CsraReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val csraNomisApiService: CsraNomisApiService,
  private val nomisApiService: NomisApiService,
  private val csraDpsApiService: CsraDpsApiService,
  @Value($$"${reports.csra.reconciliation.page-size:1000}") private val pageSize: Int = 1000,
  @Value($$"${reports.csra.reconciliation.thread-count:10}") private val threadCount: Int = 10,
) {
  private companion object {
    private const val TELEMETRY_CSRA_PREFIX = "csra-reports-reconciliation"
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun manualCheckCsra(offenderNo: String): MismatchCsra? = checkCsra(offenderNo)

  suspend fun generateReconciliationReportBatch(activeOnly: Boolean) {
    telemetryClient.trackEvent(
      "$TELEMETRY_CSRA_PREFIX-requested",
      mapOf("activeOnly" to activeOnly.toString()),
    )

    runCatching { generateReconciliationReport(activeOnly) }
      .onSuccess {
        telemetryClient.trackEvent(
          "$TELEMETRY_CSRA_PREFIX-report",
          mapOf(
            "activeOnly" to activeOnly.toString(),
            "csra-count" to it.itemsChecked.toString(),
            "page-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ),
        )
      }
      .onFailure {
        telemetryClient.trackEvent(
          "$TELEMETRY_CSRA_PREFIX-report",
          mapOf(
            "success" to "false",
            "error" to (it.message ?: it.javaClass.name),
          ),
        )
        log.error("CSRA reconciliation report failed", it)
      }
  }

  private suspend fun generateReconciliationReport(activeOnly: Boolean): ReconciliationResult<MismatchCsra> = generateRangesReconciliationReport(
    threadCount = threadCount,
    checkMatch = ::checkCsra,
    idRanges = { nomisApiService.getAllPrisonersIdRanges(pageSize.toLong(), activeOnly) },
    idsInRange = { range -> this.getOffenderNosInRange(range.fromRootOffenderId, range.toRootOffenderId, activeOnly) },
  )

  internal suspend fun checkCsra(offenderNo: String): MismatchCsra? = runCatching {
    val nomisCsras = csraNomisApiService.getCsrasForPrisoner(offenderNo)
    val dpsCsras = csraDpsApiService.getCsraHistory(offenderNo)
    val dpsCurrent = csraDpsApiService.getCsraCurrent(offenderNo)?.takeIf { current -> dpsCsras.content.none { it.id == current.reviewId } }

    val nomisFields = CsraFields(
      prisonNumber = offenderNo,
      csras = nomisCsras.csras.map {
        CsraDetailFields(
          assessmentDate = it.assessmentDate,
          level = (it.approvedLevel ?: it.reviewLevel ?: it.calculatedLevel ?: AssessmentLevel.STANDARD).toString(),
        )
      },
    )
    val dpsFields = CsraFields(
      prisonNumber = offenderNo,
      csras = dpsCsras.content.map {
        CsraDetailFields(
          assessmentDate = it.legacy?.assessmentDate ?: it.recordedDate,
          level = it.toNomisLevel().toString(),
        )
      } + (
        dpsCurrent?.rating?.let {
          listOf(
            CsraDetailFields(
              assessmentDate = dpsCurrent.finalDate ?: dpsCurrent.provisionalDate ?: dpsCurrent.startedAt?.toLocalDate(),
              level = dpsCurrent.toNomisLevel()?.toString(),
            ),
          )
        } ?: emptyList()
        ),
    )

    val differenceList = compareObjects(dpsFields, nomisFields, "prisoner-csras")

    if (differenceList.isNotEmpty()) {
      log.info("difference found for offenderNo=$offenderNo: $differenceList")
      telemetryClient.trackEvent(
        "$TELEMETRY_CSRA_PREFIX-mismatch",
        mapOf(
          "prisoner" to offenderNo,
        ) + differenceList.associate { it.property to it.toString() },
      )
      MismatchCsra(
        nomis = nomisFields,
        dps = dpsFields,
        differences = differenceList,
      )
    } else {
      null
    }
  }.onFailure {
    log.error("Unable to match CSRAs for offenderNo=$offenderNo", it)
  }.getOrNull()

  private fun <T> compareLists(dpsList: List<T>, nomisList: List<T>, parentProperty: String): List<Difference> {
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

  private fun compareObjects(dpsObj: Any?, nomisObj: Any?, parentProperty: String): List<Difference> {
    if (dpsObj == null && nomisObj == null) return emptyList()
    if (dpsObj == null || nomisObj == null || dpsObj::class != nomisObj::class) return listOf(Difference(parentProperty, dpsObj, nomisObj))

    val differences = mutableListOf<Difference>()

    when (dpsObj) {
      is CsraFields -> {
        nomisObj as CsraFields

        if (dpsObj.prisonNumber != nomisObj.prisonNumber) {
          differences.add(Difference("$parentProperty.prisonNumber", dpsObj.prisonNumber, nomisObj.prisonNumber))
        }
        val sortedDpsCsras = dpsObj.csras.sortedWith(
          compareBy<CsraDetailFields> { it.assessmentDate }
            .thenBy { it.level },
        )
        val sortedNomisCsras = nomisObj.csras.sortedWith(
          compareBy<CsraDetailFields> { it.assessmentDate }
            .thenBy { it.level },
        )

        differences.addAll(compareLists(sortedDpsCsras, sortedNomisCsras, "$parentProperty.csras"))
      }

      is CsraDetailFields -> {
        nomisObj as CsraDetailFields
        if (dpsObj.assessmentDate != nomisObj.assessmentDate) {
          differences.add(Difference("$parentProperty.assessmentDate", dpsObj.assessmentDate, nomisObj.assessmentDate))
        }
        if (dpsObj.level != nomisObj.level) {
          differences.add(Difference("$parentProperty.level: assessmentDate ${nomisObj.assessmentDate}", dpsObj.level, nomisObj.level))
        }
      }
    }
    return differences
  }

  internal suspend fun getOffenderNosInRange(
    fromRootOffenderId: Long,
    toRootOffenderId: Long,
    activeOnly: Boolean,
  ): ReconciliationPageResult<String> = runCatching {
    nomisApiService.getAllPrisonersInRange(
      fromRootOffenderId = fromRootOffenderId,
      toRootOffenderId = toRootOffenderId,
      activeOnly = activeOnly,
    )
  }.fold(
    onSuccess = { prisoners ->
      ReconciliationSuccessPageResult(ids = prisoners.map { it.prisonNumber }, last = 0)
        .also { log.info("Page requested from fromRootOffenderId: $fromRootOffenderId, toRootOffenderId: $toRootOffenderId, with ${it.ids.size} prisoners") }
    },
    onFailure = {
      telemetryClient.trackEvent(
        "$TELEMETRY_CSRA_PREFIX-mismatch-page-error",
        mapOf(
          "fromRootOffenderId" to fromRootOffenderId.toString(),
          "toRootOffenderId" to toRootOffenderId.toString(),
          "error" to (it.message ?: it.javaClass.name),
        ),
      )
      log.error("Unable to match entire page of prisoners from fromRootOffenderId: $fromRootOffenderId, toRootOffenderId: $toRootOffenderId", it)
      ReconciliationErrorPageResult(it)
    },
  )
}

// The DPS review rating collapses to a single value, but legacy (NOMIS migrated) reviews retain the raw NOMIS
// level, which is a closer match to the level NOMIS itself holds for the review.
private fun CsraReviewSummary.toNomisLevel(): AssessmentLevel = legacy?.level?.let { AssessmentLevel.valueOf(it.name) }
  ?: when (rating) {
    CsraReviewSummary.Rating.HIGH, CsraReviewSummary.Rating.HIGH_GENERAL, CsraReviewSummary.Rating.HIGH_SPECIFIC -> AssessmentLevel.HI
    CsraReviewSummary.Rating.STANDARD -> AssessmentLevel.STANDARD
  }

private fun CsraCurrentRating.toNomisLevel(): AssessmentLevel? = when (rating) {
  CsraCurrentRating.Rating.HIGH, CsraCurrentRating.Rating.HIGH_GENERAL, CsraCurrentRating.Rating.HIGH_SPECIFIC -> AssessmentLevel.HI
  CsraCurrentRating.Rating.STANDARD -> AssessmentLevel.STANDARD
  null -> null
}

data class MismatchCsra(
  val nomis: CsraFields,
  val dps: CsraFields,
  val differences: List<Difference> = emptyList(),
)

data class CsraFields(
  val prisonNumber: String,
  val csras: List<CsraDetailFields> = emptyList(),
)

data class CsraDetailFields(
  val assessmentDate: LocalDate?,
  val level: String?,
)

data class Difference(val property: String, val dps: Any?, val nomis: Any?, val id: String? = null)
