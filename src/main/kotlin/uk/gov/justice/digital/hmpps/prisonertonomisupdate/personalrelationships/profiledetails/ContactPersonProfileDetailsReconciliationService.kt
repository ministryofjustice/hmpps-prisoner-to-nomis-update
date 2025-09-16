package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncPrisonerDomesticStatusResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncPrisonerNumberOfChildrenResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails.ContactPersonProfileType.CHILD
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails.ContactPersonProfileType.MARITAL
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.profiledetails.ProfileDetailsNomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.doApiCallWithRetries
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.ceil

typealias Mismatches = List<String>

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalAtomicApi::class)
@Service
class ContactPersonProfileDetailsReconciliationService(
  @Autowired private val nomisIdsApi: NomisApiService,
  @Autowired private val nomisApi: ProfileDetailsNomisApiService,
  @Autowired private val dpsApi: ContactPersonProfileDetailsDpsApiService,
  @Value("\${reports.contact-person.profile-details.reconciliation.page-size:20}") private val pageSize: Long = 20,
  @Value("\${reports.contact-person.profile-details.reconciliation.parallel-jobs:20}") private val parallelJobs: Long = 20,
  @Value("\${feature.recon.contact-person.profile-details:true}") private val reconciliationTurnedOn: Boolean = true,
  @Autowired private val telemetryClient: TelemetryClient,
  @Autowired(required = false) private val channelActivityDebugger: ContactPersonProfileDetailsChannelActivityDebugger? = null,
) {
  companion object {
    const val TELEMETRY_PREFIX = "contact-person-profile-details-reconciliation"
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
  suspend fun reconciliationReport() {
    val activePrisonersCount = nomisIdsApi.getActivePrisoners(0, 1).totalElements

    telemetryClient.trackEvent(
      "$TELEMETRY_PREFIX-report-requested",
      mapOf("active-prisoners" to activePrisonersCount.toString()),
    )

    runCatching { generateReconciliationReport(activePrisonersCount) }
      .onSuccess {
        telemetryClient.trackEvent(
          "$TELEMETRY_PREFIX-report-${if (it.isEmpty()) "success" else "failed"}",
          mapOf(
            "active-prisoners" to activePrisonersCount.toString(),
            "mismatch-count" to it.size.toString(),
            "mismatch-prisoners" to it.toString(),
          ),
        )
      }
      .onFailure { e ->
        telemetryClient.trackEvent("$TELEMETRY_PREFIX-report-error", mapOf("error" to "${e.message}"))
        log.error("Failed to generate contact person profile details reconciliation report", e)
      }
  }

  suspend fun generateReconciliationReport(activePrisonersCount: Long): Mismatches = if (reconciliationTurnedOn) {
    coroutineScope {
      val idProducer = producePrisonerIds(activePrisonersCount)
      val mismatchConsumer = Channel<String>(capacity = UNLIMITED)
      (1..parallelJobs)
        .map { launchCheckPrisoners(idProducer, mismatchConsumer) }
        .let { jobs -> mismatchConsumer.waitForMismatches(jobs) }
    }
  } else {
    emptyList()
  }

  private suspend fun Channel<String>.waitForMismatches(jobs: List<Job>): Mismatches = run {
    jobs.joinAll()
    close().let { toList() }
  }

  fun CoroutineScope.producePrisonerIds(activePrisonersCount: Long) = produce<PrisonerIds>(capacity = pageSize.toInt() * 2) {
    val pages = ceil(activePrisonersCount * 1.0 / pageSize).toLong()
    (0..pages - 1)
      .forEach { page ->
        getActivePrisonersForPage(page)
          .forEach { id -> send(id).also { channelActivityDebugger?.addId() } }
      }
      .also { channel.close() }
  }

  private suspend fun getActivePrisonersForPage(page: Long) = runCatching {
    nomisIdsApi.getActivePrisoners(page, pageSize).content
  }.onFailure {
    log.error("Failed to retrieve active prisoners for page $page", it)
    telemetryClient.trackEvent("$TELEMETRY_PREFIX-page-error", mapOf("page" to page.toString(), "error" to (it.message ?: "unknown error")))
  }
    .getOrElse { emptyList() }

  suspend fun CoroutineScope.launchCheckPrisoners(
    ids: ReceiveChannel<PrisonerIds>,
    mismatches: Channel<String>,
  ) = launch {
    for (id in ids) {
      if (ids.isEmpty) {
        log.info("The contact person profile details reconciliation prisoner ids channel is empty - indicates possible sub-optimal performance.")
      }
      channelActivityDebugger?.run {
        removeId()
        addCheck()
      }
      checkPrisoner(id.offenderNo)
        .also { channelActivityDebugger?.removeCheck() }
        ?.also { mismatches.send(it) }
    }
  }

  suspend fun checkPrisoner(prisonerNumber: String): String? = runCatching {
    val apiResponses = withContext(Dispatchers.Unconfined) {
      ApiResponses(
        async { doApiCallWithRetries { nomisApi.getProfileDetails(prisonerNumber, ContactPersonProfileType.all(), latestBookingOnly = true) } },
        async { doApiCallWithRetries { dpsApi.getDomesticStatus(prisonerNumber) } },
        async { doApiCallWithRetries { dpsApi.getNumberOfChildren(prisonerNumber) } },
      )
    }

    findDifferences(apiResponses)
      ?.let { differences ->
        prisonerNumber
          .also {
            telemetryClient.trackEvent(
              "$TELEMETRY_PREFIX-prisoner-failed",
              mapOf("offenderNo" to it, "differences" to differences.joinToString()),
              null,
            )
          }
      }
  }.onFailure { e ->
    log.error("Failed to run reconciliation for prisoner $prisonerNumber", e)
    telemetryClient.trackEvent(
      "$TELEMETRY_PREFIX-prisoner-error",
      mapOf("offenderNo" to prisonerNumber, "error" to "${e.message}"),
      null,
    )
  }.getOrNull()

  private fun findDifferences(apiResponses: ApiResponses): List<String>? {
    val differences = mutableListOf<String>()

    val nomisDomesticStatus = apiResponses.nomisProfileDetails?.findDomesticStatusCode(MARITAL.name)
    val dpsDomesticStatus = apiResponses.dpsDomesticStatus?.domesticStatusCode
    if (nomisDomesticStatus != dpsDomesticStatus) {
      differences += MARITAL.identifier + checkForNull(nomisDomesticStatus, dpsDomesticStatus)
    }

    val nomisNumberOfChildren = apiResponses.nomisProfileDetails?.findDomesticStatusCode(CHILD.name)
    val dpsNumberOfChildren = apiResponses.dpsNumberOfChildren?.numberOfChildren
    if (nomisNumberOfChildren != dpsNumberOfChildren) {
      differences += CHILD.identifier + checkForNull(nomisNumberOfChildren, dpsNumberOfChildren)
    }

    return differences.takeIf { it.isNotEmpty() }
  }

  private fun checkForNull(nomis: String?, dps: String?): String = when {
    nomis == null -> "-null-nomis"
    dps == null -> "-null-dps"
    else -> ""
  }

  private data class ApiResponses(
    val nomisProfileDetails: PrisonerProfileDetailsResponse?,
    val dpsDomesticStatus: SyncPrisonerDomesticStatusResponse?,
    val dpsNumberOfChildren: SyncPrisonerNumberOfChildrenResponse?,
  ) {
    companion object {
      suspend operator fun invoke(
        nomisProfileDetails: Deferred<PrisonerProfileDetailsResponse?>,
        dpsDomesticStatus: Deferred<SyncPrisonerDomesticStatusResponse?>,
        dpsNumberOfChildren: Deferred<SyncPrisonerNumberOfChildrenResponse?>,
      ) = ApiResponses(nomisProfileDetails.await(), dpsDomesticStatus.await(), dpsNumberOfChildren.await())
    }
  }

  private fun PrisonerProfileDetailsResponse.findDomesticStatusCode(profileType: String): String? = bookings.firstOrNull { it.latestBooking }
    ?.profileDetails
    ?.firstOrNull { it.type == profileType }
    ?.code
}

/**
 * A utility for debugging the channel size and number of concurrent prisoner checks when running tests. This should help
 * when trying to work out the page size and number of parallel prisoner check jobs. The goal is to prevent the channel
 * from becoming empty and keep the number of concurrent prisoner checks close to the number of parallel jobs.
 *
 * To use this when running locally:
 * - set the fixed delay on the NOMIS API stubs to realistic values (remember that response times differ with page size)
 * - in application-test.yml set reports.contact-person.profile-details.reconciliation.debug = true
 * - in application-test.yml set page-size and parallel-jobs to the values you want to test
 * - run the Happy Path integration test with a high active prisoner count
 *
 * If having problems in a real environment you could turn on "debug" in the env vars. However, you should be aware that
 * this will log 4 times for every prisoner that is checked - approx. 90,000 active prisoner * 4 ~= 360,000 extra logs.
 */
@Service
@ConditionalOnProperty("reports.contact-person.profile-details.reconciliation.debug", havingValue = "true")
@OptIn(ExperimentalAtomicApi::class)
class ContactPersonProfileDetailsChannelActivityDebugger(
  @Value("\${reports.contact-person.profile-details.reconciliation.page-size}") private val pageSize: Long,
  @Value("\${reports.contact-person.profile-details.reconciliation.parallel-jobs}") private val parallelJobs: Long,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    var checkPrisonerCounter: AtomicInt = AtomicInt(0)
    var idsInChannel: AtomicInt = AtomicInt(0)
  }

  init {
    log.debug("page-size=$pageSize, parallel-jobs=$parallelJobs")
  }

  fun addId() = idsInChannel.addAndFetch(1)
  fun removeId() = idsInChannel.addAndFetch(-1).also { logId(it) }
  private fun logId(count: Int) = log.debug("ids in channel=$count")

  fun addCheck() = checkPrisonerCounter.addAndFetch(1).also { logCheck(it) }
  fun removeCheck() = checkPrisonerCounter.addAndFetch(-1).also { logCheck(it) }
  private fun logCheck(count: Int) = log.debug("concurrent prisoner checks=$count")
}
