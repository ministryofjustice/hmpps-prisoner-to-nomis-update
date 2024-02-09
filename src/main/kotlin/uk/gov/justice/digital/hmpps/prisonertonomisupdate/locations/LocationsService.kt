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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.LocalDateTime

@Service
class LocationsService(
  private val locationsApiService: LocationsApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: LocationsMappingService,
  private val locationsUpdateQueueService: LocationsRetryQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {

  suspend fun createLocation(event: LocationDomainEvent) {
    synchronise {
      name = "location"
      telemetryClient = this@LocationsService.telemetryClient
      retryQueueService = locationsUpdateQueueService
      eventTelemetry = mapOf("dpsId" to event.additionalInformation.id)

      checkMappingDoesNotExist {
        mappingService.getMappingGivenDpsIdOrNull(event.additionalInformation.id)
      }
      transform {
        locationsApiService.getLocation(event.additionalInformation.id).run {
          val request = toCreateLocationRequest(this)

          eventTelemetry += "prisonId" to prisonId
          // TODO: add more telemetry

          LocationMappingDto(
            dpsLocationId = id.toString(),
            nomisLocationId = nomisApiService.createLocation(request).locationId,
            mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
          )
        }
      }
      saveMapping { }
    }
  }

  private fun toCreateLocationRequest(instance: Location) = CreateLocationRequest(
    locationCode = instance.code,
    certified = instance.certification != null,
    locationType = CreateLocationRequest.LocationType.valueOf(instance.locationType.name),
    comment = instance.comments,
    parentLocationId = 0, // instance.parentId,
    prisonId = instance.prisonId,
  )
  // TODO: will probably use a sync endpoint to get Nomis-style fields

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
)
