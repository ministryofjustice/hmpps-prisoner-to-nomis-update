package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.Logger
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.model.LegacyAgencyDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.logger
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AgencyResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.awaitBoth
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
@Service
class AgencyRegistersReconciliationService(
  private val telemetryClient: TelemetryClient,
  private val dpsApiService: AgencyRegistersDpsApiService,
  private val nomisApiService: AgencyNomisApiService,
) {
  internal companion object {
    val log: Logger = logger()
    const val TELEMETRY_PREFIX = "agency-reconciliation"
  }

  suspend fun generateAgencyReconciliationReportBatch() {
    telemetryClient.trackEvent(
      "$TELEMETRY_PREFIX-requested",
      mapOf(),
    )

    runCatching { generateAgencyReconciliationReport() }
      .onSuccess {
        log.info("Agency report completed with ${it.mismatches.size} mismatches")
        telemetryClient.trackEvent(
          "$TELEMETRY_PREFIX-report",
          mapOf(
            "agency-count" to it.itemsChecked.toString(),
            "mismatch-count" to it.mismatches.size.toString(),
            "success" to "true",
          ) + it.mismatches.asMap(),
        )
      }
      .onFailure {
        telemetryClient.trackEvent("$TELEMETRY_PREFIX-report", mapOf("success" to "false"))
        log.error("Agency report failed", it)
      }
  }

  suspend fun checkMatch(agencyId: String): MismatchAgency? {
    val nomisAgency = runCatching { nomisApiService.getAgency(agencyId) }.getOrNull()
    val dpsAgency = runCatching { dpsApiService.getAgency(agencyId) }.getOrNull()

    return when {
      nomisAgency == null -> MismatchAgency(agencyId, "Missing in NOMIS")
      dpsAgency == null -> MismatchAgency(agencyId, "Missing in DPS")
      else -> compare(nomisAgency, dpsAgency)
    }?.also {
      telemetryClient.trackEvent(
        "$TELEMETRY_PREFIX-mismatch",
        mapOf(
          "agencyId" to it.agencyId,
          "reason" to it.reason,
        ),
      )
    }
  }

  fun compare(nomisAgency: AgencyResponse, dpsAgency: LegacyAgencyDto): MismatchAgency? = when {
    nomisAgency.asCoreDetails() != dpsAgency.asCoreDetails() -> MismatchAgency(nomisAgency.agencyId, "different-core-agency-details", nomis = nomisAgency.asCoreDetails().toString(), dps = dpsAgency.asCoreDetails().toString())
    else -> null
  }

  suspend fun generateAgencyReconciliationReport(): ReconciliationResult<MismatchAgency> {
    val (nomisAgencyIds, dpsAgencyIds) = withContext(Dispatchers.Unconfined) {
      async { nomisApiService.getAgencyIds().agencyIds.map { it.agencyId } } to
        async { dpsApiService.getAgencyIds().agencyIds.map { it.agencyId } }
    }.awaitBoth()

    val allIds = (nomisAgencyIds + dpsAgencyIds).toSet()

    return coroutineScope {
      val itemsCount = AtomicInteger(0)
      val pagesCount = AtomicInteger(0)

      val mismatchesChannel = Channel<MismatchAgency>(capacity = UNLIMITED)
      val channel = produce {
        allIds.forEach { send(it) }
        channel.close()
      }

      val jobs = (1L..10).map {
        launch {
          for (item in channel) {
            checkMatch(item)?.also { mismatchesChannel.send(it) }
            itemsCount.incrementAndGet()
          }
        }
      }

      launch {
        jobs.joinAll()
        mismatchesChannel.close()
      }

      ReconciliationResult(
        mismatches = mismatchesChannel.toList().also { log.info("Mismatch result: $it") },
        itemsChecked = itemsCount.get(),
        pagesChecked = pagesCount.get(),
      )
    }
  }
}
data class MismatchAgency(
  val agencyId: String,
  val reason: String,
  val nomis: String? = null,
  val dps: String? = null,
)

data class AgencyCoreDetails(
  val name: String,
  val longDescription: String?,
  val active: Boolean,
  val deactivationDate: LocalDate?,
  val cjitCode: String?,
  val areaCode: String?,
  val subareaCode: String?,
  val regionCode: String?,
  val geographicalAreaCode: String?,
  val payrollRegionCode: String?,
  val courtTypeCode: String?,
  val disabilityAccessCode: String?,
  val contact: String?,
  val localAuthorityCode: String?,
)

private fun AgencyResponse.asCoreDetails(): AgencyCoreDetails = AgencyCoreDetails(
  name = description,
  longDescription = longDescription,
  active = active,
  deactivationDate = deactivationDate,
  cjitCode = cjitCode,
  areaCode = area?.code,
  subareaCode = subArea?.code,
  regionCode = nomsRegion?.code,
  geographicalAreaCode = region?.code,
  payrollRegionCode = payrollRegion?.code,
  courtTypeCode = courtType?.code,
  disabilityAccessCode = when (disabilityAccessCode) {
    "WHEEL" -> LegacyAgencyDto.AccessibleAccess.WHEELCHAIR_ACCESS.name
    "Y", "Yes" -> LegacyAgencyDto.AccessibleAccess.ACCESSIBLE.name
    "N", "No" -> LegacyAgencyDto.AccessibleAccess.NONE.name
    "BA" -> LegacyAgencyDto.AccessibleAccess.BY_ARRANGEMENT_ONLY.name
    else -> null
  },
  contact = contactName,
  localAuthorityCode = localAuthorities.firstOrNull()?.code,
)
private fun LegacyAgencyDto.asCoreDetails(): AgencyCoreDetails = AgencyCoreDetails(
  name = name,
  longDescription = description,
  active = active,
  deactivationDate = inactiveDate,
  cjitCode = cjitCode,
  areaCode = areaCode,
  subareaCode = subareaCode,
  regionCode = regionCode,
  geographicalAreaCode = geographicalAreaCode,
  payrollRegionCode = payrollRegionCode,
  courtTypeCode = courtTypeCode,
  disabilityAccessCode = accessibleAccess?.name,
  contact = contact,
  localAuthorityCode = localAuthorityCode,
)

private fun List<MismatchAgency>.asMap(): Pair<String, String> = this
  .sortedBy { it.agencyId }.take(10).let { mismatch -> "agencyIds" to mismatch.joinToString { it.agencyId } }
