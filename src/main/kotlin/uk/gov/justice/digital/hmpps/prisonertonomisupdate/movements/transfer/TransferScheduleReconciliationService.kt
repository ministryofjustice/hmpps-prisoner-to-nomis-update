package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.generateReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService

@Service
class TransferScheduleReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApi: TransferSchedulerNomisApiService,
  private val dpsApi: TransferSchedulerDpsApiService,
  private val mappingApi: TransferSchedulerMappingApiService,
  private val nomisPrisonerApi: NomisApiService,
  @param:Value($$"${reports.transfer-scheduler.active-prisoners.reconciliation.page-size}") private val pageSize: Int = 100,
  @param:Value($$"${reports.transfer-scheduler.active-prisoners.reconciliation.thread-count}") private val threadCount: Int = 15,
) {
  internal companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    const val TELEMETRY_TRANSFER_SCHEDULER = "transfer-scheduler-reconciliation"
  }

  suspend fun generateTransferSchedulerReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_TRANSFER_SCHEDULER-requested",
      mapOf(),
    )

    runCatching { generateTransferSchedulerReconciliationReport() }
      .onSuccess {
        log.info("Transfer scheduler reconciliation report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_TRANSFER_SCHEDULER-report",
          mapOf(
            "prisoners-count" to it.itemsChecked.toString(),
            "pages-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) + it.mismatches.asMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_TRANSFER_SCHEDULER-report", mapOf("success" to "false"))
        log.error("Transfer scheduler all prisoners reconciliation report failed", it)
      }
  }

  private fun List<PrisonerId>.asMap(): Pair<String, String> = this
    .sortedBy { it.offenderNo }.take(10).let { mismatch -> "offenderNos" to mismatch.joinToString { it.offenderNo } }

  suspend fun generateTransferSchedulerReconciliationReport(): ReconciliationResult<PrisonerId> = generateReconciliationReport(
    threadCount = threadCount,
    pageSize = pageSize,
    checkMatch = ::checkPrisonersMatch,
    nextPage = ::getNextPrisonersForPage,
  )

  private suspend fun getNextPrisonersForPage(lastOffenderId: Long): ReconciliationPageResult<PrisonerId> = runCatching {
    nomisPrisonerApi.getAllPrisoners(fromId = lastOffenderId, pageSize = pageSize)
  }.onFailure {
    telemetryClient.trackEvent(
      "$TELEMETRY_TRANSFER_SCHEDULER-mismatch-page-error",
      mapOf(
        "offenderId" to lastOffenderId.toString(),
      ),
    )
    log.error("Unable to match entire page of offenders from offender ID: $lastOffenderId", it)
  }
    .map {
      ReconciliationSuccessPageResult(
        ids = it.prisonerIds,
        last = it.lastOffenderId,
      )
    }
    .getOrElse { ReconciliationErrorPageResult(it) }
    .also { log.info("Page requested from offender ID: $lastOffenderId, with $pageSize offenders") }

  suspend fun checkPrisonersMatch(prisonerId: PrisonerId): PrisonerId? = runCatching {
    checkPrisonersMatch(prisonerId.offenderNo).takeIf { it.isNotEmpty() }
      ?.let { prisonerId }
  }.onFailure {
    log.error("Unable to match transfers for prisoner ${prisonerId.offenderNo}", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_TRANSFER_SCHEDULER-mismatch-error",
      mapOf(
        "offenderNo" to prisonerId.offenderNo,
        "reason" to (it.message ?: "unknown"),
      ),
    )
  }.getOrNull()

  suspend fun checkPrisonersMatch(offenderNo: String): List<MismatchedPrisoner> = withContext(Dispatchers.Unconfined) {
    val nomisTransfers = async { nomisApi.getOffenderTransferMovementsOrNull(offenderNo) }
    val dpsTransfers = async { dpsApi.getTransferSchedulerReconciliation(offenderNo) }
    val mappings = async { mappingApi.getMappings(offenderNo) }

    nomisTransfers.await()
    dpsTransfers.await()
    mappings.await()

    listOf()
  }
}

class MismatchedPrisoner(
  val offenderNo: String,
  val type: String,
)
