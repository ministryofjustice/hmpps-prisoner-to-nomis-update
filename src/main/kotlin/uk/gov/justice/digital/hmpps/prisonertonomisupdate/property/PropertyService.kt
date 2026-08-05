package uk.gov.justice.digital.hmpps.prisonertonomisupdate.property

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PropertyContainerMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PropertyContainerCode
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PropertyContainerCreateRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PropertyContainerUpdateRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.property.model.PropertyContainerDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.track
import java.util.UUID

@Service
class PropertyService(
  private val propertyDpsApiService: PropertyDpsApiService,
  private val propertyNomisApiService: PropertyNomisApiService,
  private val propertyMappingService: PropertyMappingService,
  private val propertyRetryQueueService: PropertyRetryQueueService,
  override val telemetryClient: TelemetryClient,
) : CreateMappingRetryable,
  TelemetryEnabled {
  suspend fun created(event: PropertyDomainEvent) {
    val telemetry = event.asTelemetry()
    if (event.originatedInDps()) {
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
            val request = toNomisCreateRequest()

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
    } else {
      telemetryClient.trackEvent("property-create-ignored", telemetry, null)
    }
  }

  suspend fun updated(event: PropertyDomainEvent) {
    val telemetry = event.asTelemetry()
    if (event.originatedInDps() && event.supportedFieldUpdated()) {
      track("property-update", telemetry) {
        val dpsId = event.additionalInformation.dpsId
        val dpsData = propertyDpsApiService.getProperty(UUID.fromString(dpsId))
        val mapping = propertyMappingService.getMappingByDpsId(dpsId)
        val nomisId = mapping.nomisPropertyContainerId
        telemetry += "nomisPropertyContainerId" to nomisId.toString()

        propertyNomisApiService.updateProperty(nomisId, dpsData.toNomisUpdateRequest())
      }
    } else {
      telemetryClient.trackEvent("property-update-ignored", telemetry, null)
    }
  }

  suspend fun PropertyContainerDto.toNomisUpdateRequest() = PropertyContainerUpdateRequest(
    sealMark = currentSealNumber,
    containerCode = toNomisContainerCode(),
    internalLocationId = toNomisLocation(),
    proposedDisposalDate = proposedDisposalDate,
    active = removalOutcome == null,
    expiryDate = removalDate,
  )

  override suspend fun retryCreateMapping(message: String) {
    TODO("Not yet implemented")
  }

  private suspend fun PropertyContainerDto.toNomisCreateRequest() = PropertyContainerCreateRequest(
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

private val supportedFields = setOf(
  "sealNumber",
  "location",
  "containerType",
  "proposedDisposalDate",
  "removalOutcome",
)

private fun PropertyDomainEvent.supportedFieldUpdated(): Boolean = additionalInformation
  .changedFields?.let { supportedFields.intersect(it.toSet()).isNotEmpty() } == true

private fun PropertyDomainEvent.originatedInDps(): Boolean = source == "DPS"

fun PropertyDomainEvent.asTelemetry() = mutableMapOf(
  "dpsPropertyContainerId" to additionalInformation.dpsId,
  "offenderNo" to prisonerNumber,
  "changedFields" to additionalInformation.changedFields.toString(),
  "source" to source.toString(),
)

private fun PropertyContainerDto.toNomisContainerCode(): PropertyContainerCode = when (
  this.currentLocationType
) {
  PropertyContainerDto.CurrentLocationType.BRANSTON -> PropertyContainerCode.BRA
  PropertyContainerDto.CurrentLocationType.INTERNAL ->
    when (this.containerType) {
      PropertyContainerDto.ContainerType.STANDARD -> PropertyContainerCode.BULK
      PropertyContainerDto.ContainerType.EXCESS -> PropertyContainerCode.BRA
      PropertyContainerDto.ContainerType.VALUABLES -> PropertyContainerCode.VALU
      PropertyContainerDto.ContainerType.CONFISCATED -> PropertyContainerCode.CO
    }

  null -> PropertyContainerCode.BULK
}
