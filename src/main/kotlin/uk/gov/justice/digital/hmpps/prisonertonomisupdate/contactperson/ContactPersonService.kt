package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.Contact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.PrisonerContact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonContactMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonMappingDto.MappingType.DPS_CREATED
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
    const val CONTACT_PERSON = "contactperson"
    const val CONTACT = "contact"
  }

  suspend fun contactCreated(event: ContactCreatedEvent) {
    val entityName = CONTACT_PERSON

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
    val entityName = CONTACT
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

  override suspend fun retryCreateMapping(message: String) {
    val baseMapping: CreateMappingRetryMessage<*> = message.fromJson()
    when (baseMapping.entityName) {
      CONTACT_PERSON -> createPersonMapping(message.fromJson())
      CONTACT -> createPersonContactMapping(message.fromJson())
      else -> throw IllegalArgumentException("Unknown entity type: ${baseMapping.entityName}")
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

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}

private fun Contact.toNomisCreateRequest(): CreatePersonRequest = CreatePersonRequest(
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

private fun PrisonerContact.toNomisCreateRequest(): CreatePersonContactRequest = CreatePersonContactRequest(
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

private fun SourcedContactPersonEvent.didOriginateInDPS() = this.additionalInformation.source == "DPS"
