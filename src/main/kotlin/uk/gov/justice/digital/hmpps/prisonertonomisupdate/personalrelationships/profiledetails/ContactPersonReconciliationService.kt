package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerProfileDetailsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncPrisonerDomesticStatusResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncPrisonerNumberOfChildrenResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails.ContactPersonProfileType.CHILD
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails.ContactPersonProfileType.MARITAL
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.profiledetails.ProfileDetailsNomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.doApiCallWithRetries

@Service
class ContactPersonReconciliationService(
  @Autowired private val nomisApi: ProfileDetailsNomisApiService,
  @Autowired private val dpsApi: ContactPersonProfileDetailsDpsApiService,
  @Autowired private val telemetryClient: TelemetryClient,
) {

  suspend fun checkPrisoner(prisonerNumber: String): String? = runCatching {
    val apiResponses = withContext(Dispatchers.Unconfined) {
      ApiResponses(
        async { doApiCallWithRetries { nomisApi.getProfileDetails(prisonerNumber, ContactPersonProfileType.all()) } },
        async { doApiCallWithRetries { dpsApi.getDomesticStatus(prisonerNumber) } },
        async { doApiCallWithRetries { dpsApi.getNumberOfChildren(prisonerNumber) } },
      )
    }

    findDifferences(apiResponses)
      ?.let { differences ->
        prisonerNumber
          .also {
            telemetryClient.trackEvent(
              "contact-person-profile-details-reconciliation-prisoner-failed",
              mapOf("offenderNo" to it, "differences" to differences.joinToString()),
              null,
            )
          }
      }
  }.onFailure { e ->
    telemetryClient.trackEvent(
      "contact-person-profile-details-reconciliation-prisoner-error",
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

  private fun PrisonerProfileDetailsResponse.findDomesticStatusCode(profileType: String): String? = bookings[0].profileDetails.firstOrNull { it.type == profileType }?.code
}
