package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonService.Companion.MappingTypes.CONTACT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonService.Companion.MappingTypes.CONTACT_ADDRESS
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonService.Companion.MappingTypes.CONTACT_ADDRESS_PHONE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonService.Companion.MappingTypes.CONTACT_EMAIL
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonService.Companion.MappingTypes.CONTACT_IDENTITY
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonService.Companion.MappingTypes.CONTACT_PERSON
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonService.Companion.MappingTypes.CONTACT_PHONE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonService.Companion.MappingTypes.CONTACT_RESTRICTION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonService.Companion.MappingTypes.PRISONER_CONTACT_RESTRICTION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncContactAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncContactAddressPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncContactEmail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncContactIdentity
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncContactPhone
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncContactRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncPrisonerContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.SyncPrisonerContactRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonAddressMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonContactRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonEmailMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonIdentifierMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonMappingDto.MappingType.DPS_CREATED
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonPhoneMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateContactPersonRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonContactRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonIdentifierRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonPhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateContactPersonRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdatePersonAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdatePersonContactRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdatePersonEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdatePersonIdentifierRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdatePersonPhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdatePersonRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.LocalDate

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
      CONTACT_EMAIL("contact-email"),
      CONTACT_PHONE("contact-phone"),
      CONTACT_ADDRESS_PHONE("contact-address-phone"),
      CONTACT_IDENTITY("contact-identity"),
      PRISONER_CONTACT_RESTRICTION("prisoner-contact-restriction"),
      CONTACT_RESTRICTION("contact-restriction"),
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
  suspend fun contactDeleted(event: ContactDeletedEvent) {
    val entityName = CONTACT_PERSON.entityName

    val dpsContactId = event.additionalInformation.contactId
    val telemetryMap = mutableMapOf(
      "dpsContactId" to dpsContactId.toString(),
    )

    if (event.didOriginateInDPS()) {
      mappingApiService.getByDpsContactIdOrNull(dpsContactId)?.also {
        telemetryMap["nomisPersonId"] = it.nomisId.toString()
        nomisApiService.deletePerson(it.nomisId)
        mappingApiService.deleteByDpsContactId(dpsContactId)
        telemetryClient.trackEvent(
          "$entityName-delete-success",
          telemetryMap,
          null,
        )
      } ?: kotlin.run {
        telemetryClient.trackEvent("$entityName-delete-skipped", telemetryMap)
      }
    } else {
      telemetryClient.trackEvent("$entityName-delete-ignored", telemetryMap)
    }
  }
  suspend fun contactUpdated(event: ContactUpdatedEvent) {
    val entityName = CONTACT_PERSON.entityName

    val dpsContactId = event.additionalInformation.contactId
    val telemetryMap = mutableMapOf(
      "dpsContactId" to dpsContactId.toString(),
    )

    if (event.didOriginateInDPS()) {
      // not entirely necessary since both ids are the same, but ensures the person has been created
      val nomisPersonId = mappingApiService.getByDpsContactId(dpsContactId).nomisId.also {
        telemetryMap["nomisPersonId"] = it.toString()
      }
      val dpsContact = dpsApiService.getContact(dpsContactId)
      nomisApiService.updatePerson(nomisPersonId, dpsContact.toNomisUpdateRequest())
      telemetryClient.trackEvent(
        "$entityName-update-success",
        telemetryMap,
        null,
      )
    } else {
      telemetryClient.trackEvent("$entityName-update-ignored", telemetryMap)
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

  suspend fun prisonerContactUpdated(event: PrisonerContactUpdatedEvent) {
    val entityName = CONTACT.entityName

    val dpsPrisonerContactId = event.additionalInformation.prisonerContactId
    val telemetryMap = mutableMapOf(
      "dpsPrisonerContactId" to dpsPrisonerContactId.toString(),
    )

    if (event.didOriginateInDPS()) {
      val nomisContactId = mappingApiService.getByDpsPrisonerContactId(dpsPrisonerContactId).nomisId.also {
        telemetryMap["nomisContactId"] = it.toString()
      }
      val dpsPrisonerContact = dpsApiService.getPrisonerContact(dpsPrisonerContactId).also {
        telemetryMap["nomisPersonId"] = it.contactId.toString()
        telemetryMap["dpsContactId"] = it.contactId.toString()
      }
      nomisApiService.updatePersonContact(
        personId = dpsPrisonerContact.contactId,
        contactId = nomisContactId,
        dpsPrisonerContact.toNomisUpdateRequest(),
      )
      telemetryClient.trackEvent(
        "$entityName-update-success",
        telemetryMap,
        null,
      )
    } else {
      telemetryClient.trackEvent("$entityName-update-ignored", telemetryMap)
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

  suspend fun contactAddressUpdated(event: ContactAddressUpdateEvent) {
    val entityName = CONTACT_ADDRESS.entityName

    val dpsContactAddressId = event.additionalInformation.contactAddressId
    val telemetryMap = mutableMapOf(
      "dpsContactAddressId" to dpsContactAddressId.toString(),
    )

    if (event.didOriginateInDPS()) {
      val nomisAddressId = mappingApiService.getByDpsContactAddressId(dpsContactAddressId).nomisId.also {
        telemetryMap["nomisAddressId"] = it.toString()
      }
      val dpsContactAddress = dpsApiService.getContactAddress(dpsContactAddressId).also {
        telemetryMap["nomisPersonId"] = it.contactId.toString()
        telemetryMap["dpsContactId"] = it.contactId.toString()
      }
      nomisApiService.updatePersonAddress(
        personId = dpsContactAddress.contactId,
        addressId = nomisAddressId,
        dpsContactAddress.toNomisUpdateRequest(),
      )
      telemetryClient.trackEvent(
        "$entityName-update-success",
        telemetryMap,
        null,
      )
    } else {
      telemetryClient.trackEvent("$entityName-update-ignored", telemetryMap)
    }
  }

  suspend fun contactEmailCreated(event: ContactEmailCreatedEvent) {
    val entityName = CONTACT_EMAIL.entityName
    val dpsContactEmailId = event.additionalInformation.contactEmailId
    val telemetryMap = mutableMapOf(
      "dpsContactEmailId" to dpsContactEmailId.toString(),
    )

    if (event.didOriginateInDPS()) {
      synchronise {
        name = entityName
        telemetryClient = this@ContactPersonService.telemetryClient
        retryQueueService = contactPersonRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getByDpsContactEmailIdOrNull(dpsContactEmailId)
        }
        transform {
          val dpsEmail = dpsApiService.getContactEmail(dpsContactEmailId).also {
            telemetryMap["dpsContactId"] = it.contactId.toString()
            telemetryMap["nomisPersonId"] = it.contactId.toString()
          }
          nomisApiService.createPersonEmail(dpsEmail.contactId, dpsEmail.toNomisCreateRequest()).also {
            telemetryMap["nomisInternetAddressId"] = it.emailAddressId.toString()
          }.let {
            PersonEmailMappingDto(
              dpsId = dpsContactEmailId.toString(),
              nomisId = it.emailAddressId,
              mappingType = PersonEmailMappingDto.MappingType.DPS_CREATED,
            )
          }
        }
        saveMapping { mappingApiService.createEmailMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("$entityName-create-ignored", telemetryMap)
    }
  }
  suspend fun contactEmailUpdated(event: ContactEmailUpdatedEvent) {
    val entityName = CONTACT_EMAIL.entityName
    val dpsContactEmailId = event.additionalInformation.contactEmailId
    val telemetryMap = mutableMapOf(
      "dpsContactEmailId" to dpsContactEmailId.toString(),
    )

    if (event.didOriginateInDPS()) {
      val nomisEmailId = mappingApiService.getByDpsContactEmailId(dpsContactEmailId).nomisId.also {
        telemetryMap["nomisInternetAddressId"] = it.toString()
      }
      val dpsContactEmail = dpsApiService.getContactEmail(dpsContactEmailId).also {
        telemetryMap["nomisPersonId"] = it.contactId.toString()
        telemetryMap["dpsContactId"] = it.contactId.toString()
      }
      nomisApiService.updatePersonEmail(dpsContactEmail.contactId, nomisEmailId, dpsContactEmail.toNomisUpdateRequest())
      telemetryClient.trackEvent("$entityName-update-success", telemetryMap)
    } else {
      telemetryClient.trackEvent("$entityName-update-ignored", telemetryMap)
    }
  }
  suspend fun contactPhoneCreated(event: ContactPhoneCreatedEvent) {
    val entityName = CONTACT_PHONE.entityName
    val dpsContactPhoneId = event.additionalInformation.contactPhoneId
    val telemetryMap = mutableMapOf(
      "dpsContactPhoneId" to dpsContactPhoneId.toString(),
    )

    if (event.didOriginateInDPS()) {
      synchronise {
        name = entityName
        telemetryClient = this@ContactPersonService.telemetryClient
        retryQueueService = contactPersonRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getByDpsContactPhoneIdOrNull(dpsContactPhoneId)
        }
        transform {
          val dpsPhone = dpsApiService.getContactPhone(dpsContactPhoneId).also {
            telemetryMap["dpsContactId"] = it.contactId.toString()
            telemetryMap["nomisPersonId"] = it.contactId.toString()
          }
          nomisApiService.createPersonPhone(dpsPhone.contactId, dpsPhone.toNomisCreateRequest()).also {
            telemetryMap["nomisPhoneId"] = it.phoneId.toString()
          }.let {
            PersonPhoneMappingDto(
              dpsId = dpsContactPhoneId.toString(),
              nomisId = it.phoneId,
              dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.PERSON,
              mappingType = PersonPhoneMappingDto.MappingType.DPS_CREATED,
            )
          }
        }
        saveMapping { mappingApiService.createPhoneMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("$entityName-create-ignored", telemetryMap)
    }
  }
  suspend fun contactPhoneUpdated(event: ContactPhoneUpdatedEvent) {
    val entityName = CONTACT_PHONE.entityName
    val dpsContactPhoneId = event.additionalInformation.contactPhoneId
    val telemetryMap = mutableMapOf(
      "dpsContactPhoneId" to dpsContactPhoneId.toString(),
    )

    if (event.didOriginateInDPS()) {
      val nomisPhoneId = mappingApiService.getByDpsContactPhoneId(dpsContactPhoneId).nomisId.also {
        telemetryMap["nomisPhoneId"] = it.toString()
      }
      val dpsContactPhone = dpsApiService.getContactPhone(dpsContactPhoneId).also {
        telemetryMap["nomisPersonId"] = it.contactId.toString()
        telemetryMap["dpsContactId"] = it.contactId.toString()
        telemetryMap["dpsContactPhoneId"] = it.contactPhoneId.toString()
      }
      nomisApiService.updatePersonPhone(
        personId = dpsContactPhone.contactId,
        phoneId = nomisPhoneId,
        dpsContactPhone.toNomisUpdateRequest(),
      )
      telemetryClient.trackEvent("$entityName-update-success", telemetryMap)
    } else {
      telemetryClient.trackEvent("$entityName-update-ignored", telemetryMap)
    }
  }

  suspend fun contactAddressPhoneCreated(event: ContactAddressPhoneCreatedEvent) {
    val entityName = CONTACT_ADDRESS_PHONE.entityName
    val dpsContactAddressPhoneId = event.additionalInformation.contactAddressPhoneId
    val dpsContactAddressId = event.additionalInformation.contactAddressId
    val telemetryMap = mutableMapOf(
      "dpsContactAddressPhoneId" to dpsContactAddressPhoneId.toString(),
      "dpsContactAddressId" to dpsContactAddressId.toString(),
    )

    if (event.didOriginateInDPS()) {
      synchronise {
        name = entityName
        telemetryClient = this@ContactPersonService.telemetryClient
        retryQueueService = contactPersonRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getByDpsContactAddressPhoneIdOrNull(dpsContactAddressPhoneId)
        }
        transform {
          val dpsPhone = dpsApiService.getContactAddressPhone(dpsContactAddressPhoneId).also {
            telemetryMap["dpsContactId"] = it.contactId.toString()
            telemetryMap["nomisPersonId"] = it.contactId.toString()
          }
          val nomisAddressId = mappingApiService.getByDpsContactAddressId(dpsContactAddressId).nomisId.also {
            telemetryMap["nomisAddressId"] = it.toString()
          }
          nomisApiService.createPersonAddressPhone(
            personId = dpsPhone.contactId,
            addressId = nomisAddressId,
            request = dpsPhone.toNomisCreateRequest(),
          ).also {
            telemetryMap["nomisPhoneId"] = it.phoneId.toString()
          }.let {
            PersonPhoneMappingDto(
              dpsId = dpsContactAddressPhoneId.toString(),
              nomisId = it.phoneId,
              dpsPhoneType = PersonPhoneMappingDto.DpsPhoneType.ADDRESS,
              mappingType = PersonPhoneMappingDto.MappingType.DPS_CREATED,
            )
          }
        }
        saveMapping { mappingApiService.createPhoneMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("$entityName-create-ignored", telemetryMap)
    }
  }
  suspend fun contactAddressPhoneUpdated(event: ContactAddressPhoneUpdatedEvent) {
    val entityName = CONTACT_ADDRESS_PHONE.entityName
    val dpsContactAddressPhoneId = event.additionalInformation.contactAddressPhoneId
    val dpsContactAddressId = event.additionalInformation.contactAddressId
    val telemetryMap = mutableMapOf(
      "dpsContactAddressPhoneId" to dpsContactAddressPhoneId.toString(),
      "dpsContactAddressId" to dpsContactAddressId.toString(),
    )

    if (event.didOriginateInDPS()) {
      val nomisPhoneId = mappingApiService.getByDpsContactAddressPhoneId(dpsContactAddressPhoneId).nomisId.also {
        telemetryMap["nomisPhoneId"] = it.toString()
      }
      val dpsContactPhone = dpsApiService.getContactAddressPhone(dpsContactAddressPhoneId).also {
        telemetryMap["nomisPersonId"] = it.contactId.toString()
        telemetryMap["dpsContactId"] = it.contactId.toString()
        telemetryMap["dpsContactAddressPhoneId"] = it.contactAddressPhoneId.toString()
      }

      val nomisAddressId = mappingApiService.getByDpsContactAddressId(dpsContactAddressId).nomisId.also {
        telemetryMap["nomisAddressId"] = it.toString()
      }
      nomisApiService.updatePersonAddressPhone(
        personId = dpsContactPhone.contactId,
        phoneId = nomisPhoneId,
        addressId = nomisAddressId,
        request = dpsContactPhone.toNomisUpdateRequest(),
      )

      telemetryClient.trackEvent("$entityName-update-success", telemetryMap)
    } else {
      telemetryClient.trackEvent("$entityName-update-ignored", telemetryMap)
    }
  }

  suspend fun contactIdentityCreated(event: ContactIdentityCreatedEvent) {
    val entityName = CONTACT_IDENTITY.entityName
    val dpsContactIdentityId = event.additionalInformation.contactIdentityId
    val telemetryMap = mutableMapOf(
      "dpsContactIdentityId" to dpsContactIdentityId.toString(),
    )

    if (event.didOriginateInDPS()) {
      synchronise {
        name = entityName
        telemetryClient = this@ContactPersonService.telemetryClient
        retryQueueService = contactPersonRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getByDpsContactIdentityIdOrNull(dpsContactIdentityId)
        }
        transform {
          val dpsIdentity = dpsApiService.getContactIdentity(dpsContactIdentityId).also {
            telemetryMap["dpsContactId"] = it.contactId.toString()
            telemetryMap["nomisPersonId"] = it.contactId.toString()
          }
          nomisApiService.createPersonIdentifier(dpsIdentity.contactId, dpsIdentity.toNomisCreateRequest()).also {
            telemetryMap["nomisSequenceNumber"] = it.sequence.toString()
          }.let {
            PersonIdentifierMappingDto(
              dpsId = dpsContactIdentityId.toString(),
              // nomis person Id is same as DPS contact id
              nomisPersonId = dpsIdentity.contactId,
              nomisSequenceNumber = it.sequence,
              mappingType = PersonIdentifierMappingDto.MappingType.DPS_CREATED,
            )
          }
        }
        saveMapping { mappingApiService.createIdentifierMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("$entityName-create-ignored", telemetryMap)
    }
  }
  suspend fun contactIdentityUpdated(event: ContactIdentityUpdatedEvent) {
    val entityName = CONTACT_IDENTITY.entityName
    val dpsContactIdentityId = event.additionalInformation.contactIdentityId
    val telemetryMap = mutableMapOf(
      "dpsContactIdentityId" to dpsContactIdentityId.toString(),
    )

    if (event.didOriginateInDPS()) {
      val mapping = mappingApiService.getByDpsContactIdentityId(dpsContactIdentityId).also {
        telemetryMap["nomisSequenceNumber"] = it.nomisSequenceNumber.toString()
        telemetryMap["nomisPersonId"] = it.nomisPersonId.toString()
      }
      val dpsContactIdentity = dpsApiService.getContactIdentity(dpsContactIdentityId).also {
        telemetryMap["dpsContactId"] = it.contactId.toString()
      }
      nomisApiService.updatePersonIdentifier(personId = mapping.nomisPersonId, sequence = mapping.nomisSequenceNumber, dpsContactIdentity.toNomisUpdateRequest())
      telemetryClient.trackEvent("$entityName-update-success", telemetryMap)
    } else {
      telemetryClient.trackEvent("$entityName-update-ignored", telemetryMap)
    }
  }

  suspend fun prisonerContactRestrictionCreated(event: PrisonerContactRestrictionCreatedEvent) {
    val entityName = PRISONER_CONTACT_RESTRICTION.entityName
    val dpsPrisonerContactRestrictionId = event.additionalInformation.prisonerContactRestrictionId
    val telemetryMap = mutableMapOf(
      "dpsPrisonerContactRestrictionId" to dpsPrisonerContactRestrictionId.toString(),
    )

    if (event.didOriginateInDPS()) {
      synchronise {
        name = entityName
        telemetryClient = this@ContactPersonService.telemetryClient
        retryQueueService = contactPersonRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getByDpsPrisonerContactRestrictionIdOrNull(dpsPrisonerContactRestrictionId)
        }
        transform {
          val dpsPrisonerContactRestriction = dpsApiService.getPrisonerContactRestriction(dpsPrisonerContactRestrictionId).also {
            telemetryMap["offenderNo"] = it.prisonerNumber
            telemetryMap["dpsContactId"] = it.contactId.toString()
            telemetryMap["nomisPersonId"] = it.contactId.toString()
            telemetryMap["dpsPrisonerContactId"] = it.prisonerContactId.toString()
          }
          val nomisContactId = mappingApiService.getByDpsPrisonerContactId(dpsPrisonerContactRestriction.prisonerContactId).nomisId.also {
            telemetryMap["nomisContactId"] = it.toString()
          }
          nomisApiService.createContactRestriction(
            personId = dpsPrisonerContactRestriction.contactId,
            contactId = nomisContactId,
            dpsPrisonerContactRestriction.toNomisCreateRequest(),
          ).also {
            telemetryMap["nomisContactRestrictionId"] = it.id.toString()
          }.let {
            PersonContactRestrictionMappingDto(
              dpsId = dpsPrisonerContactRestrictionId.toString(),
              nomisId = it.id,
              mappingType = PersonContactRestrictionMappingDto.MappingType.DPS_CREATED,
            )
          }
        }
        saveMapping { mappingApiService.createContactRestrictionMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("$entityName-create-ignored", telemetryMap)
    }
  }

  suspend fun prisonerContactRestrictionUpdated(event: PrisonerContactRestrictionUpdatedEvent) {
    val entityName = PRISONER_CONTACT_RESTRICTION.entityName
    val dpsPrisonerContactRestrictionId = event.additionalInformation.prisonerContactRestrictionId
    val telemetryMap = mutableMapOf(
      "dpsPrisonerContactRestrictionId" to dpsPrisonerContactRestrictionId.toString(),
    )

    if (event.didOriginateInDPS()) {
      try {
        val nomisContactRestrictionId = mappingApiService.getByDpsPrisonerContactRestrictionId(dpsPrisonerContactRestrictionId).nomisId.also {
          telemetryMap["nomisContactRestrictionId"] = it.toString()
        }
        val dpsPrisonerContactRestriction = dpsApiService.getPrisonerContactRestriction(dpsPrisonerContactRestrictionId).also {
          telemetryMap["offenderNo"] = it.prisonerNumber
          telemetryMap["dpsContactId"] = it.contactId.toString()
          telemetryMap["nomisPersonId"] = it.contactId.toString()
          telemetryMap["dpsPrisonerContactId"] = it.prisonerContactId.toString()
        }
        val nomisContactId = mappingApiService.getByDpsPrisonerContactId(dpsPrisonerContactRestriction.prisonerContactId).nomisId.also {
          telemetryMap["nomisContactId"] = it.toString()
        }
        nomisApiService.updateContactRestriction(
          personId = dpsPrisonerContactRestriction.contactId,
          contactId = nomisContactId,
          contactRestrictionId = nomisContactRestrictionId,
          dpsPrisonerContactRestriction.toNomisUpdateRequest(),
        )
        telemetryClient.trackEvent("$entityName-update-success", telemetryMap)
      } catch (e: Exception) {
        telemetryMap["error"] = e.message.toString()
        telemetryClient.trackEvent("$entityName-update-failed", telemetryMap)
        throw e
      }
    } else {
      telemetryClient.trackEvent("$entityName-update-ignored", telemetryMap)
    }
  }

  suspend fun contactRestrictionCreated(event: ContactRestrictionCreatedEvent) {
    val entityName = CONTACT_RESTRICTION.entityName
    val dpsContactRestrictionId = event.additionalInformation.contactRestrictionId
    val telemetryMap = mutableMapOf(
      "dpsContactRestrictionId" to dpsContactRestrictionId.toString(),
    )

    if (event.didOriginateInDPS()) {
      synchronise {
        name = entityName
        telemetryClient = this@ContactPersonService.telemetryClient
        retryQueueService = contactPersonRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getByDpsContactRestrictionIdOrNull(dpsContactRestrictionId)
        }
        transform {
          val dpsContactRestriction = dpsApiService.getContactRestriction(dpsContactRestrictionId).also {
            telemetryMap["dpsContactId"] = it.contactId.toString()
            telemetryMap["nomisPersonId"] = it.contactId.toString()
          }
          nomisApiService.createPersonRestriction(dpsContactRestriction.contactId, dpsContactRestriction.toNomisCreateRequest()).also {
            telemetryMap["nomisPersonRestrictionId"] = it.id.toString()
          }.let {
            PersonRestrictionMappingDto(
              dpsId = dpsContactRestrictionId.toString(),
              nomisId = it.id,
              mappingType = PersonRestrictionMappingDto.MappingType.DPS_CREATED,
            )
          }
        }
        saveMapping { mappingApiService.createPersonRestrictionMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("$entityName-create-ignored", telemetryMap)
    }
  }
  suspend fun contactRestrictionUpdated(event: ContactRestrictionUpdatedEvent) {
    val entityName = CONTACT_RESTRICTION.entityName
    val dpsContactRestrictionId = event.additionalInformation.contactRestrictionId
    val telemetryMap = mutableMapOf(
      "dpsContactRestrictionId" to dpsContactRestrictionId.toString(),
    )

    if (event.didOriginateInDPS()) {
      try {
        val nomisPersonRestrictionId = mappingApiService.getByDpsContactRestrictionId(dpsContactRestrictionId).nomisId.also {
          telemetryMap["nomisPersonRestrictionId"] = it.toString()
        }
        val dpsContactRestriction = dpsApiService.getContactRestriction(dpsContactRestrictionId).also {
          telemetryMap["dpsContactId"] = it.contactId.toString()
          telemetryMap["nomisPersonId"] = it.contactId.toString()
        }
        nomisApiService.updatePersonRestriction(dpsContactRestriction.contactId, personRestrictionId = nomisPersonRestrictionId, dpsContactRestriction.toNomisUpdateRequest())
        telemetryClient.trackEvent("$entityName-update-success", telemetryMap)
      } catch (e: Exception) {
        telemetryMap["error"] = e.message.toString()
        telemetryClient.trackEvent("$entityName-update-failed", telemetryMap)
        throw e
      }
    } else {
      telemetryClient.trackEvent("$entityName-update-ignored", telemetryMap)
    }
  }

  override suspend fun retryCreateMapping(message: String) {
    val baseMapping: CreateMappingRetryMessage<*> = message.fromJson()
    when (MappingTypes.fromEntityName(baseMapping.entityName)) {
      CONTACT_PERSON -> createPersonMapping(message.fromJson())
      CONTACT -> createPersonContactMapping(message.fromJson())
      CONTACT_ADDRESS -> createContactAddressMapping(message.fromJson())
      CONTACT_EMAIL -> createContactEmailMapping(message.fromJson())
      CONTACT_PHONE -> createContactPhoneMapping(message.fromJson())
      CONTACT_ADDRESS_PHONE -> createContactAddressPhoneMapping(message.fromJson())
      CONTACT_IDENTITY -> createContactIdentifierMapping(message.fromJson())
      PRISONER_CONTACT_RESTRICTION -> createContactRestrictionMapping(message.fromJson())
      CONTACT_RESTRICTION -> createPersonRestrictionMapping(message.fromJson())
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
  suspend fun createContactEmailMapping(message: CreateMappingRetryMessage<PersonEmailMappingDto>) {
    mappingApiService.createEmailMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "contact-email-create-success",
        message.telemetryAttributes,
        null,
      )
    }
  }
  suspend fun createContactPhoneMapping(message: CreateMappingRetryMessage<PersonPhoneMappingDto>) {
    mappingApiService.createPhoneMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "contact-phone-create-success",
        message.telemetryAttributes,
        null,
      )
    }
  }
  suspend fun createContactIdentifierMapping(message: CreateMappingRetryMessage<PersonIdentifierMappingDto>) {
    mappingApiService.createIdentifierMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "contact-identity-create-success",
        message.telemetryAttributes,
        null,
      )
    }
  }

  suspend fun createContactAddressPhoneMapping(message: CreateMappingRetryMessage<PersonPhoneMappingDto>) {
    mappingApiService.createPhoneMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "contact-address-phone-create-success",
        message.telemetryAttributes,
        null,
      )
    }
  }

  suspend fun createContactRestrictionMapping(message: CreateMappingRetryMessage<PersonContactRestrictionMappingDto>) {
    mappingApiService.createContactRestrictionMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "prisoner-contact-restriction-create-success",
        message.telemetryAttributes,
        null,
      )
    }
  }

  suspend fun createPersonRestrictionMapping(message: CreateMappingRetryMessage<PersonRestrictionMappingDto>) {
    mappingApiService.createPersonRestrictionMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "contact-restriction-create-success",
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
private fun SyncContact.toNomisUpdateRequest(): UpdatePersonRequest = UpdatePersonRequest(
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
  deceasedDate = this.deceasedDate,
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
private fun SyncPrisonerContact.toNomisUpdateRequest(): UpdatePersonContactRequest = UpdatePersonContactRequest(
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
private fun SyncContactAddress.toNomisUpdateRequest(): UpdatePersonAddressRequest = UpdatePersonAddressRequest(
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
  validatedPAF = this.verified,
)

private fun SyncContactEmail.toNomisCreateRequest(): CreatePersonEmailRequest = CreatePersonEmailRequest(
  email = this.emailAddress,
)
private fun SyncContactEmail.toNomisUpdateRequest(): UpdatePersonEmailRequest = UpdatePersonEmailRequest(
  email = this.emailAddress,
)

private fun SyncContactPhone.toNomisCreateRequest(): CreatePersonPhoneRequest = CreatePersonPhoneRequest(
  number = this.phoneNumber,
  extension = this.extNumber,
  typeCode = this.phoneType,
)
private fun SyncContactPhone.toNomisUpdateRequest(): UpdatePersonPhoneRequest = UpdatePersonPhoneRequest(
  number = this.phoneNumber,
  extension = this.extNumber,
  typeCode = this.phoneType,
)

private fun SyncContactAddressPhone.toNomisCreateRequest(): CreatePersonPhoneRequest = CreatePersonPhoneRequest(
  number = this.phoneNumber,
  extension = this.extNumber,
  typeCode = this.phoneType,
)

private fun SyncContactAddressPhone.toNomisUpdateRequest(): UpdatePersonPhoneRequest = UpdatePersonPhoneRequest(
  number = this.phoneNumber,
  extension = this.extNumber,
  typeCode = this.phoneType,
)

private fun SyncContactIdentity.toNomisCreateRequest(): CreatePersonIdentifierRequest = CreatePersonIdentifierRequest(
  // TODO - check with DPS - this should be non-nullable
  identifier = this.identityValue!!,
  issuedAuthority = this.issuingAuthority,
  typeCode = this.identityType,
)

private fun SyncContactIdentity.toNomisUpdateRequest(): UpdatePersonIdentifierRequest = UpdatePersonIdentifierRequest(
  // TODO - check with DPS - this should be non-nullable
  identifier = this.identityValue!!,
  issuedAuthority = this.issuingAuthority,
  typeCode = this.identityType,
)

private fun SyncPrisonerContactRestriction.toNomisCreateRequest(): CreateContactPersonRestrictionRequest = CreateContactPersonRestrictionRequest(
  // TODO - check with DPS - this should be non-nullable
  typeCode = this.restrictionType!!,
  effectiveDate = this.startDate ?: LocalDate.now(),
  expiryDate = this.expiryDate,
  comment = this.comments,
  // TODO - check with DPS - this should be non-nullable
  enteredStaffUsername = this.createdBy!!,
)
private fun SyncPrisonerContactRestriction.toNomisUpdateRequest(): UpdateContactPersonRestrictionRequest = UpdateContactPersonRestrictionRequest(
  // TODO - check with DPS - this should be non-nullable
  typeCode = this.restrictionType!!,
  effectiveDate = this.startDate ?: LocalDate.now(),
  expiryDate = this.expiryDate,
  comment = this.comments,
  // TODO - check with DPS - this should be non-nullable
  enteredStaffUsername = this.updatedBy!!,
)

private fun SyncContactRestriction.toNomisCreateRequest(): CreateContactPersonRestrictionRequest = CreateContactPersonRestrictionRequest(
  typeCode = this.restrictionType,
  effectiveDate = this.startDate ?: LocalDate.now(),
  expiryDate = this.expiryDate,
  comment = this.comments,
  enteredStaffUsername = this.createdBy,
)
private fun SyncContactRestriction.toNomisUpdateRequest(): UpdateContactPersonRestrictionRequest = UpdateContactPersonRestrictionRequest(
  typeCode = this.restrictionType,
  effectiveDate = this.startDate ?: LocalDate.now(),
  expiryDate = this.expiryDate,
  comment = this.comments,
  enteredStaffUsername = this.updatedBy!!,
)

private fun SourcedContactPersonEvent.didOriginateInDPS() = this.additionalInformation.source == "DPS"
