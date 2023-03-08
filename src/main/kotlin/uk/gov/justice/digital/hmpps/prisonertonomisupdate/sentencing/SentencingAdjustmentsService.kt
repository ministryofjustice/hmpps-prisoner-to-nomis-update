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
        sentencingAdjustmentsMappingService.getMappingGivenAdjustmentId(createEvent.additionalInformation.id)
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
    sentencingAdjustmentsMappingService.getMappingGivenAdjustmentId(createEvent.additionalInformation.id)
      ?.also { mapping ->
        sentencingAdjustmentsApiService.getAdjustment(createEvent.additionalInformation.id).also { adjustment ->
          if (adjustment.creatingSystem != CreatingSystem.NOMIS) {
            val nomisAdjustmentRequest = UpdateSentencingAdjustmentRequest(
              adjustmentTypeCode = adjustment.adjustmentType,
              adjustmentDate = adjustment.adjustmentDate,
              adjustmentFromDate = adjustment.adjustmentStartPeriod,
              adjustmentDays = adjustment.adjustmentDays,
              comment = adjustment.comment,
            )
            if (adjustment.sentenceSequence == null) {
              nomisApiService.updateKeyDateAdjustment(
                mapping.nomisAdjustmentId,
                nomisAdjustmentRequest,
              )
            } else {
              nomisApiService.updateSentenceAdjustment(
                mapping.nomisAdjustmentId,
                nomisAdjustmentRequest,
              )
            }.also {
              telemetryClient.trackEvent(
                "sentencing-adjustment-updated-success",
                mapOf(
                  "adjustmentId" to adjustment.adjustmentId,
                  "nomisAdjustmentId" to mapping.nomisAdjustmentId.toString(),
                  "offenderNo" to createEvent.additionalInformation.nomsNumber,
                ),
                null,
              )
            }
          } else {
            telemetryClient.trackEvent(
              "sentencing-adjustment-updated-ignored",
              mapOf(
                "adjustmentId" to adjustment.adjustmentId,
                "offenderNo" to createEvent.additionalInformation.nomsNumber,
              ),
              null,
            )
          }
        }
      }
      ?: throw RuntimeException("No mapping found for adjustment ${createEvent.additionalInformation.id}, maybe we never received a create")
  }

  suspend fun deleteAdjustment(createEvent: AdjustmentDeletedEvent) {
    sentencingAdjustmentsMappingService.getMappingGivenAdjustmentId(createEvent.additionalInformation.id)
      ?.also { mapping ->
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
          telemetryClient.trackEvent(
            "sentencing-adjustment-deleted-success",
            mapOf(
              "adjustmentId" to mapping.adjustmentId,
              "nomisAdjustmentId" to mapping.nomisAdjustmentId.toString(),
              "nomisAdjustmentCategory" to mapping.nomisAdjustmentCategory,
              "offenderNo" to createEvent.additionalInformation.nomsNumber,
            ),
            null,
          )
        }
      }
      ?: throw RuntimeException("No mapping found for adjustment ${createEvent.additionalInformation.id}, maybe we never received a create")
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
