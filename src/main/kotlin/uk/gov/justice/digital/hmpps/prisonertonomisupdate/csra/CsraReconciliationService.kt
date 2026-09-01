package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.model.CsraCurrentRating
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
    val nomisCsra = csraNomisApiService.getCurrentCsraForPrisonerOrNull(offenderNo)
    val dpsCsra = csraDpsApiService.getCsraCurrent(offenderNo)

    val nomisField = nomisCsra?.let {
      fun deriveClassification(): String? {
        val derivedLevel = it.reviewLevel ?: it.overrideLevel ?: it.calculatedLevel
        return if (derivedLevel == AssessmentLevel.PEND) null else derivedLevel.toString()
      }

      CsraDetailFields(
        id = "${it.bookingId}-${it.sequence}",
        assessmentDate = it.assessmentDate,
        level = deriveClassification(),
      )
    }

    val dpsField = dpsCsra?.let {
      CsraDetailFields(
        id = it.reviewId.toString(),
        assessmentDate = it.finalDate ?: it.provisionalDate ?: it.startedAt?.toLocalDate(),
        level = it.toNomisLevel()?.toString(),
      )
    }

    val differenceList = compareObjects(dpsField, nomisField)

    if (differenceList.isNotEmpty()) {
      log.info("difference found for offenderNo=$offenderNo: $differenceList")
      telemetryClient.trackEvent(
        "$TELEMETRY_CSRA_PREFIX-mismatch",
        mapOf(
          "prisoner" to offenderNo,
        ) + differenceList.associate { it.property to it.toString() },
      )
      MismatchCsra(
        nomis = nomisField,
        dps = dpsField,
        differences = differenceList,
      )
    } else {
      null
    }
  }.onFailure {
    log.error("Unable to match CSRAs for offenderNo=$offenderNo", it)
  }.getOrNull()

//  private fun <T> compareLists(dpsList: List<T>, nomisList: List<T>, parentProperty: String): List<Difference> {
//    val differences = mutableListOf<Difference>()
//    val maxSize = maxOf(dpsList.size, nomisList.size)
//    if (dpsList.size != nomisList.size) {
//      differences.add(Difference(parentProperty, dpsList.size, nomisList.size))
//    } else {
//      for (i in 0 until maxSize) {
//        val dpsObj = dpsList.getOrNull(i)
//        val nomisObj = nomisList.getOrNull(i)
//        differences.addAll(compareObjects(dpsObj, nomisObj, "$parentProperty[$i]"))
//      }
//    }
//    return differences
//  }

  private fun compareObjects(dpsDetail: CsraDetailFields?, nomisDetail: CsraDetailFields?): List<Difference> {
    if (dpsDetail == null && nomisDetail == null) return emptyList()
    if (dpsDetail == null) return listOf(Difference("current-csra", 0, 1, nomisId = nomisDetail?.id))
    if (nomisDetail == null) return listOf(Difference("current-csra", 1, 0, dpsId = dpsDetail.id))

    val differences = mutableListOf<Difference>()

    if (dpsDetail.assessmentDate != nomisDetail.assessmentDate) {
      differences.add(
        Difference(
          "assessmentDate",
          dpsDetail.assessmentDate,
          nomisDetail.assessmentDate,
          dpsDetail.id,
          nomisDetail.id,
        ),
      )
    }
    if (dpsDetail.level != nomisDetail.level) {
      differences.add(
        Difference(
          "level",
          dpsDetail.level,
          nomisDetail.level,
          dpsDetail.id,
          nomisDetail.id,
        ),
      )
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
// private fun CsraReviewSummary.toNomisLevel(): AssessmentLevel = legacy?.level?.let { AssessmentLevel.valueOf(it.name) }
//  ?: when (rating) {
//    CsraReviewSummary.Rating.HIGH, CsraReviewSummary.Rating.HIGH_GENERAL, CsraReviewSummary.Rating.HIGH_SPECIFIC -> AssessmentLevel.HI
//    CsraReviewSummary.Rating.STANDARD -> AssessmentLevel.STANDARD
//  }

private fun CsraCurrentRating.toNomisLevel(): AssessmentLevel? = when (rating) {
  CsraCurrentRating.Rating.HIGH, CsraCurrentRating.Rating.HIGH_GENERAL, CsraCurrentRating.Rating.HIGH_SPECIFIC -> AssessmentLevel.HI
  CsraCurrentRating.Rating.STANDARD -> AssessmentLevel.STANDARD
  null -> null
}

data class MismatchCsra(
  val nomis: CsraDetailFields?,
  val dps: CsraDetailFields?,
  val differences: List<Difference> = emptyList(),
)

data class CsraDetailFields(
  val id: String,
  val sequence: Int? = null,
  val assessmentDate: LocalDate?,
  val level: String?,
)

data class Difference(
  val property: String,
  val dps: Any?,
  val nomis: Any?,
  val dpsId: String? = null,
  val nomisId: String? = null,
)
