package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.OrganisationsMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporateAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporateEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporatePhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCorporateWebAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCorporateAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCorporateEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCorporatePhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCorporateTypesRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCorporateWebAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsService.Companion.MappingTypes.ORGANISATION_ADDRESS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsService.Companion.MappingTypes.ORGANISATION_ADDRESS_PHONE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsService.Companion.MappingTypes.ORGANISATION_EMAIL
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsService.Companion.MappingTypes.ORGANISATION_PHONE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsService.Companion.MappingTypes.ORGANISATION_TYPES
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsService.Companion.MappingTypes.ORGANISATION_WEB
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncAddressPhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncEmailResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncPhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncTypesResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncWebResponse
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
    if (event.didOriginateInDPS()) {
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

  suspend fun organisationTypesUpdated(event: OrganisationEvent) {
    val entityName = ORGANISATION_TYPES.entityName
    val organisationId = event.organisationId
    val telemetryMap = mutableMapOf(
      "organisationId" to organisationId.toString(),
    )

    if (event.didOriginateInDPS()) {
      val dpsTypes = dpsApiService.getSyncOrganisationTypes(organisationId)
      nomisApiService.updateCorporateTypes(
        corporateId = organisationId,
        request = dpsTypes.toNomisUpdateRequest(),
      )
      telemetryClient.trackEvent("$entityName-update-success", telemetryMap)
    } else {
      telemetryClient.trackEvent("$entityName-update-ignored", telemetryMap)
    }
  }

  suspend fun organisationAddressCreated(event: OrganisationAddressEvent) {
    val entityName = ORGANISATION_ADDRESS.entityName
    val dpsOrganisationAddressId = event.addressId
    val telemetryMap = mutableMapOf(
      "dpsOrganisationAddressId" to dpsOrganisationAddressId.toString(),
    )

    if (event.didOriginateInDPS()) {
      synchronise {
        name = entityName
        telemetryClient = this@OrganisationsService.telemetryClient
        retryQueueService = organisationsRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getByDpsAddressIdOrNull(dpsOrganisationAddressId)
        }
        transform {
          val dpsAddress = dpsApiService.getSyncOrganisationAddress(dpsOrganisationAddressId).also {
            telemetryMap["dpsAddressId"] = it.organisationAddressId.toString()
            telemetryMap["organisationId"] = it.organisationId.toString()
          }
          nomisApiService.createCorporateAddress(dpsAddress.organisationId, dpsAddress.toNomisCreateRequest()).also {
            telemetryMap["nomisAddressId"] = it.id.toString()
          }.let {
            OrganisationsMappingDto(
              dpsId = dpsOrganisationAddressId.toString(),
              nomisId = it.id,
              mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
            )
          }
        }
        saveMapping { mappingApiService.createAddressMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("$entityName-create-ignored", telemetryMap)
    }
  }
  suspend fun organisationAddressUpdated(event: OrganisationAddressEvent) {
    val entityName = ORGANISATION_ADDRESS.entityName
    val dpsAddressId = event.addressId
    val telemetryMap = mutableMapOf(
      "dpsAddressId" to dpsAddressId.toString(),
    )

    if (event.didOriginateInDPS()) {
      val nomisAddressId = mappingApiService.getByDpsAddressId(dpsAddressId).nomisId.also {
        telemetryMap["nomisAddressId"] = it.toString()
      }
      val dpsAddress = dpsApiService.getSyncOrganisationAddress(dpsAddressId).also {
        telemetryMap["organisationId"] = it.organisationId.toString()
        telemetryMap["dpsAddressId"] = it.organisationAddressId.toString()
      }
      nomisApiService.updateCorporateAddress(
        corporateId = dpsAddress.organisationId,
        addressId = nomisAddressId,
        dpsAddress.toNomisUpdateRequest(),
      )
      telemetryClient.trackEvent("$entityName-update-success", telemetryMap)
    } else {
      telemetryClient.trackEvent("$entityName-update-ignored", telemetryMap)
    }
  }
  suspend fun organisationAddressDeleted(event: OrganisationAddressEvent) {
    val entityName = ORGANISATION_ADDRESS.entityName
    val dpsAddressId = event.addressId
    val organisationId = event.organisationId
    val telemetryMap = mutableMapOf(
      "dpsAddressId" to dpsAddressId.toString(),
      "organisationId" to organisationId.toString(),
    )

    if (event.didOriginateInDPS()) {
      mappingApiService.getByDpsAddressIdOrNull(dpsAddressId)?.nomisId?.also {
        telemetryMap["nomisAddressId"] = it.toString()
        nomisApiService.deleteCorporateAddress(
          corporateId = organisationId,
          addressId = it,
        )
        mappingApiService.deleteByNomisAddressId(nomisAddressId = it)
        telemetryClient.trackEvent("$entityName-delete-success", telemetryMap)
      } ?: run {
        telemetryClient.trackEvent("$entityName-delete-skipped", telemetryMap)
      }
    } else {
      telemetryClient.trackEvent("$entityName-delete-ignored", telemetryMap)
    }
  }

  suspend fun organisationWebCreated(event: OrganisationWebEvent) {
    val entityName = ORGANISATION_WEB.entityName
    val dpsOrganisationWebId = event.webId
    val telemetryMap = mutableMapOf(
      "dpsOrganisationWebId" to dpsOrganisationWebId.toString(),
    )

    if (event.didOriginateInDPS()) {
      synchronise {
        name = entityName
        telemetryClient = this@OrganisationsService.telemetryClient
        retryQueueService = organisationsRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getByDpsWebIdOrNull(dpsOrganisationWebId)
        }
        transform {
          val dpsWeb = dpsApiService.getSyncOrganisationWeb(dpsOrganisationWebId).also {
            telemetryMap["dpsWebId"] = it.organisationWebAddressId.toString()
            telemetryMap["organisationId"] = it.organisationId.toString()
          }
          nomisApiService.createCorporateWebAddress(dpsWeb.organisationId, dpsWeb.toNomisCreateRequest()).also {
            telemetryMap["nomisWebId"] = it.id.toString()
          }.let {
            OrganisationsMappingDto(
              dpsId = dpsOrganisationWebId.toString(),
              nomisId = it.id,
              mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
            )
          }
        }
        saveMapping { mappingApiService.createWebMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("$entityName-create-ignored", telemetryMap)
    }
  }
  suspend fun organisationWebUpdated(event: OrganisationWebEvent) {
    val entityName = ORGANISATION_WEB.entityName
    val dpsWebId = event.webId
    val telemetryMap = mutableMapOf(
      "dpsWebId" to dpsWebId.toString(),
    )

    if (event.didOriginateInDPS()) {
      val nomisWebId = mappingApiService.getByDpsWebId(dpsWebId).nomisId.also {
        telemetryMap["nomisWebId"] = it.toString()
      }
      val dpsWeb = dpsApiService.getSyncOrganisationWeb(dpsWebId).also {
        telemetryMap["organisationId"] = it.organisationId.toString()
        telemetryMap["dpsWebId"] = it.organisationWebAddressId.toString()
      }
      nomisApiService.updateCorporateWebAddress(
        corporateId = dpsWeb.organisationId,
        webAddressId = nomisWebId,
        dpsWeb.toNomisUpdateRequest(),
      )
      telemetryClient.trackEvent("$entityName-update-success", telemetryMap)
    } else {
      telemetryClient.trackEvent("$entityName-update-ignored", telemetryMap)
    }
  }
  suspend fun organisationWebDeleted(event: OrganisationWebEvent) {
    val entityName = ORGANISATION_WEB.entityName
    val dpsWebId = event.webId
    val organisationId = event.organisationId
    val telemetryMap = mutableMapOf(
      "dpsWebId" to dpsWebId.toString(),
      "organisationId" to organisationId.toString(),
    )

    if (event.didOriginateInDPS()) {
      mappingApiService.getByDpsWebIdOrNull(dpsWebId)?.nomisId?.also {
        telemetryMap["nomisWebId"] = it.toString()
        nomisApiService.deleteCorporateWebAddress(
          corporateId = organisationId,
          webAddressId = it,
        )
        mappingApiService.deleteByNomisWebId(nomisWebId = it)
        telemetryClient.trackEvent("$entityName-delete-success", telemetryMap)
      } ?: run {
        telemetryClient.trackEvent("$entityName-delete-skipped", telemetryMap)
      }
    } else {
      telemetryClient.trackEvent("$entityName-delete-ignored", telemetryMap)
    }
  }

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

  suspend fun organisationEmailCreated(event: OrganisationEmailEvent) {
    val entityName = ORGANISATION_EMAIL.entityName
    val dpsOrganisationEmailId = event.emailId
    val telemetryMap = mutableMapOf(
      "dpsOrganisationEmailId" to dpsOrganisationEmailId.toString(),
    )

    if (event.didOriginateInDPS()) {
      synchronise {
        name = entityName
        telemetryClient = this@OrganisationsService.telemetryClient
        retryQueueService = organisationsRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getByDpsEmailIdOrNull(dpsOrganisationEmailId)
        }
        transform {
          val dpsEmail = dpsApiService.getSyncOrganisationEmail(dpsOrganisationEmailId).also {
            telemetryMap["dpsEmailId"] = it.organisationEmailId.toString()
            telemetryMap["organisationId"] = it.organisationId.toString()
          }
          nomisApiService.createCorporateEmail(dpsEmail.organisationId, dpsEmail.toNomisCreateRequest()).also {
            telemetryMap["nomisEmailId"] = it.id.toString()
          }.let {
            OrganisationsMappingDto(
              dpsId = dpsOrganisationEmailId.toString(),
              nomisId = it.id,
              mappingType = OrganisationsMappingDto.MappingType.DPS_CREATED,
            )
          }
        }
        saveMapping { mappingApiService.createEmailMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("$entityName-create-ignored", telemetryMap)
    }
  }
  suspend fun organisationEmailUpdated(event: OrganisationEmailEvent) {
    val entityName = ORGANISATION_EMAIL.entityName
    val dpsEmailId = event.emailId
    val telemetryMap = mutableMapOf(
      "dpsEmailId" to dpsEmailId.toString(),
    )

    if (event.didOriginateInDPS()) {
      val nomisEmailId = mappingApiService.getByDpsEmailId(dpsEmailId).nomisId.also {
        telemetryMap["nomisEmailId"] = it.toString()
      }
      val dpsEmail = dpsApiService.getSyncOrganisationEmail(dpsEmailId).also {
        telemetryMap["organisationId"] = it.organisationId.toString()
        telemetryMap["dpsEmailId"] = it.organisationEmailId.toString()
      }
      nomisApiService.updateCorporateEmail(
        corporateId = dpsEmail.organisationId,
        emailId = nomisEmailId,
        dpsEmail.toNomisUpdateRequest(),
      )
      telemetryClient.trackEvent("$entityName-update-success", telemetryMap)
    } else {
      telemetryClient.trackEvent("$entityName-update-ignored", telemetryMap)
    }
  }
  suspend fun organisationEmailDeleted(event: OrganisationEmailEvent) {
    val entityName = ORGANISATION_EMAIL.entityName
    val dpsEmailId = event.emailId
    val organisationId = event.organisationId
    val telemetryMap = mutableMapOf(
      "dpsEmailId" to dpsEmailId.toString(),
      "organisationId" to organisationId.toString(),
    )

    if (event.didOriginateInDPS()) {
      mappingApiService.getByDpsEmailIdOrNull(dpsEmailId)?.nomisId?.also {
        telemetryMap["nomisEmailId"] = it.toString()
        nomisApiService.deleteCorporateEmail(
          corporateId = organisationId,
          emailId = it,
        )
        mappingApiService.deleteByNomisEmailId(nomisEmailId = it)
        telemetryClient.trackEvent("$entityName-delete-success", telemetryMap)
      } ?: run {
        telemetryClient.trackEvent("$entityName-delete-skipped", telemetryMap)
      }
    } else {
      telemetryClient.trackEvent("$entityName-delete-ignored", telemetryMap)
    }
  }

  private suspend fun tryToDeleteMapping(dpsOrganisationId: String) = runCatching {
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

private fun SourcedOrganisationsEvent.didOriginateInDPS() = this.additionalInformation.source == "DPS"
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
private fun SyncAddressResponse.toNomisCreateRequest(): CreateCorporateAddressRequest = CreateCorporateAddressRequest(
  primaryAddress = this.primaryAddress,
  noFixedAddress = this.noFixedAddress,
  mailAddress = this.mailAddress,
  startDate = this.startDate!!,
  isServices = this.serviceAddress,
  typeCode = this.addressType,
  flat = this.flat,
  premise = this.property,
  street = this.street,
  locality = this.area,
  postcode = this.postcode,
  cityCode = this.cityCode,
  countyCode = this.countyCode,
  countryCode = this.countryCode,
  comment = this.comments,
  endDate = this.endDate,
  businessHours = this.businessHours,
  contactPersonName = this.contactPersonName,

)
private fun SyncAddressResponse.toNomisUpdateRequest(): UpdateCorporateAddressRequest = UpdateCorporateAddressRequest(
  primaryAddress = this.primaryAddress,
  noFixedAddress = this.noFixedAddress,
  mailAddress = this.mailAddress,
  startDate = this.startDate!!,
  isServices = this.serviceAddress,
  typeCode = this.addressType,
  flat = this.flat,
  premise = this.property,
  street = this.street,
  locality = this.area,
  postcode = this.postcode,
  cityCode = this.cityCode,
  countyCode = this.countyCode,
  countryCode = this.countryCode,
  comment = this.comments,
  endDate = this.endDate,
  businessHours = this.businessHours,
  contactPersonName = this.contactPersonName,
)
private fun SyncEmailResponse.toNomisCreateRequest(): CreateCorporateEmailRequest = CreateCorporateEmailRequest(
  email = this.emailAddress,
)
private fun SyncEmailResponse.toNomisUpdateRequest(): UpdateCorporateEmailRequest = UpdateCorporateEmailRequest(
  email = this.emailAddress,
)
private fun SyncWebResponse.toNomisCreateRequest(): CreateCorporateWebAddressRequest = CreateCorporateWebAddressRequest(
  webAddress = this.webAddress,
)
private fun SyncWebResponse.toNomisUpdateRequest(): UpdateCorporateWebAddressRequest = UpdateCorporateWebAddressRequest(
  webAddress = this.webAddress,
)
private fun SyncAddressPhoneResponse.toNomisCreateRequest(): CreateCorporatePhoneRequest = CreateCorporatePhoneRequest(
  number = this.phoneNumber,
  extension = this.extNumber,
  typeCode = this.phoneType,
)
private fun SyncAddressPhoneResponse.toNomisUpdateRequest(): UpdateCorporatePhoneRequest = UpdateCorporatePhoneRequest(
  number = this.phoneNumber,
  extension = this.extNumber,
  typeCode = this.phoneType,
)
private fun SyncTypesResponse.toNomisUpdateRequest(): UpdateCorporateTypesRequest = UpdateCorporateTypesRequest(
  typeCodes = this.types.map { it.type }.toSet(),
)
