package uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.ChangeHistory
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.LegacyLocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.AmendmentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.LocationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.ProfileRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.asPages
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.doApiCallWithRetries

@Service
class LocationsReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: NomisApiService,
  private val locationsApiService: LocationsApiService,
  private val mappingService: LocationsMappingService,
  @Value("\${reports.locations.reconciliation.page-size}")
  private val pageSize: Long,
) {
  private val invalidPrisons = listOf("ZZGHI", "UNKNWN", "TRN", "LT3", "LT4", "ALI") // Albany defunct
  private var nomisTotal = 0
  private var invalidPrisonsTotal = 0

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateReconciliationReport(locationsCount: Long): List<MismatchLocation> {
    val allDpsIdsInNomis = mutableSetOf<String>()
    nomisTotal = locationsCount.toInt()
    val results = locationsCount.asPages(pageSize).flatMap { page ->
      val locations = getNomisLocationsForPage(page)
        .map { it.locationId }
        .mapNotNull { nomisId ->
          doApiCallWithRetries { mappingService.getMappingGivenNomisIdOrNull(nomisId) }
            ?.also { mappingDto ->
              allDpsIdsInNomis.add(mappingDto.dpsLocationId)
            }
            ?: run {
              val nomisDetails = doApiCallWithRetries { nomisApiService.getLocationDetails(nomisId) }
              if (invalidPrisons.contains(nomisDetails.prisonId)) {
                invalidPrisonsTotal++
              } else {
                log.info("No mapping found for location $nomisId, key ${nomisDetails.description}")
                telemetryClient.trackEvent(
                  "locations-reports-reconciliation-mismatch-missing-mapping",
                  mapOf("locationId" to nomisId.toString()),
                )
              }
              null
            }
        }

      withContext(Dispatchers.Unconfined) {
        locations.map { async { checkMatch(it) } }
      }.awaitAll().filterNotNull()
    }
    return results + checkForMissingDpsRecords(allDpsIdsInNomis)
  }

  internal suspend fun checkForMissingDpsRecords(allDpsIdsInNomis: Set<String>): List<MismatchLocation> {
    val allDpsIds = locationsApiService.getLocations(0, 1).totalElements

    val validNomisTotal = nomisTotal - invalidPrisonsTotal
    if (allDpsIds.toInt() == validNomisTotal) {
      log.info("Total no of locations matches: DPS=$allDpsIds, Nomis=$validNomisTotal, (nomis invalidPrisonsTotal = $invalidPrisonsTotal)")
      return emptyList()
    }
    log.info("Total no of locations does not match: DPS=$allDpsIds, Nomis=$validNomisTotal, (nomis invalidPrisonsTotal = $invalidPrisonsTotal)")
    telemetryClient.trackEvent(
      "locations-reports-reconciliation-mismatch-missing-dps-records",
      mapOf("dps-total" to allDpsIds.toString(), "nomis-total" to validNomisTotal.toString()),
    )
    return allDpsIds.asPages(pageSize).flatMap { page ->
      getDpsLocationsForPage(page)
        .filterNot {
          allDpsIdsInNomis.contains((it.id.toString()))
        }
        .map { dpsRecord ->
          val mismatch = MismatchLocation(
            dpsId = dpsRecord.id.toString(),
            dpsLocation =
            LocationReportDetail(
              dpsRecord.code,
              "${dpsRecord.prisonId}-${dpsRecord.pathHierarchy}",
              dpsRecord.residentialHousingType?.name,
              dpsRecord.localName,
              dpsRecord.comments,
              dpsRecord.capacity?.workingCapacity,
              dpsRecord.capacity?.maxCapacity,
              dpsRecord.certification?.certified,
              dpsRecord.certification?.capacityOfCertifiedCell,
              dpsRecord.active,
              dpsRecord.attributes?.size,
              dpsRecord.usage?.size,
              dpsRecord.changeHistory?.size,
            ),
          )
          log.info("Location Mismatch found extra DPS location $dpsRecord")
          telemetryClient.trackEvent(
            "locations-reports-reconciliation-dps-only",
            mapOf(
              "dpsId" to dpsRecord.id.toString(),
              "key" to "${dpsRecord.prisonId}-${dpsRecord.pathHierarchy}",
              "dps" to mismatch.dpsLocation.toString(),
            ),
          )
          mismatch
        }
    }
  }

  internal suspend fun getNomisLocationsForPage(page: Pair<Long, Long>) =
    runCatching { doApiCallWithRetries { nomisApiService.getLocations(page.first, page.second).content } }
      .onFailure {
        telemetryClient.trackEvent(
          "locations-reports-reconciliation-mismatch-page-error",
          mapOf("page" to page.first.toString()),
        )
        log.error("Unable to match entire Nomis page of prisoners: $page", it)
      }
      .getOrElse { emptyList() }
      .also { log.info("Nomis Page requested: $page, with ${it.size} locations") }

  internal suspend fun getDpsLocationsForPage(page: Pair<Long, Long>): List<LegacyLocation> =
    runCatching { doApiCallWithRetries { locationsApiService.getLocations(page.first, page.second).content } }
      .onFailure {
        telemetryClient.trackEvent(
          "locations-reports-reconciliation-mismatch-page-error",
          mapOf("page" to page.first.toString()),
        )
        log.error("Unable to match entire DPS page of prisoners: $page", it)
      }
      .getOrElse { emptyList() }
      .also { log.info("DPS Page requested: $page, with ${it.size} locations") }

  internal suspend fun checkMatch(mapping: LocationMappingDto): MismatchLocation? = runCatching {
    // log.debug("Checking location: {}", mapping)

    val (nomisRecord, dpsRecord) = withContext(Dispatchers.Unconfined) {
      async { doApiCallWithRetries { nomisApiService.getLocationDetails(mapping.nomisLocationId) } } to
        async { doApiCallWithRetries { locationsApiService.getLocation(mapping.dpsLocationId, true) } }
    }.awaitBoth()

    val verdict = doesNotMatch(nomisRecord, dpsRecord)
    return if (verdict != null) {
      val mismatch =
        MismatchLocation(
          nomisRecord.locationId,
          dpsRecord.id.toString(),
          LocationReportDetail(
            nomisRecord.locationCode,
            nomisRecord.description,
            nomisRecord.unitType?.name,
            nomisRecord.userDescription,
            nomisRecord.comment,
            nomisRecord.operationalCapacity,
            nomisRecord.capacity,
            nomisRecord.certified,
            nomisRecord.cnaCapacity,
            nomisRecord.active,
            expectedDpsAttributesSize(nomisRecord.profiles),
            nomisRecord.usages?.size,
            expectedDpsHistorySize(nomisRecord.amendments),
          ),
          LocationReportDetail(
            dpsRecord.code,
            "${dpsRecord.prisonId}-${dpsRecord.pathHierarchy}",
            dpsRecord.residentialHousingType?.name,
            dpsRecord.localName,
            dpsRecord.comments,
            dpsRecord.capacity?.workingCapacity,
            dpsRecord.capacity?.maxCapacity,
            dpsRecord.certification?.certified,
            dpsRecord.certification?.capacityOfCertifiedCell,
            dpsRecord.active,
            dpsRecord.attributes?.size,
            dpsRecord.usage?.size,
            dpsRecord.changeHistory?.size,
          ),
        )
      log.info("Location Mismatch found $verdict (type ${nomisRecord.locationType}) in:\n  $mismatch")
      telemetryClient.trackEvent(
        "locations-reports-reconciliation-mismatch",
        mapOf(
          "nomisId" to mismatch.nomisId.toString(),
          "dpsId" to mismatch.dpsId.toString(),
          "verdict" to verdict,
          "nomis" to (mismatch.nomisLocation?.toString() ?: "null"),
          "dps" to (mismatch.dpsLocation?.toString() ?: "null"),
        ),
      )
      mismatch
    } else {
      null
    }
  }.onSuccess {
    log.debug("Checking location (onSuccess: ${mapping.nomisLocationId})")
  }.onFailure {
    log.error("Unable to match locations for id: ${mapping.nomisLocationId},${mapping.dpsLocationId}", it)
    telemetryClient.trackEvent(
      "locations-reports-reconciliation-mismatch-error",
      mapOf(
        "nomisId" to mapping.nomisLocationId.toString(),
        "dpsId" to mapping.dpsLocationId,
      ),
    )
  }.getOrNull()

  internal fun doesNotMatch(
    nomis: LocationResponse,
    dps: LegacyLocation,
  ): String? {
    if (nomis.locationCode != dps.code) return "Location code mismatch"
    if (nomis.prisonId != dps.prisonId) return "Prison id mismatch"
    if (nomis.listSequence != dps.orderWithinParentLocation) return "order mismatch nomis=${nomis.listSequence} dps=${dps.orderWithinParentLocation}"
    // if (nomis.description != dps.key) return "Location key mismatch"
    // IN DPS can be inactive if parent is inactive:  if (nomis.active != dps.active) return "Location active mismatch"
    if ((nomis.unitType == null) != (dps.residentialHousingType == null)) return "Housing type mismatch"
    if (nomis.userDescription != dps.localName) return "Local Name mismatch"
    if (nomis.comment != dps.comments) return "Comment mismatch"
    if (dps.residentialHousingType != null && dps.locationType == LegacyLocation.LocationType.CELL) {
      if (dps.residentialHousingType != LegacyLocation.ResidentialHousingType.HOLDING_CELL) {
        if (nomis.operationalCapacity != null && nomis.operationalCapacity > 0 && nomis.operationalCapacity != dps.capacity?.workingCapacity) return "Cell operational capacity mismatch"
        if (nomis.capacity != null && nomis.capacity > 0 && nomis.capacity != dps.capacity?.maxCapacity) return "Cell max capacity mismatch"
        if (nomis.cnaCapacity != null && nomis.cnaCapacity > 0) {
          if ((nomis.certified == true) != (dps.certification?.certified == true)) return "Cell certification mismatch"
          if (nomis.cnaCapacity != dps.certification?.capacityOfCertifiedCell) return "Cell CNA capacity mismatch"
        }
      }
      if (expectedDpsAttributesSize(nomis.profiles) != (dps.attributes?.size ?: 0)) return "Cell attributes mismatch"
    }
    if (dps.residentialHousingType == null && (nomis.usages?.size ?: 0) != (dps.usage?.size ?: 0)) return "Location usage mismatch"
//    if (historyDoesNotMatch(nomis, dps)) {
//      return "Location history mismatch:\n${reportHistory(nomis.amendments, dps.changeHistory)}"
//    }
    return null
  }

  fun historyDoesNotMatch(nomis: LocationResponse, dps: LegacyLocation): Boolean {
    val filteredAmendmentResponses = filterAmendmentResponses(nomis.amendments)
    val filteredDpsHistoryResponses = dps.changeHistory?.filter { it.attribute != "Converted Cell Type" }
    if ((filteredAmendmentResponses?.size ?: 0) != (filteredDpsHistoryResponses?.size ?: 0)) {
      return true
    }
    val nomisListConverted = filteredAmendmentResponses?.map { a ->
      ChangeHistory(toHistoryAttribute(a.columnName), a.amendedBy, a.amendDateTime, a.oldValue, a.newValue)
    } ?: emptyList()
    val nomisSetSorted = nomisListConverted.sortedBy { it.amendedDate + it.attribute + it.amendedBy + it.oldValue + it.newValue }
    val dpsSetSorted = filteredDpsHistoryResponses?.sortedBy { it.amendedDate + it.attribute + it.amendedBy + it.oldValue + it.newValue } ?: emptyList()

    if (dpsSetSorted.size > nomisSetSorted.size) {
      return true
    }

    return nomisSetSorted.zip(dpsSetSorted)
      .any { (nomisItem, dpsItem) ->
        nomisItem.attribute != dpsItem.attribute ||
          nomisItem.amendedBy != dpsItem.amendedBy ||
          nomisItem.amendedDate != dpsItem.amendedDate ||
          nomisItem.oldValue != dpsItem.oldValue ||
          nomisItem.newValue != dpsItem.newValue
      }
  }

  private fun reportHistory(nomisList: List<AmendmentResponse>?, dpsList: List<ChangeHistory>?): String {
    val nomisListConverted = filterAmendmentResponses(nomisList)?.map { nomis ->
      ChangeHistory(toHistoryAttribute(nomis.columnName), nomis.amendedBy, nomis.amendDateTime, nomis.oldValue, nomis.newValue)
    } ?: emptyList()

    val dpsListNonNull = dpsList ?: emptyList()
    val onlyInDps = dpsListNonNull - nomisListConverted.toSet()
    val onlyInNomis = nomisListConverted - dpsListNonNull.toSet()

    if (onlyInDps.isNotEmpty() || onlyInNomis.isNotEmpty()) {
      return """  Only in Dps: $onlyInDps
          |  Only in Nomis: $onlyInNomis
      """.trimMargin()
    }

    val nomisSetSorted = nomisListConverted.sortedBy { it.amendedDate + it.attribute + it.amendedBy + it.oldValue + it.newValue }
    val dpsSetSorted = dpsListNonNull.sortedBy { it.amendedDate + it.attribute + it.amendedBy + it.oldValue + it.newValue }

    nomisSetSorted.zip(dpsSetSorted).forEachIndexed { index, (nomisItem, dpsItem) ->
      if (nomisItem.attribute != dpsItem.attribute ||
        nomisItem.amendedBy != dpsItem.amendedBy ||
        nomisItem.amendedDate != dpsItem.amendedDate ||
        nomisItem.oldValue != dpsItem.oldValue ||
        nomisItem.newValue != dpsItem.newValue
      ) {
        return """Item at index $index doesn't match :
            | prev  ${dpsSetSorted.elementAtOrNull(index - 1)}
            | nomis $nomisItem
            | dps   $dpsItem
        """.trimMargin()
      }
    }
    return ""
  }

  private fun expectedDpsAttributesSize(profiles: List<ProfileRequest>?) = (
    profiles
      ?.filterNot { it.profileType == ProfileRequest.ProfileType.NON_ASSO_TYP }
      ?.filterNot {
        // Invalid codes (not in ref data)
        (it.profileType == ProfileRequest.ProfileType.HOU_USED_FOR && it.profileCode == "S") ||
          (it.profileType == ProfileRequest.ProfileType.HOU_USED_FOR && it.profileCode == "BM") ||
          (it.profileType == ProfileRequest.ProfileType.HOU_USED_FOR && it.profileCode == "MB") ||
          (it.profileType == ProfileRequest.ProfileType.HOU_SANI_FIT && it.profileCode == "ABC") ||
          (it.profileType == ProfileRequest.ProfileType.HOU_UNIT_ATT && it.profileCode == "LISTENER") ||
          (it.profileType == ProfileRequest.ProfileType.HOU_USED_FOR && it.profileCode == "0") ||
          (it.profileType == ProfileRequest.ProfileType.HOU_SANI_FIT && it.profileCode == "TVP") ||
          (it.profileType == ProfileRequest.ProfileType.HOU_SANI_FIT && it.profileCode == "ST")
      }
      ?.map {
        if (it.profileCode == "N/A") {
          ProfileRequest(ProfileRequest.ProfileType.SUP_LVL_TYPE, "NA")
        } else if (it.profileType == ProfileRequest.ProfileType.HOU_USED_FOR && it.profileCode == "V") {
          ProfileRequest(ProfileRequest.ProfileType.HOU_USED_FOR, "7")
        } else {
          it
        }
      }
      ?.toSet()
      ?.size ?: 0
    )

  private fun expectedDpsHistorySize(amendments: List<AmendmentResponse>?) = filterAmendmentResponses(amendments)?.size ?: 0

  private fun filterAmendmentResponses(amendments: List<AmendmentResponse>?) =
    amendments
      ?.filter { it.oldValue != it.newValue }
      // user_desc not recorded in Nomis history
      ?.filterNot { it.columnName == "DESCRIPTION" }
      // eliminate duplicate entries with timestamps within same second (about 120)
      ?.toSet()

  private fun toHistoryAttribute(columnName: String?): String =
    when (columnName) {
      "Unit Type" -> "RESIDENTIAL_HOUSING_TYPE"
      "Active" -> "ACTIVE"
      "Living Unit Id" -> "CODE"
      "Comments" -> "COMMENTS"
      "Accommodation Type" -> "LOCATION_TYPE"
      "Certified" -> "CERTIFIED"
      "Description" -> "DESCRIPTION"
      "Baseline CNA" -> "CERTIFIED_CAPACITY"
      "Operational Capacity" -> "OPERATIONAL_CAPACITY"
      "Deactivate Reason" -> "DEACTIVATED_REASON"
      "Proposed Reactivate Date" -> "PROPOSED_REACTIVATION_DATE"
      "Sequence" -> "ORDER_WITHIN_PARENT_LOCATION"
      "Deactivate Date" -> "DEACTIVATED_DATE"
      "Maximum Capacity" -> "CAPACITY"
      null -> "ATTRIBUTES"
      else -> throw IllegalArgumentException("Unknown history attribute column name $columnName")
    }.let {
      LocationAttribute.valueOf(it).description
    }
}

// Copied from locations api
enum class LocationAttribute(
  val description: String,
) {
  CODE("Code"),
  LOCATION_TYPE("Location Type"),
  RESIDENTIAL_HOUSING_TYPE("Residential Housing Type"),
  OPERATIONAL_CAPACITY("Working Capacity"),
  CAPACITY("Max Capacity"),
  CERTIFIED_CAPACITY("Baseline Certified Capacity"),
  CERTIFIED("Certified"),
  PARENT_LOCATION("Parent Location"),
  ORDER_WITHIN_PARENT_LOCATION("Order within parent location"),
  COMMENTS("Comments"),
  DESCRIPTION("Local Name"),
  ATTRIBUTES("Attributes"),
  USAGE("Usage"),
  NON_RESIDENTIAL_CAPACITY("Non Residential Capacity"),
  ACTIVE("Active"),
  DEACTIVATED_DATE("Deactivated Date"),
  DEACTIVATED_REASON("Deactivated Reason"),
  PROPOSED_REACTIVATION_DATE("Proposed Reactivation Date"),
}

data class LocationReportDetail(
  val code: String,
  val key: String,
  val housingType: String? = null,
  val localName: String? = null,
  val comment: String?,
  val operationalCapacity: Int? = null,
  val maxCapacity: Int? = null,
  val certified: Boolean? = null,
  val cnaCapacity: Int? = null,
  val active: Boolean,
  val attributes: Int?,
  val usages: Int?,
  val history: Int?,
)

data class MismatchLocation(
  val nomisId: Long? = null,
  val dpsId: String? = null,
  val nomisLocation: LocationReportDetail? = null,
  val dpsLocation: LocationReportDetail? = null,
)
