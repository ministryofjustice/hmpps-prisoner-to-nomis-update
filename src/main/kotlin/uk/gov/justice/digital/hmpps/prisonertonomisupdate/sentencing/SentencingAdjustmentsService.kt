package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateSentencingAdjustmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.SynchronisationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpdateSentencingAdjustmentRequest

@Service
class SentencingAdjustmentsService(
  private val sentencingAdjustmentsApiService: SentencingAdjustmentsApiService,
  private val nomisApiService: NomisApiService,
  private val sentencingAdjustmentsMappingService: SentencingAdjustmentsMappingService,
  private val sentencingUpdateQueueService: SentencingUpdateQueueService,
  private val telemetryClient: TelemetryClient,
  objectMapper: ObjectMapper,
) : SynchronisationService(objectMapper = objectMapper) {
  suspend fun createAdjustment(createEvent: AdjustmentCreatedEvent) {
    sentencingAdjustmentsMappingService.getMappingGivenAdjustmentId(createEvent.additionalInformation.id)
      ?.let {
        telemetryClient.trackEvent(
          "sentencing-adjustment-create-duplicate",
          mapOf(
            "adjustmentId" to createEvent.additionalInformation.id,
            "offenderNo" to createEvent.additionalInformation.nomsNumber,
          ),
          null,
        )
      } ?: let {
      sentencingAdjustmentsApiService.getAdjustment(createEvent.additionalInformation.id).let { adjustment ->
        if (adjustment.creatingSystem != CreatingSystem.NOMIS) {
          val nomisAdjustmentRequest = CreateSentencingAdjustmentRequest(
            adjustmentTypeCode = adjustment.adjustmentType,
            adjustmentDate = adjustment.adjustmentDate,
            adjustmentFromDate = adjustment.adjustmentStartPeriod,
            adjustmentDays = adjustment.adjustmentDays,
            comment = adjustment.comment,
          )
          if (adjustment.sentenceSequence == null) {
            nomisApiService.createKeyDateAdjustment(
              adjustment.bookingId,
              nomisAdjustmentRequest,
            )
          } else {
            nomisApiService.createSentenceAdjustment(
              adjustment.bookingId,
              adjustment.sentenceSequence,
              nomisAdjustmentRequest,
            )
          }.also { createdNomisAdjustment ->
            val mapping = SentencingAdjustmentMappingDto(
              nomisAdjustmentId = createdNomisAdjustment.id,
              nomisAdjustmentCategory = if (adjustment.sentenceSequence == null) "KEY-DATE" else "SENTENCE",
              adjustmentId = adjustment.adjustmentId,
            )
            kotlin.runCatching {
              sentencingAdjustmentsMappingService.createMapping(mapping)
            }
              .onFailure {
                sentencingUpdateQueueService.sendMessage(createEvent.additionalInformation.nomsNumber, mapping)
              }
              .onSuccess {
                telemetryClient.trackEvent(
                  "sentencing-adjustment-create-success",
                  mapOf(
                    "adjustmentId" to adjustment.adjustmentId,
                    "nomisAdjustmentId" to createdNomisAdjustment.id.toString(),
                    "offenderNo" to createEvent.additionalInformation.nomsNumber,
                  ),
                  null,
                )
              }
          }
        } else {
          telemetryClient.trackEvent(
            "sentencing-adjustment-create-ignored",
            mapOf(
              "adjustmentId" to adjustment.adjustmentId,
              "offenderNo" to createEvent.additionalInformation.nomsNumber,
            ),
            null,
          )
        }
      }
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

  suspend fun createSentencingAdjustmentMapping(message: SentencingAdjustmentCreateMappingRetryMessage) =
    sentencingAdjustmentsMappingService.createMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "sentencing-adjustment-create-success",
        mapOf(
          "adjustmentId" to message.mapping.adjustmentId,
          "nomisAdjustmentId" to message.mapping.nomisAdjustmentId.toString(),
          "offenderNo" to message.offenderNo,
        ),
        null,
      )
    }

  override suspend fun retryCreateMapping(message: String) = createSentencingAdjustmentMapping(message.fromJson())
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
