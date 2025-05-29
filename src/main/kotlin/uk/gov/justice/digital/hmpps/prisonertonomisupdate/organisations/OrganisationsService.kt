package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OrganisationsMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporatePhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCorporatePhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsService.Companion.MappingTypes.ORGANISATION_ADDRESS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsService.Companion.MappingTypes.ORGANISATION_ADDRESS_PHONE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsService.Companion.MappingTypes.ORGANISATION_EMAIL
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsService.Companion.MappingTypes.ORGANISATION_PHONE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsService.Companion.MappingTypes.ORGANISATION_WEB
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncPhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise

@Service
class OrganisationsService(
  private val nomisApiService: OrganisationsNomisApiService,
  private val dpsApiService: OrganisationsDpsApiService,
  private val mappingApiService: OrganisationsMappingApiService,
  private val organisationsRetryQueueService: OrganisationsRetryQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {
  private companion object {
    enum class MappingTypes(val entityName: String) {
      ORGANISATION("organisation"),
      ORGANISATION_TYPES("organisation-types"),
      ORGANISATION_ADDRESS("organisation-address"),
      ORGANISATION_EMAIL("organisation-email"),
      ORGANISATION_WEB("organisation-web"),
      ORGANISATION_PHONE("organisation-phone"),
      ORGANISATION_ADDRESS_PHONE("organisation-address-phone"),
      ;

      companion object {
        fun fromEntityName(entityName: String) = entries.find { it.entityName == entityName }
          ?: throw IllegalStateException("Mapping type $entityName does not exist")
      }
    }

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

  suspend fun organisationPhoneCreated(event: OrganisationPhoneEvent) {
    val entityName = ORGANISATION_PHONE.entityName
    val dpsOrganisationPhoneId = event.phoneId
    val telemetryMap = mutableMapOf(
      "dpsOrganisationPhoneId" to dpsOrganisationPhoneId.toString(),
    )

    if (event.didOriginateInDPS()) {
      synchronise {
        name = entityName
        telemetryClient = this@OrganisationsService.telemetryClient
        retryQueueService = organisationsRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getByDpsPhoneIdOrNull(dpsOrganisationPhoneId)
        }
        transform {
          val dpsPhone = dpsApiService.getSyncOrganisationPhone(dpsOrganisationPhoneId).also {
            telemetryMap["dpsPhoneId"] = it.organisationPhoneId.toString()
            telemetryMap["organisationId"] = it.organisationId.toString()
          }
          nomisApiService.createCorporatePhone(dpsPhone.organisationId, dpsPhone.toNomisCreateRequest()).also {
            telemetryMap["nomisPhoneId"] = it.id.toString()
          }.let {
            OrganisationsMappingDto(
              dpsId = dpsOrganisationPhoneId.toString(),
              nomisId = it.id,
              mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
            )
          }
        }
        saveMapping { mappingApiService.createPhoneMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("$entityName-create-ignored", telemetryMap)
    }
  }
  suspend fun organisationPhoneUpdated(event: OrganisationPhoneEvent) {
    val entityName = ORGANISATION_PHONE.entityName
    val dpsPhoneId = event.phoneId
    val telemetryMap = mutableMapOf(
      "dpsPhoneId" to dpsPhoneId.toString(),
    )

    if (event.didOriginateInDPS()) {
      val nomisPhoneId = mappingApiService.getByDpsPhoneId(dpsPhoneId).nomisId.also {
        telemetryMap["nomisPhoneId"] = it.toString()
      }
      val dpsPhone = dpsApiService.getSyncOrganisationPhone(dpsPhoneId).also {
        telemetryMap["organisationId"] = it.organisationId.toString()
        telemetryMap["dpsPhoneId"] = it.organisationPhoneId.toString()
      }
      nomisApiService.updateCorporatePhone(
        corporateId = dpsPhone.organisationId,
        phoneId = nomisPhoneId,
        dpsPhone.toNomisUpdateRequest(),
      )
      telemetryClient.trackEvent("$entityName-update-success", telemetryMap)
    } else {
      telemetryClient.trackEvent("$entityName-update-ignored", telemetryMap)
    }
  }

  suspend fun organisationPhoneDeleted(event: OrganisationPhoneEvent) {
    val entityName = ORGANISATION_PHONE.entityName
    val dpsPhoneId = event.phoneId
    val organisationId = event.organisationId
    val telemetryMap = mutableMapOf(
      "dpsPhoneId" to dpsPhoneId.toString(),
      "organisationId" to organisationId.toString(),
    )

    if (event.didOriginateInDPS()) {
      mappingApiService.getByDpsPhoneIdOrNull(dpsPhoneId)?.nomisId?.also {
        telemetryMap["nomisPhoneId"] = it.toString()
        nomisApiService.deleteCorporatePhone(
          corporateId = organisationId,
          phoneId = it,
        )
        mappingApiService.deleteByNomisPhoneId(nomisPhoneId = it)
        telemetryClient.trackEvent("$entityName-delete-success", telemetryMap)
      } ?: run {
        telemetryClient.trackEvent("$entityName-delete-skipped", telemetryMap)
      }
    } else {
      telemetryClient.trackEvent("$entityName-delete-ignored", telemetryMap)
    }
  }

  suspend fun organisationAddressPhoneCreated(event: OrganisationAddressPhoneEvent) = log.debug("Received organisation address phone created event for {} on organisation {}", event.addressPhoneId, event.organisationId)
  suspend fun organisationAddressPhoneUpdated(event: OrganisationAddressPhoneEvent) = log.debug("Received organisation address phone updated event for {} on organisation {}", event.addressPhoneId, event.organisationId)
  suspend fun organisationAddressPhoneDeleted(event: OrganisationAddressPhoneEvent) = log.debug("Received organisation address phone deleted event for {} on organisation {}", event.addressPhoneId, event.organisationId)
  suspend fun organisationEmailCreated(event: OrganisationEmailEvent) = log.debug("Received organisation email created event for {} on organisation {}", event.emailId, event.organisationId)
  suspend fun organisationEmailUpdated(event: OrganisationEmailEvent) = log.debug("Received organisation email updated event for {} on organisation {}", event.emailId, event.organisationId)
  suspend fun organisationEmailDeleted(event: OrganisationEmailEvent) = log.debug("Received organisation email deleted event for {} on organisation {}", event.emailId, event.organisationId)

  private suspend fun tryToDeleteMapping(dpsOrganisationId: String) = kotlin.runCatching {
    mappingApiService.deleteByDpsOrganisationId(dpsOrganisationId)
  }.onFailure { e ->
    telemetryClient.trackEvent("organisation-mapping-deleted-failed", mapOf("dpsOrganisationId" to dpsOrganisationId))
    log.warn("Unable to delete mapping for organisation $dpsOrganisationId. Please delete manually", e)
  }

  override suspend fun retryCreateMapping(message: String) {
    val baseMapping: CreateMappingRetryMessage<*> = message.fromJson()
    when (MappingTypes.fromEntityName(baseMapping.entityName)) {
      ORGANISATION_ADDRESS -> createAddressMapping(message.fromJson())
      ORGANISATION_PHONE -> createPhoneMapping(message.fromJson())
      ORGANISATION_EMAIL -> createEmailMapping(message.fromJson())
      ORGANISATION_WEB -> createWebMapping(message.fromJson())
      ORGANISATION_ADDRESS_PHONE -> createAddressPhoneMapping(message.fromJson())
      else -> log.info("Received a message I wasn't expecting: {}", baseMapping.entityName)
    }
  }

  suspend fun createAddressMapping(message: CreateMappingRetryMessage<OrganisationsMappingDto>) {
    mappingApiService.createAddressMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "${ORGANISATION_ADDRESS.entityName}-create-success",
        message.telemetryAttributes,
      )
    }
  }
  suspend fun createEmailMapping(message: CreateMappingRetryMessage<OrganisationsMappingDto>) {
    mappingApiService.createEmailMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "${ORGANISATION_EMAIL.entityName}-create-success",
        message.telemetryAttributes,
      )
    }
  }
  suspend fun createWebMapping(message: CreateMappingRetryMessage<OrganisationsMappingDto>) {
    mappingApiService.createWebMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "${ORGANISATION_WEB.entityName}-create-success",
        message.telemetryAttributes,
      )
    }
  }
  suspend fun createPhoneMapping(message: CreateMappingRetryMessage<OrganisationsMappingDto>) {
    mappingApiService.createPhoneMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "${ORGANISATION_PHONE.entityName}-create-success",
        message.telemetryAttributes,
      )
    }
  }

  suspend fun createAddressPhoneMapping(message: CreateMappingRetryMessage<OrganisationsMappingDto>) {
    mappingApiService.createPhoneMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "${ORGANISATION_ADDRESS_PHONE.entityName}-create-success",
        message.telemetryAttributes,
      )
    }
  }

  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)
}

enum class OrganisationSource {
  DPS,
  NOMIS,
}

private fun SourcedOrganisationsEvent.originatedInDPS() = this.additionalInformation.source == "DPS"
private fun SourcedOrganisationChildEvent.didOriginateInDPS() = this.additionalInformation.source == "DPS"

private fun SyncPhoneResponse.toNomisCreateRequest(): CreateCorporatePhoneRequest = CreateCorporatePhoneRequest(
  number = this.phoneNumber,
  extension = this.extNumber,
  typeCode = this.phoneType,
)
private fun SyncPhoneResponse.toNomisUpdateRequest(): UpdateCorporatePhoneRequest = UpdateCorporatePhoneRequest(
  number = this.phoneNumber,
  extension = this.extNumber,
  typeCode = this.phoneType,
)
