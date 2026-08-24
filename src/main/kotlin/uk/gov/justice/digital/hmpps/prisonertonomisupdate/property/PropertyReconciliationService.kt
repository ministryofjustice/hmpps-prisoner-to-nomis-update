package uk.gov.justice.digital.hmpps.prisonertonomisupdate.property

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.data.IdRange
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.generateIdRangesReconciliationReport
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.LocationsMappingService
import java.time.LocalDate
import java.util.UUID

@Service
class PropertyReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val propertyNomisApiService: PropertyNomisApiService,
  private val dpsApiService: PropertyDpsApiService,
  private val propertyMappingService: PropertyMappingService,
  private val locationsMappingService: LocationsMappingService,
  @Value("\${reports.property.reconciliation.page-size:1000}") private val pageSize: Int = 1000,
  @Value("\${reports.property.reconciliation.thread-count:10}") private val threadCount: Int = 10,
) {
  private companion object {
    private const val TELEMETRY_PREFIX = "property-reports-reconciliation"
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun generatePropertyReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_PREFIX-requested",
      emptyMap(),
    )

    runCatching { generatePropertyReconciliationReport() }
      .onSuccess {
        telemetryClient.trackEvent(
          "$TELEMETRY_PREFIX-report",
          mapOf(
            "property-count" to it.itemsChecked.toString(),
            "page-count" to it.pagesChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ),
        )
      }
      .onFailure {
        telemetryClient.trackEvent(
          "$TELEMETRY_PREFIX-report",
          mapOf(
            "success" to "false",
            "error" to (it.message ?: it.javaClass.name),
          ),
        )
        log.error("Property reconciliation report failed", it)
      }
  }

  private suspend fun generatePropertyReconciliationReport(): ReconciliationResult<MismatchProperty> = generateIdRangesReconciliationReport(
    threadCount = threadCount,
    checkMatch = ::checkProperty,
    idRanges = { propertyNomisApiService.getIdRanges(pageSize).toIdRanges() },
    idsInRange = { range -> this.getIdsInRange(range.fromId, range.toId) },
  )

  fun List<Long>.toIdRanges(): List<IdRange> = mutableListOf(0L).apply {
    addAll(this@toIdRanges)
    add(Long.MAX_VALUE)
  }
    .zipWithNext()
    .map { IdRange(it.first, it.second) }

  internal suspend fun checkProperty(id: Long): MismatchProperty? = runCatching {
    val nomisData = propertyNomisApiService.getPropertyContainer(id)

    val location = nomisData.internalLocationId?.let { locationsMappingService.getLocationMappingUsingExternalApi(it).dpsLocationId }

    val nomisFields = PropertyContainerFields(
      id = nomisData.containerId.toString(),
      offenderNo = nomisData.offenderNo,
      location = location,
      prison = nomisData.prisonId,
      active = nomisData.active,
      sealMark = nomisData.sealMark,
      containerCode = nomisData.containerCode.name,
      proposedDisposalDate = nomisData.proposedDisposalDate,
      expiryDate = nomisData.expiryDate,
    )
    val mapping = propertyMappingService.getMappingByNomisIdOrNull(id)
    if (mapping == null) {
      log.info("No mapping found for nomisId=$id")
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-no-mapping",
        mapOf(
          "nomisId" to id.toString(),
        ),
      )
      return MismatchProperty(
        nomis = nomisFields,
        dps = null,
        differences = listOf(Difference("nomis-only", dps = null, nomis = nomisFields)),
      )
    }

    val dpsData = dpsApiService.getPropertyOrNull(UUID.fromString(mapping.dpsPropertyContainerId))
    if (dpsData == null) {
      log.info("No DPS record found for nomisId=$id")
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-no-dps",
        mapOf(
          "nomisId" to id.toString(),
          "dpsId" to mapping.dpsPropertyContainerId,
        ),
      )
      return MismatchProperty(
        nomis = nomisFields,
        dps = null,
        differences = listOf(Difference("nomis-only", dps = null, nomis = nomisFields)),
      )
    }

    val dpsFields = PropertyContainerFields(
      id = dpsData.id.toString(),
      offenderNo = dpsData.prisonerNumber,
      location = dpsData.currentLocation?.toString(),
      prison = dpsData.prisonId,
      active = dpsData.removalOutcome == null,
      sealMark = dpsData.currentSealNumber,
      containerCode = dpsData.toNomisContainerCode().name,
      proposedDisposalDate = dpsData.proposedDisposalDate,
      expiryDate = dpsData.removalDate,
    )

    val differenceList = compareObjects(dpsFields, nomisFields)

    // log.info("$id compared\n$dpsFields with\n$nomisFields with result\n$differenceList")

    if (differenceList.isEmpty()) {
      return null
    }
    log.info("Differences: $differenceList")
    telemetryClient.trackEvent(
      "$TELEMETRY_PREFIX-mismatch",
      mapOf(
        "nomisId" to nomisData.containerId.toString(),
        "dpsId" to dpsData.id.toString(),
        "differences" to differenceList.joinToString { "${it.field}: dps=${it.dps}, nomis=${it.nomis}" },
      ),
    )
    return MismatchProperty(
      nomis = nomisFields,
      dps = dpsFields,
      differences = differenceList,
    )
  }.onFailure {
    log.error("Unable to match property containers for id=$id", it)
    telemetryClient.trackEvent(
      "$TELEMETRY_PREFIX-error",
      mapOf(
        "nomisId" to id.toString(),
        "error" to (it.message ?: it.javaClass.name),
      ),
    )
  }.getOrNull()

  internal suspend fun getIdsInRange(
    fromId: Long,
    toId: Long,
  ): ReconciliationPageResult<Long> = runCatching {
    propertyNomisApiService.getIdentifiersInRange(fromId, toId)
  }.fold(
    onSuccess = { ids ->
      ReconciliationSuccessPageResult(ids = ids, last = 0)
        .also { log.info("Page requested from fromId: $fromId, toId: $toId, with ${it.ids.size} prisoners") }
    },
    onFailure = {
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-mismatch-page-error",
        mapOf(
          "fromId" to fromId.toString(),
          "toId" to toId.toString(),
          "error" to (it.message ?: it.javaClass.name),
        ),
      )
      log.error("Unable to match entire page of prisoners from fromId: $fromId, toId: $toId", it)
      ReconciliationErrorPageResult(it)
    },
  )
}

data class MismatchProperty(
  val nomis: PropertyContainerFields?,
  val dps: PropertyContainerFields?,
  val differences: List<Difference> = emptyList(),
)

private fun compareObjects(dpsObj: PropertyContainerFields?, nomisObj: PropertyContainerFields?): List<Difference> {
  if (dpsObj == null && nomisObj == null) return emptyList()
  if (dpsObj == null || nomisObj == null) return listOf(Difference("property", dpsObj, nomisObj))

  val differences = mutableListOf<Difference>()

  if (dpsObj.offenderNo != nomisObj.offenderNo) {
    differences.add(Difference("offenderNo", dpsObj.offenderNo, nomisObj.offenderNo))
  }
  if (dpsObj.prison != nomisObj.prison) {
    differences.add(Difference("prisonId", dpsObj.prison, nomisObj.prison))
  }
  if (dpsObj.location != nomisObj.location) {
    differences.add(Difference("location", dpsObj.location, nomisObj.location))
  }
  if (dpsObj.active != nomisObj.active) {
    differences.add(Difference("active", dpsObj.active, nomisObj.active))
  }
  if (dpsObj.sealMark != nomisObj.sealMark) {
    differences.add(Difference("sealMark", dpsObj.sealMark, nomisObj.sealMark))
  }
  if (dpsObj.containerCode != nomisObj.containerCode) {
    differences.add(Difference("containerCode", dpsObj.containerCode, nomisObj.containerCode))
  }
  if (dpsObj.proposedDisposalDate != nomisObj.proposedDisposalDate) {
    differences.add(Difference("proposedDisposalDate", dpsObj.proposedDisposalDate, nomisObj.proposedDisposalDate))
  }
  if (dpsObj.expiryDate != nomisObj.expiryDate) {
    differences.add(Difference("expiryDate", dpsObj.expiryDate, nomisObj.expiryDate))
  }
  return differences
}

data class PropertyContainerFields(
  val id: String,
  val offenderNo: String,
  val location: String?,
  val prison: String,
  val active: Boolean,
  val sealMark: String? = null,
  val containerCode: String,
  val proposedDisposalDate: LocalDate? = null,
  val expiryDate: LocalDate? = null,
  // val createdDateTime: LocalDateTime, // different for migrated property, so not included in comparison
  // val createdBy: String, // different for sync to nomis, so not included in comparison
)

data class Difference(val field: String, val dps: Any?, val nomis: Any?)
