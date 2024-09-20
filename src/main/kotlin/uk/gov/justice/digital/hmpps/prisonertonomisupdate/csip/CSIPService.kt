package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable

@Service
class CSIPService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: CSIPNomisApiService,
  private val mappingApiService: CSIPMappingApiService,
) : CreateMappingRetryable {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun deleteCsipReport(csipEvent: CSIPEvent) {
    val dpsCsipReportId = csipEvent.additionalInformation.recordUuid
    val offenderNo = requireNotNull(csipEvent.personReference.findNomsNumber())
    val telemetryMap = mutableMapOf(
      "dpsCsipReportId" to dpsCsipReportId,
      "offenderNo" to offenderNo,
    )
    if (csipEvent.wasDeletedInDPS()) {
      runCatching {
        mappingApiService.getOrNullByDpsId(dpsCsipReportId)?.also { mapping ->

          nomisApiService.deleteCsipReport(csipReportId = mapping.nomisCSIPReportId)
          tryToDeletedMapping(dpsCsipReportId)
          telemetryClient.trackEvent("csip-deleted-success", telemetryMap)
        } ?: also {
          telemetryClient.trackEvent("csip-deleted-skipped", telemetryMap)
        }
      }.onFailure { e ->
        telemetryClient.trackEvent("csip-deleted-failed", telemetryMap)
        throw e
      }
    } else {
      telemetryClient.trackEvent("csip-deleted-ignored", telemetryMap)
    }
  }

  private suspend fun tryToDeletedMapping(dpsCsipId: String) = kotlin.runCatching {
    mappingApiService.deleteByDpsId(dpsCsipId)
  }.onFailure { e ->
    telemetryClient.trackEvent("csip-mapping-deleted-failed", mapOf("dpsCsipId" to dpsCsipId))
    log.warn("Unable to delete mapping for csip $dpsCsipId. Please delete manually", e)
  }

  override suspend fun retryCreateMapping(message: String) {
    TODO("Not yet implemented")
  }
}

data class CSIPEvent(
  val description: String?,
  val eventType: String,
  val personReference: PersonReference,
  val additionalInformation: CSIPAdditionalInformation,
)

data class CSIPAdditionalInformation(
  val recordUuid: String,
  val affectedComponents: List<AffectedComponent>,
  val source: CSIPSource,
)

data class PersonReference(val identifiers: List<Identifier> = listOf()) {
  operator fun get(key: String) = identifiers.find { it.type == key }?.value
  fun findNomsNumber() = get(NOMS_NUMBER_TYPE)

  companion object {
    const val NOMS_NUMBER_TYPE = "NOMS"
    fun withNomsNumber(prisonNumber: String) = PersonReference(listOf(Identifier(NOMS_NUMBER_TYPE, prisonNumber)))
  }

  data class Identifier(val type: String, val value: String)
}

enum class CSIPSource {
  DPS,
  NOMIS,
}

enum class AffectedComponent {
  RECORD,
  CONTRIBUTORY_FACTOR,
}

fun CSIPEvent.wasCreatedInDPS() = wasSourceDPS()
fun CSIPEvent.wasDeletedInDPS() = wasSourceDPS()
fun CSIPEvent.wasSourceDPS() = this.additionalInformation.source == CSIPSource.DPS
