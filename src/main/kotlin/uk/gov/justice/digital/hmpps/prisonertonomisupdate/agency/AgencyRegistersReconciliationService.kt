package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.logger
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@Service
class AgencyRegistersReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val dpsApiService: AgencyRegistersDpsApiService,
  private val nomisApiService: AgencyNomisApiService,
) {
  internal companion object {
    val log: Logger = logger()
    const val TELEMETRY_PREFIX = "agency-reconciliation"
  }

  suspend fun generateAgencyReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_PREFIX-requested",
      mapOf(),
    )

    runCatching { generateAgencyReconciliationReport() }
      .onSuccess {
        log.info("Agency report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_PREFIX-report",
          mapOf(
            "agency-count" to it.itemsChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) + it.mismatches.asMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_PREFIX-report", mapOf("success" to "false"))
        log.error("Agency report failed", it)
      }
  }

  suspend fun checkMatch(agencyId: String): MismatchAgency? {
    val nomisAgency = runCatching { nomisApiService.getAgency(agencyId) }.getOrNull()
    val dpsAgency = runCatching { dpsApiService.getAgency(agencyId) }.getOrNull()

    return when {
      nomisAgency == null -> MismatchAgency(agencyId, "Missing in NOMIS")
      dpsAgency == null -> MismatchAgency(agencyId, "Missing in DPS")
      else -> null
    }?.also {
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-mismatch",
        mapOf(
          "agencyId" to it.agencyId,
          "reason" to it.reason,
        ),
      )
    }
  }

  suspend fun generateAgencyReconciliationReport(): ReconciliationResult<MismatchAgency> {
    val (nomisAgencyIds, dpsAgencyIds) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getAgencyIds().agencyIds.map { it.agencyId } } to
        async { dpsApiService.getAgencyIds().agencyIds.map { it.agencyId } }
    }.awaitBoth()

    val allIds = (nomisAgencyIds + dpsAgencyIds).toSet()

    return coroutineScope {
      val itemsCount = AtomicInteger(0)
      val pagesCount = AtomicInteger(0)

      val mismatchesChannel = Channel<MismatchAgency>(capacity = UNLIMITED)
      val channel = produce {
        allIds.forEach { send(it) }
        channel.close()
      }

      val jobs = (1L..10).map {
        launch {
          for (item in channel) {
            checkMatch(item)?.also { mismatchesChannel.send(it) }
            itemsCount.incrementAndGet()
          }
        }
      }

      launch {
        jobs.joinAll()
        mismatchesChannel.close()
      }

      ReconciliationResult(
        mismatches = mismatchesChannel.toList().also { log.info("Mismatch result: $it") },
        itemsChecked = itemsCount.get(),
        pagesChecked = pagesCount.get(),
      )
    }
  }
}
data class MismatchAgency(
  val agencyId: String,
  val reason: String,
)

private fun List<MismatchAgency>.asMap(): Pair<String, String> = this
  .sortedBy { it.agencyId }.take(10).let { mismatch -> "agencyIds" to mismatch.joinToString { it.agencyId } }
