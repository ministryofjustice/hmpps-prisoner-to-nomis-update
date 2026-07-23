package uk.gov.justice.digital.hmpps.prisonertonomisupdate.property

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.health.MappingApiHealth
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PropertyContainerMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PropertyContainerCode
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PropertyContainerCreateRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.property.model.PropertyContainerDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.util.UUID

@Service
class PropertyService(
  private val propertyDpsApiService: PropertyDpsApiService,
  private val propertyNomisApiService: PropertyNomisApiService,
  private val propertyMappingService: PropertyMappingService,
  private val propertyRetryQueueService: PropertyRetryQueueService,
  private val telemetryClient: TelemetryClient,
  private val mappingApi: MappingApiHealth,
) : CreateMappingRetryable {
  suspend fun created(event: PropertyDomainEvent) {
    val telemetry = event.asTelemetry()
    if (event.source == "DPS") {
      synchronise {
        name = "property"
        telemetryClient = this@PropertyService.telemetryClient
        retryQueueService = propertyRetryQueueService
        eventTelemetry = telemetry

        checkMappingDoesNotExist {
          propertyMappingService.getMappingByDpsIdOrNull(event.additionalInformation.dpsId)
        }
        transform {
          propertyDpsApiService.getProperty(UUID.fromString(event.additionalInformation.dpsId)).run {
            val request = toNomisRequest()

            eventTelemetry += "locationId" to request.internalLocationId.toString()

            propertyNomisApiService.createProperty(request).run {
              PropertyContainerMappingDto(
                dpsPropertyContainerId = event.additionalInformation.dpsId,
                nomisPropertyContainerId = propertyContainerId,
                bookingId = bookingId,
                offenderNo = event.prisonerNumber,
                mappingType = PropertyContainerMappingDto.MappingType.DPS_CREATED,
              )
            }
          }
        }
        saveMapping { propertyMappingService.create(it) }
      }
    }
  }

  suspend fun updated(event: PropertyDomainEvent) {
    TODO("Not yet implemented")
  }

  override suspend fun retryCreateMapping(message: String) {
    TODO("Not yet implemented")
  }

  private suspend fun PropertyContainerDto.toNomisRequest() = PropertyContainerCreateRequest(
    offenderNo = prisonerNumber,
    prisonId = prisonId,
    active = true,
    sealMark = currentSealNumber!!,
    containerCode = toNomisContainerCode(),
    internalLocationId = toNomisLocation(),
    proposedDisposalDate = proposedDisposalDate,
  )

  private suspend fun PropertyContainerDto.toNomisLocation(): Long? = currentLocation
    ?.let {
      propertyMappingService.getNomisLocation(currentLocation.toString()).nomisLocationId
    }
}

fun PropertyDomainEvent.asTelemetry() = mutableMapOf(
  "changedFields" to additionalInformation.changedFields.toString(),
  "dpsId" to additionalInformation.dpsId,
  "offenderNo" to prisonerNumber,
  "source" to source.toString(),
)

private fun PropertyContainerDto.toNomisContainerCode(): PropertyContainerCode = when (
  this.currentLocationType
) {
  PropertyContainerDto.CurrentLocationType.BRANSTON -> PropertyContainerCode.BRA
  PropertyContainerDto.CurrentLocationType.INTERNAL ->
    when (this.containerType) {
      PropertyContainerDto.ContainerType.STANDARD -> PropertyContainerCode.BULK
      PropertyContainerDto.ContainerType.EXCESS -> PropertyContainerCode.DES // TODO
      PropertyContainerDto.ContainerType.VALUABLES -> PropertyContainerCode.VALU
      PropertyContainerDto.ContainerType.CONFISCATED -> PropertyContainerCode.CO
    }

  null -> PropertyContainerCode.BULK // TODO
}
