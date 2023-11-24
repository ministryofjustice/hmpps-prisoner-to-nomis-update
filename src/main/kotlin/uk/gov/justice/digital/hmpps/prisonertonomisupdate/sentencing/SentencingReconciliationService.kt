package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.ActivePrisonerId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth

@Service
class SentencingReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: NomisApiService,
  private val adjustmentsApiService: SentencingAdjustmentsApiService,

) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun checkBookingIncentiveMatch(prisonerId: ActivePrisonerId): MismatchSentencingAdjustments? = runCatching {
    val (nomisAdjustments, dpsAdjustments) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getAdjustments(prisonerId.bookingId) } to
        async { adjustmentsApiService.getAdjustments(prisonerId.offenderNo) }
    }.awaitBoth()

    log.debug("will check adjustments for prisoner with ${prisonerId.offenderNo} booking: ${prisonerId.bookingId} : DPS has adjustments ${dpsAdjustments != null}")
    return null
  }.onFailure {
    log.error("Unable to match adjustments for prisoner with ${prisonerId.offenderNo} booking: ${prisonerId.bookingId}", it)
    telemetryClient.trackEvent(
      "sentencing-reports-reconciliation-mismatch-error",
      mapOf(
        "offenderNo" to prisonerId.offenderNo,
        "bookingId" to prisonerId.bookingId.toString(),
      ),
    )
  }.getOrNull()
}

data class MismatchSentencingAdjustments(val prisonerId: ActivePrisonerId)
