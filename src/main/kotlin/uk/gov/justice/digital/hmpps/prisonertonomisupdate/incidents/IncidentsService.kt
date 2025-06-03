package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.IncidentEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.IncidentsDpsApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.IncidentsMappingApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.IncidentsNomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.IncidentsRetryQueueService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.IncidentsService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.didOriginateInDPS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.ReportWithDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.IncidentMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.IncidentMappingDto.MappingType.DPS_CREATED
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreatingSystem
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise

@Service
class IncidentsService(
  private val nomisApiService: IncidentsNomisApiService,
  private val dpsApiService: IncidentsDpsApiService,
  private val mappingApiService: IncidentsMappingApiService,
  private val incidentsRetryQueueService: IncidentsRetryQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {

  suspend fun incidentCreated(event: IncidentEvent) {
    val dpsId = event.dpsId
    val nomisId = event.nomisId
    val telemetryMap = mutableMapOf(
      "dpsIncidentId" to dpsId.toString(),
      "nomisIncidentId" to nomisId.toString(),
    )

    if (event.didOriginateInDPS()) {
      synchronise {
        telemetryClient = this@IncidentsService.telemetryClient
        retryQueueService = incidentsRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getByDpsIncidentIdOrNull(dpsId)
        }
        transform {
          val dps = dpsApiService.getIncident(dpsId)
          nomisApiService.createIncident(nomisId, dps.toNomisCreateRequest())
          IncidentMappingDto(
            dpsIncidentId = dpsId,
            nomisIncidentId = nomisId,
            mappingType = DPS_CREATED,
          )
        }
        saveMapping { mappingApiService.createIncidentMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("incident-create-ignored", telemetryMap)
    }
  }
  suspend fun incidentUpdated(event: IncidentEvent) {
    val dpsId = event.dpsId
    val telemetryMap = mutableMapOf(
      "dpsIncidentId" to dpsId.toString(),
    )

    if (event.didOriginateInDPS()) {
      val nomisId = mappingApiService.getByDpsIncidentId(dpsId).nomisIncidentId
      val dps = dpsApiService.getIncident(dpsId)
      nomisApiService.updateIncident(
        nomisId = nomisId,
        dps.toNomisUpdateRequest(),
      )
      telemetryClient.trackEvent("incident-update-success", telemetryMap)
    } else {
      telemetryClient.trackEvent("incident-update-ignored", telemetryMap)
    }
  }
  suspend fun incidentDeleted(event: IncidentEvent) {
    val dpsId = event.dpsId
    val telemetryMap = mutableMapOf(
      "dpsIncidentId" to dpsId.toString(),
    )

    if (event.didOriginateInDPS()) {
      mappingApiService.getByDpsIncidentIdOrNull(dpsId)?.nomisIncidentId?.also {
        nomisApiService.deleteIncident(nomisId = it)
        mappingApiService.deleteByDpsIncidentId(dpsIncidentId = dpsId)
        telemetryClient.trackEvent("incident-delete-success", telemetryMap)
      } ?: run {
        telemetryClient.trackEvent("incident-delete-skipped", telemetryMap)
      }
    } else {
      telemetryClient.trackEvent("incident-delete-ignored", telemetryMap)
    }
  }

  override suspend fun retryCreateMapping(message: String) {
    createIncidentMapping(message.fromJson())
  }

  suspend fun createIncidentMapping(message: CreateMappingRetryMessage<IncidentMappingDto>) {
    mappingApiService.createIncidentMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "incident-create-success",
        message.telemetryAttributes,
      )
    }
  }

  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)
}
data class IncidentEvent(
  val eventType: String,
  val description: String,
  val additionalInformation: IncidentAdditionalInformation,
) {
  val dpsId: String
    get() = additionalInformation.id
  val nomisId: Long
    get() = additionalInformation.reportReference
}
data class IncidentAdditionalInformation(
  val id: String,
  val reportReference: Long,
  val source: CreatingSystem,
  // TODO - check - we may not need this
  val whatChanged: String?,
)

enum class IncidentSource {
  DPS,
  NOMIS,
}
private fun IncidentEvent.didOriginateInDPS() = this.additionalInformation.source == CreatingSystem.DPS

private fun ReportWithDetails.toNomisCreateRequest(): CreateIncidentRequest = CreateIncidentRequest(
  title = this.title,
)
private fun ReportWithDetails.toNomisUpdateRequest(): CreateIncidentRequest = CreateIncidentRequest(
  title = this.title,
)
