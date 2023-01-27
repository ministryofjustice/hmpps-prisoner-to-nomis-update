package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateSentencingAdjustmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService

@Service
class SentencingAdjustmentsService(
  private val sentencingAdjustmentsApiService: SentencingAdjustmentsApiService,
  private val nomisApiService: NomisApiService,
  private val sentencingAdjustmentsMappingService: SentencingAdjustmentsMappingService,
  private val telemetryClient: TelemetryClient
) {
  suspend fun createAdjustment(createEvent: AdjustmentCreatedEvent) {
    sentencingAdjustmentsMappingService.getMappingGivenSentenceAdjustmentId(createEvent.additionalInformation.id)
      ?.let {
        telemetryClient.trackEvent(
          "sentencing-adjustment-create-duplicate",
          mapOf(
            "adjustmentId" to createEvent.additionalInformation.id,
            "offenderNo" to createEvent.additionalInformation.nomsNumber,
          ),
          null
        )
      } ?: let {
      sentencingAdjustmentsApiService.getAdjustment(createEvent.additionalInformation.id).let { adjustment ->
        if (adjustment.creatingSystem != CreatingSystem.NOMIS) {
          val nomisAdjustmentRequest = CreateSentencingAdjustmentRequest(
            adjustmentTypeCode = adjustment.adjustmentType,
            adjustmentDate = adjustment.adjustmentDate,
            adjustmentFomDate = adjustment.adjustmentStartPeriod,
            adjustmentDays = adjustment.adjustmentDays,
            comment = adjustment.comment,
          )
          if (adjustment.sentenceSequence == null) {
            nomisApiService.createKeyDateAdjustment(
              adjustment.bookingId,
              nomisAdjustmentRequest
            )
          } else {
            nomisApiService.createSentenceAdjustment(
              adjustment.bookingId,
              adjustment.sentenceSequence,
              nomisAdjustmentRequest
            )
          }.also { createdNomisAdjustment ->
            sentencingAdjustmentsMappingService.createMapping(
              SentencingAdjustmentMappingDto(
                nomisAdjustmentId = createdNomisAdjustment.id,
                nomisAdjustmentType = if (adjustment.sentenceSequence == null) "BOOKING" else "SENTENCE",
                sentenceAdjustmentId = adjustment.adjustmentId
              )
            )
            telemetryClient.trackEvent(
              "sentencing-adjustment-create-success",
              mapOf(
                "sentenceAdjustmentId" to adjustment.adjustmentId,
                "nomisAdjustmentId" to createdNomisAdjustment.id.toString(),
                "offenderNo" to createEvent.additionalInformation.nomsNumber,
              ),
              null
            )
          }
        } else {
          telemetryClient.trackEvent(
            "sentencing-adjustment-create-ignored",
            mapOf(
              "adjustmentId" to adjustment.adjustmentId,
              "offenderNo" to createEvent.additionalInformation.nomsNumber,
            ),
            null
          )
        }
      }
    }
  }
}

data class AdditionalInformation(
  val id: String,
  val nomsNumber: String,
)

data class AdjustmentCreatedEvent(
  val additionalInformation: AdditionalInformation,
)