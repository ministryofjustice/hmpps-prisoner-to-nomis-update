package uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * The result of a reconciliation report.
 *
 * @param mismatches list of mismatches found during the reconciliation
 * @param itemsChecked the number of items checked during the reconciliation
 * @param pagesChecked the number of pages checked during the reconciliation
 *
 * @param M the type of the mismatch
 *
 * @see ReconciliationResult.mismatches
 */
data class ReconciliationResult<M>(
  val mismatches: List<M>,
  val itemsChecked: Int,
  val pagesChecked: Int,
)

/**
 * Generates a reconciliation report by retrieving pages of IDs as specified by the [nextPage] function.
 * Each ID is passed to the [checkMatch] function and any mismatches are returned in the final report.
 *
 * @param threadCount number of threads to use to process items in parallel. For example, if set to 10 than 10 coroutine jobs will be used to process [checkMatch] function in parallel.
 * @param pageSize number items to retrieve per page. Used to also control back pressure for the [Channel]
 * @param checkMatch function takes an item ID to check and returns an optional mismatch response. Null indicates no mismatch.
 * @param nextPage function takes the last ID retrieved from the previous page and returns the next page of IDs to retrieve. The last ID class must be of type Long
 *
 * @param T the type of the item ID
 * @param M the type of the mismatch
 *
 */
suspend fun <T, M> generateReconciliationReport(
  threadCount: Int,
  pageSize: Int = threadCount,
  checkMatch: suspend (T) -> M?,
  nextPage: suspend (Long) -> ReconciliationPageResult<T>,
): ReconciliationResult<M> = coroutineScope {
  val itemsCount = AtomicInteger(0)
  val pagesCount = AtomicInteger(0)

  val mismatchesChannel = Channel<M>(capacity = UNLIMITED)
  val channel = produceIds(pagesCount = pagesCount, pageSize = pageSize) { last ->
    nextPage(last)
  }

  val jobs = (1L..threadCount).map {
    launch {
      for (item in channel) {
        checkMatch(item)?.also { mismatchesChannel.send(it) }
        itemsCount.incrementAndGet()
      }
    }
  }
  launch {
    // when all jobs finished (there are no more items to process), we can shut down the mismatch channel and return the results
    jobs.forEach { it.join() }
    mismatchesChannel.close()
  }

  ReconciliationResult(
    mismatches = mismatchesChannel.toList(),
    itemsChecked = itemsCount.get(),
    pagesChecked = pagesCount.get(),
  )
}

sealed class ReconciliationPageResult<T>
class ReconciliationSuccessPageResult<T>(val ids: List<T>, val last: Long) : ReconciliationPageResult<T>()
class ReconciliationErrorPageResult<T>(val error: Throwable) : ReconciliationPageResult<T>()

// The last page will be a non-null page with items less than page size
private fun <T> ReconciliationPageResult<T>.notLastPage(pageSize: Int): Boolean = when (this) {
  is ReconciliationSuccessPageResult -> ids.size == pageSize
  is ReconciliationErrorPageResult -> true
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> CoroutineScope.produceIds(pagesCount: AtomicInteger, pageSize: Int, nextPage: suspend (Long) -> ReconciliationPageResult<T>) = produce(capacity = pageSize * 2) {
  var lastId: Long = 0
  var pageErrorCount = 0L

  do {
    val result = nextPage(lastId)

    when (result) {
      is ReconciliationSuccessPageResult -> {
        if (result.ids.isNotEmpty()) {
          pagesCount.incrementAndGet()
          result.ids.forEach {
            send(it)
          }
          lastId = result.last
        }
      }

      is ReconciliationErrorPageResult -> {
        // skip this "page" by moving the bookingId pointer up
        lastId += pageSize
        pageErrorCount++
      }
    }
  } while (result.notLastPage(pageSize) && notManyPageErrors(pageErrorCount))

  // no more prisoner ids so signal a close of the channel
  channel.close()
}

private fun notManyPageErrors(errors: Long): Boolean = errors < 30
