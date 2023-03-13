package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateSentencingAdjustmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpdateSentencingAdjustmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise

@Service
class SentencingAdjustmentsService(
  private val sentencingAdjustmentsApiService: SentencingAdjustmentsApiService,
  private val nomisApiService: NomisApiService,
  private val sentencingAdjustmentsMappingService: SentencingAdjustmentsMappingService,
  private val sentencingRetryQueueService: SentencingRetryQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {
  suspend fun createAdjustment(createEvent: AdjustmentCreatedEvent) {
    synchronise {
      name = "sentencing-adjustment"
      telemetryClient = this@SentencingAdjustmentsService.telemetryClient
      retryQueueService = sentencingRetryQueueService
      eventTelemetry = mapOf(
        "adjustmentId" to createEvent.additionalInformation.id,
        "offenderNo" to createEvent.additionalInformation.nomsNumber,
      )

      checkMappingDoesNotExist {
        sentencingAdjustmentsMappingService.getMappingGivenAdjustmentIdOrNull(createEvent.additionalInformation.id)
      }
      transform {
        sentencingAdjustmentsApiService.getAdjustment(createEvent.additionalInformation.id)
          .takeIf { it.creatingSystem != CreatingSystem.NOMIS }?.let { adjustment ->
            createTransformedAdjustment(adjustment).let { nomisAdjustment ->
              SentencingAdjustmentMappingDto(
                nomisAdjustmentId = nomisAdjustment.id,
                nomisAdjustmentCategory = if (adjustment.sentenceSequence == null) "KEY-DATE" else "SENTENCE",
                adjustmentId = adjustment.adjustmentId,
              )
            }
          }
      }
      saveMapping { sentencingAdjustmentsMappingService.createMapping(it) }
    }
  }

  private suspend fun createTransformedAdjustment(adjustment: AdjustmentDetails) =
    CreateSentencingAdjustmentRequest(
      adjustmentTypeCode = adjustment.adjustmentType,
      adjustmentDate = adjustment.adjustmentDate,
      adjustmentFromDate = adjustment.adjustmentStartPeriod,
      adjustmentDays = adjustment.adjustmentDays,
      comment = adjustment.comment,
    ).run {
      if (adjustment.sentenceSequence == null) {
        nomisApiService.createKeyDateAdjustment(
          adjustment.bookingId,
          this,
        )
      } else {
        nomisApiService.createSentenceAdjustment(
          adjustment.bookingId,
          adjustment.sentenceSequence,
          this,
        )
      }
    }

  suspend fun updateAdjustment(createEvent: AdjustmentUpdatedEvent) {
    val adjustmentId = createEvent.additionalInformation.id
    val offenderNo = createEvent.additionalInformation.nomsNumber
    val telemetryMap = mutableMapOf("adjustmentId" to adjustmentId, "offenderNo" to offenderNo)

    runCatching {
      val nomisAdjustmentId = sentencingAdjustmentsMappingService.getMappingGivenAdjustmentId(adjustmentId).nomisAdjustmentId
        .also { telemetryMap["nomisAdjustmentId"] = it.toString() }

      sentencingAdjustmentsApiService.getAdjustment(createEvent.additionalInformation.id).let { adjustment ->
        if (adjustment.creatingSystem != CreatingSystem.NOMIS) {
          updateTransformedAdjustment(nomisAdjustmentId, adjustment)
          telemetryClient.trackEvent("sentencing-adjustment-updated-success", telemetryMap, null)
        } else {
          telemetryClient.trackEvent("sentencing-adjustment-updated-ignored", telemetryMap, null)
        }
      }
    }.onFailure { e ->
      telemetryClient.trackEvent("sentencing-adjustment-updated-failed", telemetryMap, null)
      throw e
    }
  }

  private suspend fun updateTransformedAdjustment(nomisAdjustmentId: Long, adjustment: AdjustmentDetails) =
    UpdateSentencingAdjustmentRequest(
      adjustmentTypeCode = adjustment.adjustmentType,
      adjustmentDate = adjustment.adjustmentDate,
      adjustmentFromDate = adjustment.adjustmentStartPeriod,
      adjustmentDays = adjustment.adjustmentDays,
      comment = adjustment.comment,
    ).run {
      if (adjustment.sentenceSequence == null) {
        nomisApiService.updateKeyDateAdjustment(
          nomisAdjustmentId,
          this,
        )
      } else {
        nomisApiService.updateSentenceAdjustment(
          nomisAdjustmentId,
          this,
        )
      }
    }

  suspend fun deleteAdjustment(createEvent: AdjustmentDeletedEvent) {
    val adjustmentId = createEvent.additionalInformation.id
    val offenderNo = createEvent.additionalInformation.nomsNumber
    val telemetryMap = mutableMapOf("adjustmentId" to adjustmentId, "offenderNo" to offenderNo)

    runCatching {
      val mapping = sentencingAdjustmentsMappingService.getMappingGivenAdjustmentId(adjustmentId)
        .also {
          telemetryMap["nomisAdjustmentId"] = it.nomisAdjustmentId.toString()
          telemetryMap["nomisAdjustmentCategory"] = it.nomisAdjustmentCategory
        }

      if (mapping.nomisAdjustmentCategory == "SENTENCE") {
        nomisApiService.deleteSentenceAdjustment(
          mapping.nomisAdjustmentId,
        )
      } else {
        nomisApiService.deleteKeyDateAdjustment(
          mapping.nomisAdjustmentId,
        )
      }.also {
        sentencingAdjustmentsMappingService.deleteMappingGivenAdjustmentId(createEvent.additionalInformation.id)
      }
    }.onSuccess {
      telemetryClient.trackEvent("sentencing-adjustment-deleted-success", telemetryMap, null)
    }.onFailure { e ->
      telemetryClient.trackEvent("sentencing-adjustment-deleted-failed", telemetryMap, null)
      throw e
    }
  }

  suspend fun createSentencingAdjustmentMapping(message: CreateMappingRetryMessage<SentencingAdjustmentMappingDto>) =
    sentencingAdjustmentsMappingService.createMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "sentencing-adjustment-create-success",
        message.telemetryAttributes,
        null,
      )
    }

  override suspend fun retryCreateMapping(message: String) = createSentencingAdjustmentMapping(message.fromJson())

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}

data class AdditionalInformation(
  val id: String,
  val nomsNumber: String,
)

data class AdjustmentCreatedEvent(
  val additionalInformation: AdditionalInformation,
)

data class AdjustmentUpdatedEvent(
  val additionalInformation: AdditionalInformation,
)

data class AdjustmentDeletedEvent(
  val additionalInformation: AdditionalInformation,
)
