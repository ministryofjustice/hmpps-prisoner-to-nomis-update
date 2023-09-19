package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.NonAssociationIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.asPages
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth

@Service
class NonAssociationsReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: NomisApiService,
  private val nonAssociationsApiService: NonAssociationsApiService,
  @Value("\${reports.non-associations.reconciliation.page-size}")
  private val pageSize: Long = 20,
) {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateReconciliationReport(nonAssociationsCount: Long): List<List<MismatchNonAssociation>> {
    val allNomisIds = mutableSetOf<NonAssociationIdResponse>()
    val results = nonAssociationsCount.asPages(pageSize).flatMap { page ->
      val nonAssociations = getNonAssociationsForPage(page)

      allNomisIds.addAll(nonAssociations)

      withContext(Dispatchers.Unconfined) {
        nonAssociations.map { async { checkMatch(it) } }
      }.awaitAll().filterNot { it.isEmpty() }
    }
    return results + listOf(checkForMissingDpsRecords(allNomisIds))
  }

  internal suspend fun checkForMissingDpsRecords(allNomisIds: Set<NonAssociationIdResponse>): List<MismatchNonAssociation> {
    return emptyList()
    // TODO: Re-enable this when we have a way to get all DPS records
    val allDpsIds = nonAssociationsApiService.getAllNonAssociations(0, 1).totalElements
    if (allDpsIds.toInt() != allNomisIds.size) {
      log.info("Total no of NAs does not match: DPS=$allDpsIds, Nomis=${allNomisIds.size}")
      telemetryClient.trackEvent(
        "non-associations-reports-reconciliation-mismatch",
        mapOf("missing-dps-records" to "true", "dps-total" to allDpsIds.toString(), "nomis-total" to allNomisIds.size.toString()),
      )
      return allDpsIds.asPages(1000).flatMap { page ->
        val dpsPage = nonAssociationsApiService.getAllNonAssociations(page.first, page.second).content
        dpsPage
          .filterNot {
            val dpsId = if (it.firstPrisonerNumber < it.secondPrisonerNumber) {
              NonAssociationIdResponse(it.firstPrisonerNumber, it.secondPrisonerNumber)
            } else {
              NonAssociationIdResponse(it.secondPrisonerNumber, it.firstPrisonerNumber)
            }
            allNomisIds.contains(dpsId)
          }
          .map {
            log.info("NonAssociation Mismatch found $it")
            telemetryClient.trackEvent(
              "non-associations-reports-reconciliation-mismatch",
              mapOf(
                "offenderNo1" to it.firstPrisonerNumber,
                "offenderNo2" to it.secondPrisonerNumber,
                "dps" to it.toString(),
              ),
            )

            MismatchNonAssociation(
              NonAssociationIdResponse(it.firstPrisonerNumber, it.secondPrisonerNumber),
              null,
              NonAssociationReportDetail("MISSING", "MISSING"),
            )
          }
      }
    }
    return emptyList()
  }

  internal suspend fun getNonAssociationsForPage(page: Pair<Long, Long>) =
    runCatching { nomisApiService.getNonAssociations(page.first, page.second).content }
      .onFailure {
        telemetryClient.trackEvent(
          "non-associations-reports-reconciliation-mismatch-page-error",
          mapOf("page" to page.first.toString()),
        )
        log.error("Unable to match entire page of prisoners: $page", it)
      }
      .getOrElse { emptyList() }
      .also { log.info("Page requested: $page, with ${it.size} non-associations") }

  internal suspend fun checkMatch(id: NonAssociationIdResponse): List<MismatchNonAssociation> = runCatching {
    // log.debug("Checking NA: ${id.offenderNo1}, ${id.offenderNo2}")
    val (nomisListUnsorted, dpsListUnsorted) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getNonAssociationDetails(id.offenderNo1, id.offenderNo2) } to
        async { nonAssociationsApiService.getNonAssociationsBetween(id.offenderNo1, id.offenderNo2) }
    }.awaitBoth()

    val nomisListSorted = nomisListUnsorted.sortedBy { it.effectiveDate }
    // Ignore old open records
    val closedPlusOpenLists = nomisListSorted.partition { it.expiryDate != null }
    val nomisList = closedPlusOpenLists.first + closedPlusOpenLists.second.takeLast(1)

    val dpsList = dpsListUnsorted.sortedBy { it.whenCreated }

    return if (nomisList.size > dpsList.size) {
      // log.info("Extra Nomis details found for ${id.offenderNo1}, ${id.offenderNo2}")
      (dpsList.size..<nomisList.size)
        .map { index ->
          MismatchNonAssociation(id, NonAssociationReportDetail(nomisList[index].type, nomisList[index].effectiveDate.toString()), null)
            .also { mismatch ->
              log.info("NonAssociation Mismatch found $mismatch")
              telemetryClient.trackEvent(
                "non-associations-reports-reconciliation-mismatch",
                mapOf(
                  "offenderNo1" to mismatch.id.offenderNo1,
                  "offenderNo2" to mismatch.id.offenderNo2,
                  "typeSequence" to nomisList[index].typeSequence.toString(),
                ),
              )
            }
        }
    } else if (nomisList.size < dpsList.size) {
      // log.info("Extra DPS details found for ${id.offenderNo1}, ${id.offenderNo2}")
      (nomisList.size..<dpsList.size)
        .map { index ->
          MismatchNonAssociation(id, null, NonAssociationReportDetail(dpsList[index].restrictionType.name, dpsList[index].whenCreated))
            .also { mismatch ->
              log.info("NonAssociation Mismatch found $mismatch")
              telemetryClient.trackEvent(
                "non-associations-reports-reconciliation-mismatch",
                mapOf(
                  "offenderNo1" to mismatch.id.offenderNo1,
                  "offenderNo2" to mismatch.id.offenderNo2,
                ),
              )
            }
        }
    } else {
      dpsList.indices
        .filter {
          val nomis = nomisList[it]
          val dps = dpsList[it]
          nomis.type != dps.restrictionType.name.take(4) || nomis.effectiveDate.toString() != dps.whenCreated.take(10)
        }
        .map {
          val mismatch =
            MismatchNonAssociation(
              id,
              NonAssociationReportDetail(nomisList[it].type, nomisList[it].effectiveDate.toString()),
              NonAssociationReportDetail(dpsList[it].restrictionType.name, dpsList[it].whenCreated),
            )
          log.info("NonAssociation Mismatch found $mismatch")
          telemetryClient.trackEvent(
            "non-associations-reports-reconciliation-mismatch",
            mapOf(
              "offenderNo1" to mismatch.id.offenderNo1,
              "offenderNo2" to mismatch.id.offenderNo2,
              "nomis" to (mismatch.nomisNonAssociation?.toString() ?: "null"),
              "dps" to (mismatch.dpsNonAssociation?.toString() ?: "null"),
            ),
          )
          mismatch
        }
    }
  }.onSuccess {
    log.debug("Checking NA (onSuccess: ${id.offenderNo1}, ${id.offenderNo2}")
  }.onFailure {
    log.error("Unable to match non-associations for id: ${id.offenderNo1}, ${id.offenderNo2}", it)
    telemetryClient.trackEvent(
      "non-associations-reports-reconciliation-mismatch-error",
      mapOf(
        "offenderNo1" to id.offenderNo1,
        "offenderNo2" to id.offenderNo2,
      ),
    )
  }.getOrDefault(emptyList())
}

data class NonAssociationReportDetail(val type: String, val createdDate: String)
data class MismatchNonAssociation(val id: NonAssociationIdResponse, val nomisNonAssociation: NonAssociationReportDetail?, val dpsNonAssociation: NonAssociationReportDetail?)
