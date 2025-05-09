package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable

@Service
class OrganisationsService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: OrganisationsNomisApiService,
  private val mappingApiService: OrganisationsMappingApiService,
) : CreateMappingRetryable {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun organisationCreated(event: OrganisationEvent) = log.debug("Received organisation created event for organisation {}", event.organisationId)
  suspend fun organisationUpdated(event: OrganisationEvent) = log.debug("Received organisation updated event for organisation {}", event.organisationId)

  suspend fun organisationDeleted(event: OrganisationDeletedEvent) {
    log.debug("Received organisation deleted event for organisation {}", event.organisationId)
    val dpsOrganisationId = event.organisationId.toString()
    val telemetryMap = mutableMapOf("dpsOrganisationId" to dpsOrganisationId)
    if (event.originatedInDPS()) {
      runCatching {
        mappingApiService.getByDpsOrganisationIdOrNull(dpsOrganisationId)?.also { mapping ->
          telemetryMap["nomisOrganisationId"] = mapping.nomisId.toString()
          nomisApiService.deleteCorporateOrganisation(corporateId = mapping.nomisId)
          tryToDeleteMapping(dpsOrganisationId)
          telemetryClient.trackEvent("organisation-deleted-success", telemetryMap)
        } ?: also {
          telemetryClient.trackEvent("organisation-deleted-skipped", telemetryMap)
        }
      }.onFailure { e ->
        telemetryClient.trackEvent("organisation-deleted-failed", telemetryMap)
        throw e
      }
    } else {
      telemetryClient.trackEvent("organisation-deleted-ignored", telemetryMap)
    }
  }

  suspend fun organisationTypesUpdated(event: OrganisationEvent) = log.debug("Received organisation types updated event for {}", event.organisationId)
  suspend fun organisationAddressCreated(event: OrganisationAddressEvent) = log.debug("Received organisation address created event for {} on organisation {}", event.addressId, event.organisationId)
  suspend fun organisationAddressUpdated(event: OrganisationAddressEvent) = log.debug("Received organisation address updated event for {} on organisation {}", event.addressId, event.organisationId)
  suspend fun organisationAddressDeleted(event: OrganisationAddressEvent) = log.debug("Received organisation address deleted event for {} on organisation {}", event.addressId, event.organisationId)
  suspend fun organisationWebCreated(event: OrganisationWebEvent) = log.debug("Received organisation web created event for {} on organisation {}", event.webId, event.organisationId)
  suspend fun organisationWebUpdated(event: OrganisationWebEvent) = log.debug("Received organisation web updated event for {} on organisation {}", event.webId, event.organisationId)
  suspend fun organisationWebDeleted(event: OrganisationWebEvent) = log.debug("Received organisation web deleted event for {} on organisation {}", event.webId, event.organisationId)
  suspend fun organisationPhoneCreated(event: OrganisationPhoneEvent) = log.debug("Received organisation phone created event for {} on organisation {}", event.phoneId, event.organisationId)
  suspend fun organisationPhoneUpdated(event: OrganisationPhoneEvent) = log.debug("Received organisation phone updated event for {} on organisation {}", event.phoneId, event.organisationId)
  suspend fun organisationPhoneDeleted(event: OrganisationPhoneEvent) = log.debug("Received organisation phone deleted event for {} on organisation {}", event.phoneId, event.organisationId)
  suspend fun organisationAddressPhoneCreated(event: OrganisationAddressPhoneEvent) = log.debug("Received organisation address phone created event for {} on organisation {}", event.addressPhoneId, event.organisationId)
  suspend fun organisationAddressPhoneUpdated(event: OrganisationAddressPhoneEvent) = log.debug("Received organisation address phone updated event for {} on organisation {}", event.addressPhoneId, event.organisationId)
  suspend fun organisationAddressPhoneDeleted(event: OrganisationAddressPhoneEvent) = log.debug("Received organisation address phone deleted event for {} on organisation {}", event.addressPhoneId, event.organisationId)
  suspend fun organisationEmailCreated(event: OrganisationEmailEvent) = log.debug("Received organisation email created event for {} on organisation {}", event.emailId, event.organisationId)
  suspend fun organisationEmailUpdated(event: OrganisationEmailEvent) = log.debug("Received organisation email updated event for {} on organisation {}", event.emailId, event.organisationId)
  suspend fun organisationEmailDeleted(event: OrganisationEmailEvent) = log.debug("Received organisation email deleted event for {} on organisation {}", event.emailId, event.organisationId)

  private suspend fun tryToDeleteMapping(dpsOrganisationId: String) = kotlin.runCatching {
    mappingApiService.deleteByDpsOrganisationId(dpsOrganisationId)
  }.onFailure { e ->
    telemetryClient.trackEvent("organisation-mapping-deleted-failed", mapOf("dpsOrganisationId" to dpsOrganisationId.toString()))
    log.warn("Unable to delete mapping for organisation $dpsOrganisationId. Please delete manually", e)
  }

  override suspend fun retryCreateMapping(message: String) {
    TODO("Not yet implemented")
  }
}

enum class OrganisationSource {
  DPS,
  NOMIS,
}

private fun SourcedOrganisationsEvent.originatedInDPS() = this.additionalInformation.source == "DPS"
