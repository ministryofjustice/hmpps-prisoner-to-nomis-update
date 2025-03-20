package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncPrisonerDomesticStatusResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncPrisonerNumberOfChildrenResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails.ContactPersonProfileType.CHILD
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails.ContactPersonProfileType.MARITAL
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.profiledetails.ProfileDetailsNomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.asPages
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.doApiCallWithRetries

@Service
class ContactPersonProfileDetailsReconciliationService(
  @Autowired private val nomisIdsApi: NomisApiService,
  @Autowired private val nomisApi: ProfileDetailsNomisApiService,
  @Autowired private val dpsApi: ContactPersonProfileDetailsDpsApiService,
  @Value("\${reports.contact-person.profile-details.reconciliation.page-size:20}") private val pageSize: Long = 20,
  @Value("\${feature.recon.contact-person.profile-details:true}") private val reconciliationTurnedOn: Boolean = true,
  @Autowired private val telemetryClient: TelemetryClient,
) {
  companion object {
    const val TELEMETRY_PREFIX = "contact-person-profile-details-reconciliation"
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateReconciliationReport(activePrisonersCount: Long): List<String> = if (reconciliationTurnedOn) {
    activePrisonersCount
      .asPages(pageSize)
      .flatMap { page ->
        val activePrisoners = getActivePrisonersForPage(page)

        withContext(Dispatchers.Unconfined) {
          activePrisoners.map { async { checkPrisoner(it.offenderNo) } }
        }.awaitAll().filterNotNull()
      }
  } else {
    emptyList()
  }

  private suspend fun getActivePrisonersForPage(page: Pair<Long, Long>) = runCatching { nomisIdsApi.getActivePrisoners(page.first, page.second).content }
    .onFailure {
      log.error("Failed to retrieve active prisoners for page $page", it)
      telemetryClient.trackEvent("$TELEMETRY_PREFIX-page-error", mapOf("page" to page.first.toString(), "error" to (it.message ?: "unknown error")))
    }
    .getOrElse { emptyList() }

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
