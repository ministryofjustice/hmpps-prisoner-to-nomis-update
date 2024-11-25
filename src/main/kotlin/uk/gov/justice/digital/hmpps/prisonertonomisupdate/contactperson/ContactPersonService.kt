package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonService.Companion.MappingTypes.CONTACT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonService.Companion.MappingTypes.CONTACT_ADDRESS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonService.Companion.MappingTypes.CONTACT_PERSON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncContactAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncPrisonerContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonMappingDto.MappingType.DPS_CREATED
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonContactRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise

@Service
class ContactPersonService(
  private val mappingApiService: ContactPersonMappingApiService,
  private val nomisApiService: ContactPersonNomisApiService,
  private val dpsApiService: ContactPersonDpsApiService,
  private val contactPersonRetryQueueService: ContactPersonRetryQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {
  companion object {
    enum class MappingTypes(val entityName: String) {
      CONTACT_PERSON("contactperson"),
      CONTACT("contact"),
      CONTACT_ADDRESS("contact-address"),
      ;

      companion object {
        fun fromEntityName(entityName: String) = entries.find { it.entityName == entityName } ?: throw IllegalStateException("Mapping type $entityName does not exist")
      }
    }
  }

  suspend fun contactCreated(event: ContactCreatedEvent) {
    val entityName = CONTACT_PERSON.entityName

    val dpsContactId = event.additionalInformation.contactId
    val telemetryMap = mutableMapOf(
      "dpsContactId" to dpsContactId.toString(),
    )

    if (event.didOriginateInDPS()) {
      synchronise {
        name = entityName
        telemetryClient = this@ContactPersonService.telemetryClient
        retryQueueService = contactPersonRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getByDpsContactIdOrNull(dpsContactId)
        }
        transform {
          val nomisPersonId = nomisApiService.createPerson(dpsApiService.getContact(dpsContactId).toNomisCreateRequest()).personId
          telemetryMap["nomisPersonId"] = nomisPersonId.toString()
          PersonMappingDto(
            dpsId = dpsContactId.toString(),
            nomisId = nomisPersonId,
            mappingType = DPS_CREATED,
          )
        }
        saveMapping { mappingApiService.createPersonMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("$entityName-create-ignored", telemetryMap)
    }
  }

  suspend fun prisonerContactCreated(event: PrisonerContactCreatedEvent) {
    val entityName = CONTACT.entityName
    val dpsPrisonerContactId = event.additionalInformation.prisonerContactId
    val telemetryMap = mutableMapOf(
      "dpsPrisonerContactId" to dpsPrisonerContactId.toString(),
    )

    if (event.didOriginateInDPS()) {
      synchronise {
        name = entityName
        telemetryClient = this@ContactPersonService.telemetryClient
        retryQueueService = contactPersonRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getByDpsPrisonerContactIdOrNull(dpsPrisonerContactId)
        }
        transform {
          val dpsContact = dpsApiService.getPrisonerContact(dpsPrisonerContactId).also {
            telemetryMap["offenderNo"] = it.prisonerNumber
            telemetryMap["dpsContactId"] = it.contactId.toString()
            telemetryMap["nomisPersonId"] = it.contactId.toString()
          }
          nomisApiService.createPersonContact(dpsContact.contactId, dpsContact.toNomisCreateRequest()).also {
            telemetryMap["nomisContactId"] = it.personContactId.toString()
          }.let {
            PersonContactMappingDto(
              dpsId = dpsPrisonerContactId.toString(),
              nomisId = it.personContactId,
              mappingType = PersonContactMappingDto.MappingType.DPS_CREATED,
            )
          }
        }
        saveMapping { mappingApiService.createContactMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("$entityName-create-ignored", telemetryMap)
    }
  }
  suspend fun contactAddressCreated(event: ContactAddressCreatedEvent) {
    val entityName = CONTACT_ADDRESS.entityName
    val dpsContactAddressId = event.additionalInformation.contactAddressId
    val telemetryMap = mutableMapOf(
      "dpsContactAddressId" to dpsContactAddressId.toString(),
    )

    if (event.didOriginateInDPS()) {
      synchronise {
        name = entityName
        telemetryClient = this@ContactPersonService.telemetryClient
        retryQueueService = contactPersonRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getByDpsContactAddressIdOrNull(dpsContactAddressId)
        }
        transform {
          val dpsAddress = dpsApiService.getContactAddress(dpsContactAddressId).also {
            telemetryMap["dpsContactId"] = it.contactId.toString()
            telemetryMap["nomisPersonId"] = it.contactId.toString()
          }
          nomisApiService.createPersonAddress(dpsAddress.contactId, dpsAddress.toNomisCreateRequest()).also {
            telemetryMap["nomisAddressId"] = it.personAddressId.toString()
          }.let {
            PersonAddressMappingDto(
              dpsId = dpsContactAddressId.toString(),
              nomisId = it.personAddressId,
              mappingType = PersonAddressMappingDto.MappingType.DPS_CREATED,
            )
          }
        }
        saveMapping { mappingApiService.createAddressMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("$entityName-create-ignored", telemetryMap)
    }
  }

  override suspend fun retryCreateMapping(message: String) {
    val baseMapping: CreateMappingRetryMessage<*> = message.fromJson()
    when (MappingTypes.fromEntityName(baseMapping.entityName)) {
      CONTACT_PERSON -> createPersonMapping(message.fromJson())
      CONTACT -> createPersonContactMapping(message.fromJson())
      CONTACT_ADDRESS -> createContactAddressMapping(message.fromJson())
    }
  }

  suspend fun createPersonMapping(message: CreateMappingRetryMessage<PersonMappingDto>) {
    mappingApiService.createPersonMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "contact-person-create-success",
        message.telemetryAttributes,
        null,
      )
    }
  }

  suspend fun createPersonContactMapping(message: CreateMappingRetryMessage<PersonContactMappingDto>) {
    mappingApiService.createContactMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "contact-create-success",
        message.telemetryAttributes,
        null,
      )
    }
  }
  suspend fun createContactAddressMapping(message: CreateMappingRetryMessage<PersonAddressMappingDto>) {
    mappingApiService.createAddressMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "contact-address-create-success",
        message.telemetryAttributes,
        null,
      )
    }
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}

private fun SyncContact.toNomisCreateRequest(): CreatePersonRequest = CreatePersonRequest(
  personId = this.id,
  firstName = this.firstName,
  lastName = this.lastName,
  middleName = this.middleName,
  dateOfBirth = this.dateOfBirth,
  interpreterRequired = this.interpreterRequired ?: false,
  genderCode = this.gender,
  languageCode = this.languageCode,
  domesticStatusCode = this.domesticStatus,
  titleCode = this.title,
  isStaff = this.isStaff,
)

private fun SyncPrisonerContact.toNomisCreateRequest(): CreatePersonContactRequest = CreatePersonContactRequest(
  offenderNo = this.prisonerNumber,
  contactTypeCode = this.contactType,
  relationshipTypeCode = this.relationshipType,
  active = this.active,
  approvedVisitor = this.approvedVisitor,
  emergencyContact = this.emergencyContact,
  nextOfKin = this.nextOfKin,
  comment = this.comments,
  expiryDate = this.expiryDate,
)
private fun SyncContactAddress.toNomisCreateRequest(): CreatePersonAddressRequest = CreatePersonAddressRequest(
  typeCode = this.addressType,
  flat = this.flat,
  premise = this.property,
  locality = this.area,
  postcode = this.postcode,
  street = this.street,
  cityCode = this.cityCode,
  countyCode = this.countyCode,
  countryCode = this.countryCode,
  noFixedAddress = this.noFixedAddress,
  primaryAddress = this.primaryAddress,
  mailAddress = this.mailFlag,
  startDate = this.startDate,
  endDate = this.endDate,
  comment = this.comments,
)

private fun SourcedContactPersonEvent.didOriginateInDPS() = this.additionalInformation.source == "DPS"
