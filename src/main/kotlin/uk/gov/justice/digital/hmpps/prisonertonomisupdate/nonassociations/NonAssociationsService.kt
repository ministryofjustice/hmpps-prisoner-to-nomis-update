package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateNonAssociationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations.model.LegacyNonAssociation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.LocalDate
import java.time.LocalDateTime

@Service
class NonAssociationsService(
  private val nonAssociationsApiService: NonAssociationsApiService,
  private val nomisApiService: NomisApiService,
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

//      checkMappingDoesNotExist {
//      }
      transform {
        nonAssociationsApiService.getNonAssociation(event.additionalInformation.id).run {
          val request = toCreateNonAssociationRequest(this)

          eventTelemetry += "offenderNo" to request.offenderNo
          eventTelemetry += "nsOffenderNo" to request.nsOffenderNo

          nomisApiService.createNonAssociation(request)
        }
      }
      // saveMapping { }
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

  companion object {
    private val log = LoggerFactory.getLogger(this::class.java)
  }

  override suspend fun retryCreateMapping(message: String) {
    // not required
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
