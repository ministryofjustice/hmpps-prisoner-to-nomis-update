package uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.sentencing.adjustments.model.LegacyAdjustment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateSentencingAdjustmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreatingSystem
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpdateSentencingAdjustmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.LocalDate

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
    val telemetryMap = mapOf(
      "adjustmentId" to createEvent.additionalInformation.id,
      "offenderNo" to createEvent.additionalInformation.offenderNo,
    )
    if (isDpsCreated(createEvent.additionalInformation)) {
      synchronise {
        name = "sentencing-adjustment"
        telemetryClient = this@SentencingAdjustmentsService.telemetryClient
        retryQueueService = sentencingRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          sentencingAdjustmentsMappingService.getMappingGivenAdjustmentIdOrNull(createEvent.additionalInformation.id)
        }
        transform {
          sentencingAdjustmentsApiService.getAdjustmentOrNull(createEvent.additionalInformation.id)
            ?.let { adjustment ->
              SentencingAdjustmentMappingDto(
                nomisAdjustmentId = createTransformedAdjustment(adjustment).id,
                nomisAdjustmentCategory = if (adjustment.sentenceSequence == null) "KEY-DATE" else "SENTENCE",
                adjustmentId = createEvent.additionalInformation.id,
              )
            } ?: run {
            this@SentencingAdjustmentsService.telemetryClient.trackEvent(
              "sentencing-adjustment-create-skipped",
              telemetryMap,
              null,
            )
            null
          }
        }
        saveMapping { sentencingAdjustmentsMappingService.createMapping(it) }
      }
    } else {
      telemetryClient.trackEvent(
        "sentencing-adjustment-create-ignored",
        telemetryMap,
        null,
      )
    }
  }

  private fun isDpsCreated(additionalInformation: AdditionalInformation) = isDpsCreated(additionalInformation.source)

  private fun isDpsCreated(source: String) = source != CreatingSystem.NOMIS.name

  private suspend fun createTransformedAdjustment(adjustment: LegacyAdjustment) = CreateSentencingAdjustmentRequest(
    adjustmentTypeCode = adjustment.adjustmentType.value,
    adjustmentDate = adjustment.adjustmentDate ?: LocalDate.now(),
    adjustmentFromDate = adjustment.adjustmentFromDate,
    adjustmentDays = adjustment.adjustmentDays.toLong(),
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
        adjustment.sentenceSequence!!.toLong(),
        this,
      )
    }
  }

  suspend fun repairAdjustment(offenderNo: String, adjustmentId: String, forceStatus: Boolean) = updateAdjustment(
    offenderNo = offenderNo,
    adjustmentId = adjustmentId,
    source = "DPS",
    forceStatus = forceStatus,
  )

  suspend fun updateAdjustment(createEvent: AdjustmentUpdatedEvent) = updateAdjustment(
    offenderNo = createEvent.additionalInformation.offenderNo,
    adjustmentId = createEvent.additionalInformation.id,
    source = createEvent.additionalInformation.source,
    forceStatus = false,
  )

  suspend fun updateAdjustment(offenderNo: String, adjustmentId: String, source: String, forceStatus: Boolean) {
    val telemetryMap = mutableMapOf("adjustmentId" to adjustmentId, "offenderNo" to offenderNo)
    if (isDpsCreated(source)) {
      runCatching {
        val mapping = sentencingAdjustmentsMappingService.getMappingGivenAdjustmentIdOrNull(adjustmentId)
        val adjustment = sentencingAdjustmentsApiService.getAdjustmentOrNull(adjustmentId)
        if (mapping == null && adjustment == null) {
          // adjustment has been deleted, so no need to try to process this update
          telemetryClient.trackEvent("sentencing-adjustment-updated-skipped", telemetryMap, null)
          return
        } else if (mapping == null || adjustment == null) {
          throw IllegalStateException("Mapping or adjustment not found for adjustment $adjustmentId. Mapping=$mapping, adjustment=$adjustment")
        }
        val nomisAdjustmentId =
          sentencingAdjustmentsMappingService.getMappingGivenAdjustmentId(adjustmentId).nomisAdjustmentId
            .also { telemetryMap["nomisAdjustmentId"] = it.toString() }

        sentencingAdjustmentsApiService.getAdjustment(adjustmentId).let { adjustment ->
          updateTransformedAdjustment(nomisAdjustmentId, adjustment, forceStatus)
          telemetryClient.trackEvent("sentencing-adjustment-updated-success", telemetryMap, null)
        }
      }.onFailure { e ->
        telemetryClient.trackEvent("sentencing-adjustment-updated-failed", telemetryMap, null)
        throw e
      }
    } else {
      telemetryClient.trackEvent("sentencing-adjustment-updated-ignored", telemetryMap, null)
    }
  }

  private suspend fun updateTransformedAdjustment(nomisAdjustmentId: Long, adjustment: LegacyAdjustment, forceStatus: Boolean) = UpdateSentencingAdjustmentRequest(
    adjustmentTypeCode = adjustment.adjustmentType.value,
    adjustmentDate = adjustment.adjustmentDate ?: LocalDate.now(),
    adjustmentFromDate = adjustment.adjustmentFromDate,
    adjustmentDays = adjustment.adjustmentDays.toLong(),
    comment = adjustment.comment,
    sentenceSequence = adjustment.sentenceSequence,
  ).let {
    if (forceStatus) {
      it.copy(active = adjustment.active)
    } else {
      it
    }
  }.run {
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
    val offenderNo = createEvent.additionalInformation.offenderNo
    val telemetryMap = mutableMapOf("adjustmentId" to adjustmentId, "offenderNo" to offenderNo)

    if (isDpsCreated(createEvent.additionalInformation)) {
      runCatching {
        sentencingAdjustmentsMappingService.getMappingGivenAdjustmentIdOrNull(adjustmentId)?.also { mapping ->
          telemetryMap["nomisAdjustmentId"] = mapping.nomisAdjustmentId.toString()
          telemetryMap["nomisAdjustmentCategory"] = mapping.nomisAdjustmentCategory

          if (mapping.nomisAdjustmentCategory == "SENTENCE") {
            nomisApiService.deleteSentenceAdjustment(
              mapping.nomisAdjustmentId,
            )
          } else {
            nomisApiService.deleteKeyDateAdjustment(
              mapping.nomisAdjustmentId,
            )
          }.also {
            sentencingAdjustmentsMappingService.deleteMappingGivenAdjustmentId(adjustmentId)
          }
        } ?: run {
          telemetryClient.trackEvent("sentencing-adjustment-deleted-ignored", telemetryMap + ("reason" to "mapping-already-deleted"), null)
        }
      }.onSuccess {
        telemetryClient.trackEvent("sentencing-adjustment-deleted-success", telemetryMap, null)
      }.onFailure { e ->
        telemetryClient.trackEvent("sentencing-adjustment-deleted-failed", telemetryMap, null)
        throw e
      }
    } else {
      telemetryClient.trackEvent("sentencing-adjustment-deleted-ignored", telemetryMap, null)
    }
  }

  suspend fun createSentencingAdjustmentMapping(message: CreateMappingRetryMessage<SentencingAdjustmentMappingDto>) = sentencingAdjustmentsMappingService.createMapping(message.mapping).also {
    telemetryClient.trackEvent(
      "sentencing-adjustment-create-success",
      message.telemetryAttributes,
      null,
    )
  }

  override suspend fun retryCreateMapping(message: String) = createSentencingAdjustmentMapping(message.fromJson())

  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)
}

data class AdditionalInformation(
  val id: String,
  val offenderNo: String,
  val source: String,
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
