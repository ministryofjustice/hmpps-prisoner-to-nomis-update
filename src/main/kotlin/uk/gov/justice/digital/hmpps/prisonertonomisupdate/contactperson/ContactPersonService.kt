package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.model.Contact
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PersonMappingDto.MappingType.DPS_CREATED
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreatePersonRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise

@Suppress("unused")
@Service
class ContactPersonService(
  private val mappingApiService: ContactPersonMappingApiService,
  private val nomisApiService: ContactPersonNomisApiService,
  private val dpsApiService: ContactPersonDpsApiService,
  private val contactPersonRetryQueueService: ContactPersonRetryQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {
  suspend fun contactCreated(event: ContactCreatedEvent) {
    val dpsContactId = event.additionalInformation.contactId
    val telemetryMap = mapOf(
      "dpsContactId" to dpsContactId.toString(),
    )

    if (event.didOriginateInDPS()) {
      synchronise {
        name = "contactperson"
        telemetryClient = this@ContactPersonService.telemetryClient
        retryQueueService = contactPersonRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getByDpsContactIdOrNull(dpsContactId)
        }
        transform {
          PersonMappingDto(
            dpsId = dpsContactId.toString(),
            nomisId = nomisApiService.createPerson(dpsApiService.getContact(dpsContactId).toNomisCreateRequest()).personId,
            mappingType = DPS_CREATED,
          )
        }
        saveMapping { mappingApiService.createPersonMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("contact-person-create-ignored", telemetryMap)
    }
  }

  override suspend fun retryCreateMapping(message: String) = createMapping(message.fromJson())

  suspend fun createMapping(message: CreateMappingRetryMessage<PersonMappingDto>) {
    mappingApiService.createPersonMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "contact-person-create-success",
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

// TODO - DPS needs something in th event for source
private fun ContactCreatedEvent.didOriginateInDPS() = true
