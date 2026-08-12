package uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.RootOffenderIdRange
import java.util.concurrent.atomic.AtomicInteger

/**
 * Generates a reconciliation report by retrieving pages of IDs as specified by the [idsInRange] function.
 * Each ID is passed to the [checkMatch] function and any mismatches are returned in the final report.
 *
 * @param threadCount number of threads to use to process items in parallel. For example, if set to 10 than 10 coroutine jobs will be used to process [checkMatch] function in parallel.
 * @param checkMatch function takes an item ID to check and returns an optional mismatch response. Null indicates no mismatch.
 * @param idRanges list of from and to items to loop over
 * @param idsInRange function takes the last ID retrieved from the previous page and returns the next page of IDs to retrieve. The last ID class must be of type Long
 *
 * @param T the type of the item ID
 * @param M the type of the mismatch
 *
 */
suspend fun <T, M> generateRangesReconciliationReport(
  threadCount: Int,
  checkMatch: suspend (T) -> M?,
  idRanges: List<RootOffenderIdRange>,
  idsInRange: suspend (RootOffenderIdRange) -> ReconciliationPageResult<T>,
): ReconciliationResult<M> = coroutineScope {
  val itemsCount = AtomicInteger(0)
  val pagesCount = AtomicInteger(0)

  val mismatchesChannel = Channel<M>(capacity = UNLIMITED)
  val channel = produceIds(idRanges, pagesCount = pagesCount, threadCount = threadCount) { idRange -> idsInRange(idRange) }

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
    jobs.joinAll()
    mismatchesChannel.close()
  }

  ReconciliationResult(
    mismatches = mismatchesChannel.toList(),
    itemsChecked = itemsCount.get(),
    pagesChecked = pagesCount.get(),
  )
}

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> CoroutineScope.produceIds(idRanges: List<RootOffenderIdRange>, pagesCount: AtomicInteger, threadCount: Int, idsInRange: suspend (RootOffenderIdRange) -> ReconciliationPageResult<T>) = produce(capacity = threadCount * 2) {
  var pageErrorCount = 0L
  for (idRange in idRanges) {
    when (val result = idsInRange(idRange)) {
      is ReconciliationSuccessPageResult -> {
        if (result.ids.isNotEmpty()) {
          pagesCount.incrementAndGet()
          result.ids.forEach {
            send(it)
          }
        }
      }

      is ReconciliationErrorPageResult -> {
        pageErrorCount++
      }
    }
    if (tooManyPageErrors(pageErrorCount)) {
      break
    }
  }
  // no more prisoner ids so signal a close of the channel
  channel.close()
}

private fun tooManyPageErrors(errors: Long): Boolean = errors >= 30
