package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorporateOrganisation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.OrganisationDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.asPages
import java.util.SortedSet

@Service
class OrganisationsReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val dpsApiService: OrganisationsDpsApiService,
  private val nomisApiService: OrganisationsNomisApiService,
  @Value("\${reports.alerts.reconciliation.page-size}")
  private val pageSize: Long = 20,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun getOrganisationsCounts(): OrganisationCounts = OrganisationCounts(
    dpsCount = dpsApiService.getOrganisationIds(0, 1).totalElements!!,
    nomisCount = nomisApiService.getCorporateOrganisationIds(0, 1).totalElements,
  )

  suspend fun generateReconciliationReport(organisationsCount: Long): List<MismatchOrganisation> = organisationsCount.asPages(pageSize).flatMap { page ->
    val organisations = getOrganisationsForPage(page)

    withContext(Dispatchers.Unconfined) {
      organisations.map { async { checkOrganisationMatch(it.corporateId) } }
    }.awaitAll().filterNotNull()
  }

  private suspend fun getOrganisationsForPage(page: Pair<Long, Long>) = runCatching { nomisApiService.getCorporateOrganisationIds(page.first, page.second).content }
    .onFailure {
      telemetryClient.trackEvent(
        "organisations-reports-reconciliation-mismatch-page-error",
        mapOf(
          "page" to page.first.toString(),
        ),
      )
      log.error("Unable to match entire page of organisations: $page", it)
    }
    .getOrElse { emptyList() }
    .also { log.info("Page requested: $page, with ${it.size} organisations") }

  suspend fun checkOrganisationMatch(corporateAndOrganisationId: Long): MismatchOrganisation? {
    val dpsOrganisation = dpsApiService.getOrganisation(corporateAndOrganisationId)?.toOrganisation()
    val nomisOrganisation = nomisApiService.getCorporateOrganisation(corporateAndOrganisationId).toOrganisation()
    if (nomisOrganisation != dpsOrganisation) {
      return MismatchOrganisation(organisationId = corporateAndOrganisationId).also {
        telemetryClient.trackEvent(
          "organisations-reports-reconciliation-mismatch",
          mapOf(
            "organisationId" to "$corporateAndOrganisationId",
            "dpsOrganisation" to dpsOrganisation.toString(),
            "nomisOrganisation" to nomisOrganisation.toString(),
          ),
        )
      }
    }
    return null
  }
}

private fun OrganisationDetails.toOrganisation() = Organisation(
  id = this.organisationId,
  name = this.organisationName,
  active = this.active,
  addressCount = this.addresses.size,
  types = this.organisationTypes.map { it.organisationType }.toSortedSet(),
  phoneNumbers = this.phoneNumbers.map { it.phoneNumber }.toSortedSet(),
  emailAddresses = this.emailAddresses.map { it.emailAddress }.toSortedSet(),
  webAddresses = this.webAddresses.map { it.webAddress }.toSortedSet(),
  addressPhoneNumbers = this.addresses.map { it.phoneNumbers.map { it.phoneNumber }.toSortedSet() }.toSortedSet { o1, o2 -> o1.joinToString().compareTo(o2.joinToString()) },
)

private fun CorporateOrganisation.toOrganisation() = Organisation(
  id = this.id,
  name = this.name,
  active = this.active,
  addressCount = this.addresses.size,
  types = this.types.map { it.type.code }.toSortedSet(),
  phoneNumbers = this.phoneNumbers.map { it.number }.toSortedSet(),
  emailAddresses = this.internetAddresses.filter { it.type == "EMAIL" }.map { it.internetAddress }.toSortedSet(),
  webAddresses = this.internetAddresses.filter { it.type == "WEB" }.map { it.internetAddress }.toSortedSet(),
  addressPhoneNumbers = this.addresses.map { it.phoneNumbers.map { it.number }.toSortedSet() }.toSortedSet { o1, o2 -> o1.joinToString().compareTo(o2.joinToString()) },
)

data class MismatchOrganisation(
  val organisationId: Long,
)

data class Organisation(
  val id: Long,
  val name: String,
  val active: Boolean,
  val addressCount: Int,
  val types: SortedSet<String>,
  val phoneNumbers: SortedSet<String>,
  val emailAddresses: SortedSet<String>,
  val webAddresses: SortedSet<String>,
  val addressPhoneNumbers: SortedSet<SortedSet<String>>,
)

data class OrganisationCounts(val dpsCount: Long, val nomisCount: Long)
