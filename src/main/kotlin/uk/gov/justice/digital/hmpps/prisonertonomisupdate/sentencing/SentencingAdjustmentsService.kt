package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateSentencingAdjustmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateSentencingAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.SynchronisationService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpdateSentencingAdjustmentRequest

data class NomisCreateId(val bookingId: Long, val sentenceSequence: Long?)

@Service
class SentencingAdjustmentsService(
  private val sentencingAdjustmentsApiService: SentencingAdjustmentsApiService,
  private val nomisApiService: NomisApiService,
  private val sentencingAdjustmentsMappingService: SentencingAdjustmentsMappingService,
  sentencingUpdateQueueService: SentencingUpdateQueueService<SentencingAdjustmentMappingDto>,
  telemetryClient: TelemetryClient,
  objectMapper: ObjectMapper
) :
  SynchronisationService<String, AdjustmentDetails, NomisCreateId, CreateSentencingAdjustmentRequest, CreateSentencingAdjustmentResponse, SentencingAdjustmentMappingDto>(
    objectMapper = objectMapper,
    mappingService = sentencingAdjustmentsMappingService,
    telemetryClient = telemetryClient,
    dpsService = sentencingAdjustmentsApiService,
    name = "sentencing-adjustment",
    queueService = sentencingUpdateQueueService,
  ) {

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
                nomisAdjustmentRequest
              )
            } else {
              nomisApiService.updateSentenceAdjustment(
                mapping.nomisAdjustmentId,
                nomisAdjustmentRequest
              )
            }.also {
              telemetryClient.trackEvent(
                "sentencing-adjustment-updated-success",
                mapOf(
                  "adjustmentId" to adjustment.adjustmentId,
                  "nomisAdjustmentId" to mapping.nomisAdjustmentId.toString(),
                  "offenderNo" to createEvent.additionalInformation.nomsNumber,
                ),
                null
              )
            }
          } else {
            telemetryClient.trackEvent(
              "sentencing-adjustment-updated-ignored",
              mapOf(
                "adjustmentId" to adjustment.adjustmentId,
                "offenderNo" to createEvent.additionalInformation.nomsNumber,
              ),
              null
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
            null
          )
        }
      }
      ?: throw RuntimeException("No mapping found for adjustment ${createEvent.additionalInformation.id}, maybe we never received a create")
  }

  override fun createEventToTelemetry(message: String): Map<String, String> = toAdjustmentCreatedEvent(message).let {
    mapOf(
      "adjustmentId" to it.additionalInformation.id,
      "offenderNo" to it.additionalInformation.nomsNumber,
    )
  }

  override fun createEventToTelemetry(
    message: String,
    dpsId: String,
    nomisId: NomisCreateId,
    nomisResponse: CreateSentencingAdjustmentResponse
  ): Map<String, String> = createEventToTelemetry(message) + mapOf(
    "nomisAdjustmentId" to nomisResponse.id.toString()
  )

  override fun dpsIdFromCreateEvent(message: String) = toAdjustmentCreatedEvent(message).additionalInformation.id
  override suspend fun createMapping(mapping: SentencingAdjustmentMappingDto) =
    sentencingAdjustmentsMappingService.createMapping(mapping)

  override suspend fun retryCreateMapping(message: String) =
    createMapping(message.fromJson<CreateMappingRetryMessage<SentencingAdjustmentMappingDto>>().mapping)

  override fun mapping(
    dpsId: String,
    nomisCreateId: NomisCreateId,
    nomisAdjustment: CreateSentencingAdjustmentResponse
  ) =
    SentencingAdjustmentMappingDto(
      nomisAdjustmentId = nomisAdjustment.id,
      nomisAdjustmentCategory = if (nomisCreateId.sentenceSequence == null) "KEY-DATE" else "SENTENCE",
      adjustmentId = dpsId
    )

  override fun mapDPSCreateToNOMISId(entity: AdjustmentDetails) =
    NomisCreateId(entity.bookingId, entity.sentenceSequence)

  override suspend fun createNOMISEntity(id: NomisCreateId, nomisAdjustmentRequest: CreateSentencingAdjustmentRequest) =
    if (id.sentenceSequence == null) {
      nomisApiService.createKeyDateAdjustment(
        id.bookingId,
        nomisAdjustmentRequest
      )
    } else {
      nomisApiService.createSentenceAdjustment(
        id.bookingId,
        id.sentenceSequence,
        nomisAdjustmentRequest
      )
    }

  override fun mapDPSCreateToNOMISCreate(adjustment: AdjustmentDetails): CreateSentencingAdjustmentRequest =
    CreateSentencingAdjustmentRequest(
      adjustmentTypeCode = adjustment.adjustmentType,
      adjustmentDate = adjustment.adjustmentDate,
      adjustmentFromDate = adjustment.adjustmentStartPeriod,
      adjustmentDays = adjustment.adjustmentDays,
      comment = adjustment.comment,
    )

  private fun toAdjustmentCreatedEvent(message: String): AdjustmentCreatedEvent = objectMapper.readValue(message)

  override fun shouldCreate(adjustment: AdjustmentDetails) = adjustment.creatingSystem != CreatingSystem.NOMIS
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
