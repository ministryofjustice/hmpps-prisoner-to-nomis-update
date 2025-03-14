package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.model.AdjustmentDto.AdjustmentType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.doApiCallWithRetries

@Service
class SentencingReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: NomisApiService,
  private val adjustmentsApiService: SentencingAdjustmentsApiService,
  @Value("\${reports.sentencing.reconciliation.page-size}")
  private val pageSize: Long = 20,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    val taggedBailAdjustmentTypes = listOf("S240A", "RST")
    val remandAdjustmentTypes = listOf("RSR", "RX")
  }

  suspend fun generateReconciliationReport(allPrisoners: Boolean): List<MismatchSentencingAdjustments> {
    val mismatches: MutableList<MismatchSentencingAdjustments> = mutableListOf()
    var lastBookingId = 0L
    var pageErrorCount = 0L

    do {
      val result = getNextBookingsForPage(lastBookingId, allPrisoners)

      when (result) {
        is SuccessPageResult -> {
          if (result.value.prisonerIds.isNotEmpty()) {
            result.value.checkPageOfBookingAdjustmentsMatch().also {
              mismatches += it.mismatches
              lastBookingId = it.lastBookingId
            }
          }
        }

        is ErrorPageResult -> {
          // just skip this "page" by moving the bookingId pointer up
          lastBookingId += pageSize.toInt()
          pageErrorCount++
        }
      }
    } while (result.notLastPage() && pageErrorCount.notManyPageErrors())

    return mismatches.toList()
  }

  private suspend fun BookingIdsWithLast.checkPageOfBookingAdjustmentsMatch(): MismatchPageResult {
    val prisonerIds = this.prisonerIds
    val mismatches = withContext(Dispatchers.Unconfined) {
      prisonerIds.map { async { checkBookingAdjustmentsMatch(it) } }
    }.awaitAll().filterNotNull()
    return MismatchPageResult(mismatches, prisonerIds.last().bookingId)
  }

  private fun Long.notManyPageErrors(): Boolean = this < 30

  data class MismatchPageResult(val mismatches: List<MismatchSentencingAdjustments>, val lastBookingId: Long)

  private suspend fun getNextBookingsForPage(lastBookingId: Long, allPrisoners: Boolean): PageResult = runCatching { nomisApiService.getAllLatestBookings(lastBookingId = lastBookingId, activeOnly = !allPrisoners, pageSize = pageSize.toInt()) }
    .onFailure {
      telemetryClient.trackEvent(
        "sentencing-reports-reconciliation-mismatch-page-error",
        mapOf(
          "booking" to lastBookingId.toString(),
        ),
      )
      log.error("Unable to match entire page of bookings from booking: $lastBookingId", it)
    }
    .map { SuccessPageResult(it) }
    .getOrElse { ErrorPageResult(it) }
    .also { log.info("Page requested from booking: $lastBookingId, with $pageSize bookings") }

  suspend fun checkBookingAdjustmentsMatch(prisonerId: PrisonerIds): MismatchSentencingAdjustments? = runCatching {
    val (nomisAdjustments, dpsAdjustments) = withContext(Dispatchers.Unconfined) {
      async { doApiCallWithRetries { nomisApiService.getAdjustments(prisonerId.bookingId) } } to
        async {
          doApiCallWithRetries { adjustmentsApiService.getAdjustments(prisonerId.offenderNo) }
            // temporary fix until DPS filter out old adjustments
            ?.filter { it.bookingId == prisonerId.bookingId }
            // ignore adjustments that are zero days since the status can differ
            ?.filter { it.effectiveDays != 0 }
        }
    }.awaitBoth()

    val nomisCounts: AdjustmentCounts = nomisAdjustments.let { adjustment ->
      val keyDateAdjustments = adjustment.keyDateAdjustments.filter { it.adjustmentDays != 0L }
      AdjustmentCounts(
        lawfullyAtLarge = keyDateAdjustments.count { it.adjustmentType.code == "LAL" },
        unlawfullyAtLarge = keyDateAdjustments.count { it.adjustmentType.code == "UAL" },
        restorationOfAdditionalDaysAwarded = keyDateAdjustments.count { it.adjustmentType.code == "RADA" },
        additionalDaysAwarded = keyDateAdjustments.count { it.adjustmentType.code == "ADA" },
        specialRemission = keyDateAdjustments.count { it.adjustmentType.code == "SREM" },
      )
    }.let { counts ->
      val sentenceAdjustments = nomisAdjustments.sentenceAdjustments.filter { it.adjustmentDays != 0L }
      counts.copy(
        remand = sentenceAdjustments.count { it.adjustmentType.code in remandAdjustmentTypes },
        unusedDeductions = sentenceAdjustments.count { it.adjustmentType.code == "UR" },
        taggedBail = sentenceAdjustments.count { it.adjustmentType.code in taggedBailAdjustmentTypes },
      )
    }

    val dpsCounts: AdjustmentCounts = dpsAdjustments?.let { adjustments ->
      AdjustmentCounts(
        lawfullyAtLarge = adjustments.count { it.adjustmentType == AdjustmentType.LAWFULLY_AT_LARGE },
        unlawfullyAtLarge = adjustments.count { it.adjustmentType == AdjustmentType.UNLAWFULLY_AT_LARGE },
        restorationOfAdditionalDaysAwarded = adjustments.count { it.adjustmentType == AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED },
        additionalDaysAwarded = adjustments.count { it.adjustmentType == AdjustmentType.ADDITIONAL_DAYS_AWARDED },
        specialRemission = adjustments.count { it.adjustmentType == AdjustmentType.SPECIAL_REMISSION },
        remand = dpsAdjustments.count { it.adjustmentType == AdjustmentType.REMAND },
        unusedDeductions = dpsAdjustments.count { it.adjustmentType == AdjustmentType.UNUSED_DEDUCTIONS },
        taggedBail = dpsAdjustments.count { it.adjustmentType == AdjustmentType.TAGGED_BAIL },
      )
    } ?: AdjustmentCounts()

    return if (nomisCounts == dpsCounts) {
      null
    } else {
      MismatchSentencingAdjustments(prisonerId, dpsCounts = dpsCounts, nomisCounts = nomisCounts).also { mismatch ->
        log.info("Sentencing Adjustments Mismatch found  $mismatch")
        telemetryClient.trackEvent(
          "sentencing-reports-reconciliation-mismatch",
          mapOf(
            "offenderNo" to mismatch.prisonerId.offenderNo,
            "bookingId" to mismatch.prisonerId.bookingId.toString(),
            "nomisAdjustmentsCount" to (mismatch.nomisCounts.total().toString()),
            "dpsAdjustmentsCount" to (mismatch.dpsCounts.total().toString()),
            "differences" to (mismatch.dpsCounts.differenceMessage(mismatch.nomisCounts)),
          ),
        )
      }
    }
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

  // Last page will be a non-null page with items less than page size
  private fun PageResult.notLastPage(): Boolean = when (this) {
    is SuccessPageResult -> this.value.prisonerIds.size == pageSize.toInt()
    is ErrorPageResult -> true
  }

  sealed class PageResult
  class SuccessPageResult(val value: BookingIdsWithLast) : PageResult()
  class ErrorPageResult(val error: Throwable) : PageResult()
}

data class MismatchSentencingAdjustments(
  val prisonerId: PrisonerIds,
  val dpsCounts: AdjustmentCounts,
  val nomisCounts: AdjustmentCounts,
)

data class AdjustmentCounts(
  val lawfullyAtLarge: Int = 0,
  val unlawfullyAtLarge: Int = 0,
  val restorationOfAdditionalDaysAwarded: Int = 0,
  val additionalDaysAwarded: Int = 0,
  val specialRemission: Int = 0,
  val remand: Int = 0,
  val unusedDeductions: Int = 0,
  val taggedBail: Int = 0,
) {
  fun total(): Int = lawfullyAtLarge + unlawfullyAtLarge + restorationOfAdditionalDaysAwarded + additionalDaysAwarded + specialRemission + remand + unusedDeductions + taggedBail
  fun differenceMessage(other: AdjustmentCounts): String {
    val differences = mutableListOf<String>()
    if (lawfullyAtLarge != other.lawfullyAtLarge) differences.add("lawfullyAtLarge ${lawfullyAtLarge - other.lawfullyAtLarge}")
    if (unlawfullyAtLarge != other.unlawfullyAtLarge) differences.add("unlawfullyAtLarge ${unlawfullyAtLarge - other.unlawfullyAtLarge}")
    if (restorationOfAdditionalDaysAwarded != other.restorationOfAdditionalDaysAwarded) differences.add("restorationOfAdditionalDaysAwarded ${restorationOfAdditionalDaysAwarded - other.restorationOfAdditionalDaysAwarded}")
    if (additionalDaysAwarded != other.additionalDaysAwarded) differences.add("additionalDaysAwarded ${additionalDaysAwarded - other.additionalDaysAwarded}")
    if (specialRemission != other.specialRemission) differences.add("specialRemission ${specialRemission - other.specialRemission}")
    if (remand != other.remand) differences.add("remand ${remand - other.remand}")
    if (unusedDeductions != other.unusedDeductions) differences.add("unusedDeductions ${unusedDeductions - other.unusedDeductions}")
    if (taggedBail != other.taggedBail) differences.add("taggedBail ${taggedBail - other.taggedBail}")
    if (differences.isEmpty()) return "no differences"

    return differences.joinToString(", ")
  }
}
