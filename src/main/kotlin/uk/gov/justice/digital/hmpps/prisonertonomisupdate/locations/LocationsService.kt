package uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.locations.model.Location
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateLocationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreatingSystem
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.LocalDateTime

@Service
class LocationsService(
  private val locationsApiService: LocationsApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: LocationsMappingService,
  private val locationsRetryQueueService: LocationsRetryQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {

  suspend fun createLocation(event: LocationDomainEvent) {
    val telemetryMap = mapOf("dpsId" to event.additionalInformation.id)
    if (isDpsCreated(event.additionalInformation)) {
      synchronise {
        name = "location"
        telemetryClient = this@LocationsService.telemetryClient
        retryQueueService = locationsRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingService.getMappingGivenDpsIdOrNull(event.additionalInformation.id)
        }
        transform {
          locationsApiService.getLocation(event.additionalInformation.id).run {
            val request = toCreateLocationRequest(this)

            eventTelemetry += "dpsId" to id.toString()
            eventTelemetry += "key" to key
            eventTelemetry += "prisonId" to prisonId

            LocationMappingDto(
              dpsLocationId = id.toString(),
              nomisLocationId = nomisApiService.createLocation(request).locationId,
              mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
            )
          }
        }
        saveMapping { mappingService.createMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("nonAssociation-create-ignored", telemetryMap)
    }
  }

  suspend fun deleteLocation(event: LocationDomainEvent) {
    val dpsId = event.additionalInformation.id
    val telemetryMap = mutableMapOf("dpsId" to dpsId)
    if (isDpsCreated(event.additionalInformation)) {
      runCatching {
        val nomisId =
          mappingService.getMappingGivenDpsId(dpsId).nomisLocationId
            .also { telemetryMap += "nomisId" to it.toString() }

        nomisApiService.deleteLocation(nomisId)
        mappingService.deleteMapping(dpsId)
      }.onSuccess {
        telemetryClient.trackEvent("location-delete-success", telemetryMap, null)
      }.onFailure { e ->
        telemetryClient.trackEvent("location-delete-failed", telemetryMap, null)
        throw e
      }
    }
  }

  suspend fun deleteAllLocations() {
    mappingService.getAllMappings().forEach { mapping ->
      runCatching {
        nomisApiService.deleteLocation(mapping.nomisLocationId)
        mappingService.deleteMapping(mapping.dpsLocationId)
      }.onSuccess {
        telemetryClient.trackEvent(
          "location-DELETE-ALL-success",
          mapOf(
            "nomisId" to mapping.nomisLocationId.toString(),
            "dpsId" to mapping.dpsLocationId,
          ),
          null,
        )
      }.onFailure { e ->
        log.error("Failed to delete location with dpsId ${mapping.dpsLocationId}", e)
        telemetryClient.trackEvent(
          "location-DELETE-ALL-failed",
          mapOf(
            "nomisId" to mapping.nomisLocationId.toString(),
            "dpsId" to mapping.dpsLocationId,
          ),
          null,
        )
      }
    }
  }

  private fun toCreateLocationRequest(instance: Location) = CreateLocationRequest(
    locationCode = instance.code,
    certified = instance.certification != null,
    locationType = CreateLocationRequest.LocationType.valueOf(instance.locationType.name),
    comment = instance.comments,
    parentLocationId = 0,
    prisonId = instance.prisonId,
    // TBD
  )

  private fun isDpsCreated(additionalInformation: LocationAdditionalInformation) =
    additionalInformation.source != CreatingSystem.NOMIS.name

  suspend fun createRetry(message: CreateMappingRetryMessage<LocationMappingDto>) {
    mappingService.createMapping(message.mapping)
      .also {
        telemetryClient.trackEvent(
          "location-create-mapping-retry-success",
          message.telemetryAttributes,
        )
      }
  }

  override suspend fun retryCreateMapping(message: String) = createRetry(message.fromJson())
  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

data class LocationDomainEvent(
  val eventType: String,
  val version: String,
  val description: String,
  val occurredAt: LocalDateTime,
  val additionalInformation: LocationAdditionalInformation,
)

data class LocationAdditionalInformation(
  val id: String,
  val key: String,
  val source: String? = null,
)
