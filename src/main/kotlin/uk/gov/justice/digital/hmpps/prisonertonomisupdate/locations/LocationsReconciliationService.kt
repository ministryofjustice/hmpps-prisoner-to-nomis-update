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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.LegacyLocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AmendmentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.LocationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ProfileRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.asPages
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth

private const val TRACKING_MISMATCH = "Tracking mismatch"

@Service
class LocationsReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: NomisApiService,
  private val locationsApiService: LocationsApiService,
  private val mappingService: LocationsMappingService,
  @Value($$"${reports.locations.reconciliation.page-size}")
  private val pageSize: Long,
) {
  private val invalidPrisons = listOf("ZZGHI", "UNKNWN", "TRN", "LT3", "LT4", "ALI") // Albany defunct
  private var nomisTotal = 0
  private var invalidPrisonsTotal = 0

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generateReconciliationReport() {
    val locationsCount = nomisApiService.getLocations(0, 1).totalElements

    telemetryClient.trackEvent("locations-reports-reconciliation-requested", mapOf("locations-nomis-total" to locationsCount.toString()))
    log.info("Locations reconciliation report requested for $locationsCount locations")

    runCatching { generateReconciliationReport(locationsCount) }
      .onSuccess { fullResults ->
        log.info("Locations reconciliation report completed with ${fullResults.size} mismatches")
        val results = fullResults.take(10) // Only log the first 10 to avoid an insights error with too much data
        val map = mapOf("mismatch-count" to fullResults.size.toString()) +
          results.associate { "${it.nomisId},${it.dpsId}" to "nomis=${it.nomisLocation}, dps=${it.dpsLocation}" }
        telemetryClient.trackEvent("locations-reports-reconciliation-success", map)
        log.info("Locations reconciliation report logged")
      }
      .onFailure {
        telemetryClient.trackEvent("locations-reports-reconciliation-failed")
        log.error("Locations reconciliation report failed", it)
      }
  }

  suspend fun generateReconciliationReport(locationsCount: Long): List<MismatchLocation> {
    val allDpsIdsInNomis = mutableSetOf<String>()
    nomisTotal = locationsCount.toInt()
    val results = locationsCount.asPages(pageSize).flatMap { page ->
      val locations = getNomisLocationsForPage(page)
        .mapNotNull { locationIdResponse ->
          val nomisId = locationIdResponse.locationId
          runCatching {
            mappingService.getMappingGivenNomisIdOrNull(nomisId)
              ?.also { mappingDto ->
                allDpsIdsInNomis.add(mappingDto.dpsLocationId)
              }
              ?: run {
                val nomisDetails = nomisApiService.getLocationDetails(nomisId)
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
          }.onFailure {
            telemetryClient.trackEvent(
              "locations-reports-reconciliation-retrieval-error",
              mapOf("nomis-location-id" to nomisId.toString()),
            )
            log.error("Unexpected error from api getting nomis location $nomisId", it)
          }.getOrNull()
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
            dpsLocation = LocationReportDetail(
              dpsRecord.code,
              "${dpsRecord.prisonId}-${dpsRecord.pathHierarchy}",
              dpsRecord.residentialHousingType?.name,
              dpsRecord.localName,
              dpsRecord.comments,
              dpsRecord.capacity?.workingCapacity,
              dpsRecord.capacity?.maxCapacity,
              dpsRecord.certifiedCell,
              dpsRecord.capacity?.certifiedNormalAccommodation,
              dpsRecord.active,
              dpsRecord.attributes?.size,
              dpsRecord.usage?.size,
              dpsRecord.changeHistory?.size,
              dpsRecord.internalMovementAllowed,
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

  internal suspend fun getNomisLocationsForPage(page: Pair<Long, Long>) = runCatching {
    nomisApiService.getLocations(page.first, page.second).content
  }
    .onFailure {
      telemetryClient.trackEvent(
        "locations-reports-reconciliation-mismatch-page-error",
        mapOf("page" to page.first.toString()),
      )
      log.error("Unable to match entire Nomis page of prisoners: $page", it)
    }
    .getOrElse { emptyList() }
    .also { log.info("Nomis Page requested: $page, with ${it.size} locations") }

  internal suspend fun getDpsLocationsForPage(page: Pair<Long, Long>): List<LegacyLocation> = runCatching {
    locationsApiService.getLocations(page.first, page.second).content
  }
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
      async { nomisApiService.getLocationDetails(mapping.nomisLocationId) } to
        async { locationsApiService.getLocation(mapping.dpsLocationId, false) }
    }.awaitBoth()

    val parentMapped = nomisRecord.parentLocationId?.let { mappingService.getMappingGivenNomisIdOrNull(it)?.dpsLocationId }

    val verdict = doesNotMatch(nomisRecord, dpsRecord, parentMapped)
    return if (verdict != null) {
      val mismatch = MismatchLocation(
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
          nomisRecord.tracking,
        ),
        LocationReportDetail(
          dpsRecord.code,
          "${dpsRecord.prisonId}-${dpsRecord.pathHierarchy}",
          dpsRecord.residentialHousingType?.name,
          dpsRecord.localName,
          dpsRecord.comments,
          dpsRecord.capacity?.workingCapacity,
          dpsRecord.capacity?.maxCapacity,
          dpsRecord.certifiedCell,
          dpsRecord.capacity?.certifiedNormalAccommodation,
          dpsRecord.active,
          dpsRecord.attributes?.size,
          dpsRecord.usage?.size,
          dpsRecord.changeHistory?.size,
          dpsRecord.internalMovementAllowed,
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
    nomisParentMappedToUUID: String?,
  ): String? {
    if (dps.permanentlyDeactivated) {
      return null
    }
    if (((nomisParentMappedToUUID == null) xor (dps.parentId == null)) ||
      (nomisParentMappedToUUID != null && nomisParentMappedToUUID != dps.parentId.toString())
    ) {
      return "Parent mismatch"
    }
    if (nomis.locationCode != dps.code) return "Location code mismatch"
    if (nomis.prisonId != dps.prisonId) return "Prison id mismatch"
    if (nomis.listSequence != dps.orderWithinParentLocation) return "order mismatch nomis=${nomis.listSequence} dps=${dps.orderWithinParentLocation}"
    if ((nomis.unitType == null) != (dps.residentialHousingType == null)) return "Housing type mismatch"
    if (nomis.userDescription != dps.localName) return "Local Name mismatch"
    if (dps.residentialHousingType != null && dps.locationType == LegacyLocation.LocationType.CELL) {
      if (dps.residentialHousingType != LegacyLocation.ResidentialHousingType.HOLDING_CELL) {
        val oc = nomis.operationalCapacity // satisfy the finickety compiler
        val c = nomis.capacity
        val cc = nomis.cnaCapacity

        if (oc != null && oc > 0 && oc != dps.capacity?.workingCapacity) return "Cell operational capacity mismatch"
        if (c != null && c > 0 && c != dps.capacity?.maxCapacity) return "Cell max capacity mismatch"
        if (cc != null && cc > 0) {
          if ((nomis.certified == true) != (dps.certifiedCell == true)) return "Cell certification mismatch"
          if (cc != dps.capacity?.certifiedNormalAccommodation) return "Cell CNA capacity mismatch"
        }
      }
      if (expectedDpsAttributesSize(nomis.profiles) != (dps.attributes?.size ?: 0)) return "Cell attributes mismatch"
    }
    if (dps.residentialHousingType == null && (nomis.usages?.size ?: 0) != (dps.usage?.size ?: 0)) return "Location usage mismatch"
    // There are 100s of key mismatches not counting BOX and POSI, general position is that we dont care.
    // we also dont care about comment mismatches or location history mismatches
    if (dps.active != nomis.active) return "Active mismatch"
    if (dps.residentialHousingType == null && nomis.tracking != dps.internalMovementAllowed && nomis.active) {
      return TRACKING_MISMATCH
    }
    return null
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

  private fun filterAmendmentResponses(amendments: List<AmendmentResponse>?) = amendments
    ?.filter { it.oldValue != it.newValue }
    // user_desc not recorded in Nomis history
    ?.filterNot { it.columnName == "DESCRIPTION" }
    // eliminate duplicate entries with timestamps within same second (about 120)
    ?.toSet()
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
  val internalMovementAllowed: Boolean?,
)

data class MismatchLocation(
  val nomisId: Long? = null,
  val dpsId: String? = null,
  val nomisLocation: LocationReportDetail? = null,
  val dpsLocation: LocationReportDetail? = null,
)
