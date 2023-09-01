package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateNonAssociationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations.model.LegacyNonAssociation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class NonAssociationsService(
  private val nonAssociationsApiService: NonAssociationsApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: NonAssociationMappingService,
  private val nonAssociationsUpdateQueueService: NonAssociationsUpdateQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {

  suspend fun createNonAssociation(event: NonAssociationDomainEvent) {
    synchronise {
      name = "nonAssociation"
      telemetryClient = this@NonAssociationsService.telemetryClient
      retryQueueService = nonAssociationsUpdateQueueService
      eventTelemetry = mapOf("nonAssociationId" to event.additionalInformation.id.toString())

      checkMappingDoesNotExist {
        mappingService.getMappingGivenNonAssociationIdOrNull(event.additionalInformation.id)
      }
      transform {
        nonAssociationsApiService.getNonAssociation(event.additionalInformation.id).run {
          val request = toCreateNonAssociationRequest(this)

          eventTelemetry += "offenderNo" to request.offenderNo
          eventTelemetry += "nsOffenderNo" to request.nsOffenderNo

          NonAssociationMappingDto(
            firstOffenderNo = request.offenderNo,
            secondOffenderNo = request.nsOffenderNo,
            nomisTypeSequence = nomisApiService.createNonAssociation(request).typeSequence,
            nonAssociationId = id,
            mappingType = "NON_ASSOCIATION_CREATED",
          )
        }
      }
      saveMapping { mappingService.createMapping(it) }
    }
  }

  private fun toCreateNonAssociationRequest(instance: LegacyNonAssociation) = CreateNonAssociationRequest(
    offenderNo = instance.offenderNo,
    nsOffenderNo = instance.offenderNonAssociation.offenderNo,
    reason = instance.reasonCode.value,
    recipReason = instance.offenderNonAssociation.reasonCode.value,
    type = instance.typeCode.value,
    effectiveDate = LocalDate.parse(instance.effectiveDate),
    authorisedBy = instance.authorisedBy,
    comment = instance.comments,
  )

  suspend fun createRetry(message: CreateMappingRetryMessage<NonAssociationMappingDto>) {
    mappingService.createMapping(message.mapping)
      .also {
        telemetryClient.trackEvent(
          "non-association-create-mapping-retry-success",
          message.telemetryAttributes,
        )
      }
  }

  override suspend fun retryCreateMapping(message: String) = createRetry(message.fromJson())

  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }
}

data class NonAssociationDomainEvent(
  val eventType: String,
  val version: String,
  val description: String,
  val occurredAt: LocalDateTime,
  val additionalInformation: NonAssociationAdditionalInformation,
)

data class NonAssociationAdditionalInformation(
  val id: Long,
)
