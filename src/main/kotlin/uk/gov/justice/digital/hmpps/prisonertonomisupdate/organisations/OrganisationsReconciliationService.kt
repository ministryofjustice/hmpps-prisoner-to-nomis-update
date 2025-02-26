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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CorporateOrganisationIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.asPages

@Service
class OrganisationReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val dpsApiService: OrganisationsDpsApiService,
  private val nomisApiService: OrganisationsNomisApiService,
  @Value("\${reports.alerts.reconciliation.page-size}")
  private val pageSize: Long = 20,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateReconciliationReport(organisationsCount: Long): List<MismatchOrganisation> = organisationsCount.asPages(pageSize).flatMap { page ->
    val organisations = getOrganisationsForPage(page)

    withContext(Dispatchers.Unconfined) {
      organisations.map { async { checkOrganisationMatch(it) } }
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

  suspend fun checkOrganisationMatch(organisationId: CorporateOrganisationIdResponse): MismatchOrganisation? = null
}

data class MismatchOrganisation(
  val organisationId: Long,
)
