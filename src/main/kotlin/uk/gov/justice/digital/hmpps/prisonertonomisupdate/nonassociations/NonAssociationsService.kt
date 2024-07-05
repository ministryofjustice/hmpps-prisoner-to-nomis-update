package uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateNonAssociationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateNonAssociationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nonassociations.model.LegacyNonAssociation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreatingSystem
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.MergeEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
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
    val telemetryMap = mapOf("nonAssociationId" to event.additionalInformation.id.toString())
    if (isDpsCreated(event.additionalInformation)) {
      synchronise {
        name = "non-association"
        telemetryClient = this@NonAssociationsService.telemetryClient
        retryQueueService = nonAssociationsUpdateQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingService.getMappingGivenNonAssociationIdOrNull(event.additionalInformation.id)
        }
        transform {
          nonAssociationsApiService.getNonAssociation(event.additionalInformation.id).run {
            val request = toCreateNonAssociationRequest(this)

            eventTelemetry += "offenderNo" to request.offenderNo
            eventTelemetry += "nsOffenderNo" to request.nsOffenderNo

            NonAssociationMappingDto(
              firstOffenderNo = if (request.offenderNo < request.nsOffenderNo) request.offenderNo else request.nsOffenderNo,
              secondOffenderNo = if (request.offenderNo < request.nsOffenderNo) request.nsOffenderNo else request.offenderNo,
              nomisTypeSequence = nomisApiService.createNonAssociation(request).typeSequence,
              nonAssociationId = id,
              mappingType = "NON_ASSOCIATION_CREATED",
            )
          }
        }
        saveMapping { mappingService.createMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("non-association-create-ignored", telemetryMap)
    }
  }

  suspend fun amendNonAssociation(event: NonAssociationDomainEvent) {
    val telemetryMap = mutableMapOf("nonAssociationId" to event.additionalInformation.id.toString())

    if (isDpsCreated(event.additionalInformation)) {
      runCatching {
        val nonAssociation = nonAssociationsApiService.getNonAssociation(event.additionalInformation.id)

        mappingService.getMappingGivenNonAssociationId(event.additionalInformation.id)
          .apply {
            telemetryMap["offender1"] = firstOffenderNo
            telemetryMap["offender2"] = secondOffenderNo
            telemetryMap["sequence"] = nomisTypeSequence.toString()
            nomisApiService.amendNonAssociation(firstOffenderNo, secondOffenderNo, nomisTypeSequence, toUpdateNonAssociationRequest(nonAssociation))
          }
      }.onSuccess {
        telemetryClient.trackEvent("non-association-amend-success", telemetryMap)
      }.onFailure { e ->
        telemetryClient.trackEvent("non-association-amend-failed", telemetryMap)
        throw e
      }
    } else {
      telemetryClient.trackEvent("non-association-amend-ignored", telemetryMap)
    }
  }

  suspend fun closeNonAssociation(event: NonAssociationDomainEvent) {
    val telemetryMap = mutableMapOf("nonAssociationId" to event.additionalInformation.id.toString())

    if (isDpsCreated(event.additionalInformation)) {
      runCatching {
        mappingService.getMappingGivenNonAssociationId(event.additionalInformation.id)
          .apply {
            telemetryMap["offender1"] = firstOffenderNo
            telemetryMap["offender2"] = secondOffenderNo
            telemetryMap["sequence"] = nomisTypeSequence.toString()
            nomisApiService.closeNonAssociation(firstOffenderNo, secondOffenderNo, nomisTypeSequence)
          }
      }.onSuccess {
        telemetryClient.trackEvent("non-association-close-success", telemetryMap)
      }.onFailure { e ->
        telemetryClient.trackEvent("non-association-close-failed", telemetryMap)
        throw e
      }
    } else {
      telemetryClient.trackEvent("non-association-close-ignored", telemetryMap)
    }
  }

  suspend fun deleteNonAssociation(event: NonAssociationDomainEvent) {
    val telemetryMap = mutableMapOf("nonAssociationId" to event.additionalInformation.id.toString())

    if (isDpsCreated(event.additionalInformation)) {
      runCatching {
        mappingService.getMappingGivenNonAssociationId(event.additionalInformation.id)
          .apply {
            telemetryMap["offender1"] = firstOffenderNo
            telemetryMap["offender2"] = secondOffenderNo
            telemetryMap["sequence"] = nomisTypeSequence.toString()
            nomisApiService.deleteNonAssociation(firstOffenderNo, secondOffenderNo, nomisTypeSequence)
            mappingService.deleteNonAssociation(nonAssociationId)
          }
      }.onSuccess {
        telemetryClient.trackEvent("non-association-delete-success", telemetryMap)
      }.onFailure { e ->
        telemetryClient.trackEvent("non-association-delete-failed", telemetryMap)
        throw e
      }
    } else {
      telemetryClient.trackEvent("non-association-delete-ignored", telemetryMap)
    }
  }

  suspend fun processMerge(event: MergeEvent) {
    if (event.additionalInformation.reason == "MERGE") {
      mappingService.mergeNomsNumber(event.additionalInformation.removedNomsNumber, event.additionalInformation.nomsNumber)
      telemetryClient.trackEvent(
        "non-association-merge-success",
        mapOf(
          "removedNomsNumber" to event.additionalInformation.removedNomsNumber,
          "nomsNumber" to event.additionalInformation.nomsNumber,
          "reason" to event.additionalInformation.reason,
        ),
      )
    }
  }

  private fun toCreateNonAssociationRequest(instance: LegacyNonAssociation) = CreateNonAssociationRequest(
    offenderNo = instance.offenderNo,
    nsOffenderNo = instance.offenderNonAssociation.offenderNo,
    reason = instance.reasonCode.value,
    recipReason = instance.offenderNonAssociation.reasonCode.value,
    type = instance.typeCode.value,
    effectiveDate = LocalDateTime.parse(instance.effectiveDate).toLocalDate(),
    authorisedBy = instance.authorisedBy,
    comment = instance.comments,
  )

  private fun toUpdateNonAssociationRequest(instance: LegacyNonAssociation) = UpdateNonAssociationRequest(
    reason = instance.reasonCode.value,
    recipReason = instance.offenderNonAssociation.reasonCode.value,
    type = instance.typeCode.value,
    effectiveDate = LocalDateTime.parse(instance.effectiveDate).toLocalDate(),
    authorisedBy = instance.authorisedBy,
    comment = instance.comments,
  )

  private fun isDpsCreated(additionalInformation: NonAssociationAdditionalInformation) =
    additionalInformation.source != CreatingSystem.NOMIS.name

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
}

data class NonAssociationDomainEvent(
  val eventType: String,
  val version: String,
  val description: String,
  val additionalInformation: NonAssociationAdditionalInformation,
)

data class NonAssociationAdditionalInformation(
  val id: Long,
  val source: String? = null,
)
