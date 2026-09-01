package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra.model.CsraReviewDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CsraMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AssessmentLevel
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AssessmentStatusType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AssessmentType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CsraCreateDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.TelemetryEnabled
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise

@Service
class CsraService(
  private val csraDpsApiService: CsraDpsApiService,
  private val csraNomisApiService: CsraNomisApiService,
  private val csraMappingService: CsraMappingService,
  private val csraRetryQueueService: CsraRetryQueueService,
  override val telemetryClient: TelemetryClient,
  private val jsonMapper: JsonMapper,
) : CreateMappingRetryable,
  TelemetryEnabled {
  suspend fun created(event: CsraDomainEvent) {
    val telemetry = event.asTelemetry()
    if (event.originatedInDps()) {
      val (dpsId, offenderNo) = event.additionalInformation
      if (dpsId == null || offenderNo == null) {
        telemetryClient.trackEvent("csra-create-failed", telemetry, null)
        return
      }
      synchronise {
        name = "csra"
        telemetryClient = this@CsraService.telemetryClient
        retryQueueService = csraRetryQueueService
        eventTelemetry = telemetry

        checkMappingDoesNotExist {
          csraMappingService.getMappingByDpsIdOrNull(dpsId.toString())
        }
        transform {
          csraDpsApiService.getCsraReview(dpsId).run {
            val request = toNomisCreateRequest()

            eventTelemetry += "type" to request.type.toString()

            csraNomisApiService.createCsra(offenderNo, request).run {
              CsraMappingDto(
                dpsCsraId = dpsId.toString(),
                nomisBookingId = bookingId,
                nomisSequence = sequence,
                offenderNo = offenderNo,
                mappingType = CsraMappingDto.MappingType.DPS_CREATED,
              )
            }
          }
        }
        saveMapping { csraMappingService.create(it) }
      }
    } else {
      telemetryClient.trackEvent("csra-create-ignored", telemetry, null)
    }
  }

  suspend fun updated(event: CsraDomainEvent) {
    val telemetry = event.asTelemetry()
    // TODO: I dont think this is needed, as we don't update CSRA in DPS, only create.
    //    if (event.originatedInDps()) {
//      track("csra-update", telemetry) {
//        val dpsId = event.additionalInformation.id!!
//        val dpsData = csraDpsApiService.getCsraReview((dpsId))
//        val mapping = csraMappingService.getMappingByDpsId(dpsId.toString())
//        val nomisId = mapping.nomisBookingId, sequence
//        telemetry += "nomisBookingId" to nomisId.toString()
//
//        csraNomisApiService.updateCsra(nomisId, dpsData.toNomisUpdateRequest())
//      }
//    } else {
    telemetryClient.trackEvent("csra-update-ignored", telemetry, null)
  }

  override suspend fun retryCreateMapping(message: String) {
    val (mapping, telemetryAttributes) = message.fromJson<CreateMappingRetryMessage<CsraMappingDto>>()
    csraMappingService.create(
      CsraMappingDto(
        dpsCsraId = mapping.dpsCsraId,
        nomisBookingId = mapping.nomisBookingId,
        nomisSequence = mapping.nomisSequence,
        offenderNo = mapping.offenderNo,
        mappingType = CsraMappingDto.MappingType.DPS_CREATED,
      ),
    )
      .also {
        telemetryClient.trackEvent(
          "csra-mapping-create-success",
          telemetryAttributes,
          null,
        )
      }
  }

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}

private fun CsraDomainEvent.originatedInDps(): Boolean = additionalInformation.source == InformationSource.DPS

fun CsraDomainEvent.asTelemetry() = mutableMapOf(
  "dpsCsraId" to additionalInformation.id.toString(),
  "offenderNo" to additionalInformation.nomsNumber.toString(),
  "source" to additionalInformation.source.toString(),
)

private fun CsraReviewDetail.toNomisCreateRequest() = CsraCreateDto(
  assessmentDate = assessmentDate,
  type = type.toNomisAssessmentType(),
  calculatedLevel = finalResult?.toNomisLevel() ?: interimResult?.toNomisLevel() ?: AssessmentLevel.STANDARD,
  // DPS does not hold a numeric CSRA score for a review, only a rating/level, so this is not populated
  score = null,
  status = if (finalResult != null) AssessmentStatusType.A else AssessmentStatusType.P,
  createdDateTime = createdAt,
  createdBy = createdBy,
)

// private fun CsraReviewDetail.toNomisUpdateRequest() = CsraUpdateDto(
//   reviewLevel = finalResult?.toNomisLevel(),
//   status = if (finalResult != null) AssessmentStatusType.A else AssessmentStatusType.P,
// )

private fun CsraReviewDetail.Type.toNomisAssessmentType(): AssessmentType = when (this) {
  CsraReviewDetail.Type.FULL -> AssessmentType.CSRF
  CsraReviewDetail.Type.HEALTH -> AssessmentType.CSRH
  CsraReviewDetail.Type.LOCATE -> AssessmentType.CSRDO
  CsraReviewDetail.Type.RATING -> AssessmentType.CSR
  CsraReviewDetail.Type.RECEPTION -> AssessmentType.CSR1
  CsraReviewDetail.Type.REVIEW -> AssessmentType.CSRREV
  CsraReviewDetail.Type.CSRA_INITIAL_REVIEW -> AssessmentType.CSR
  CsraReviewDetail.Type.CSRA_REVIEW -> AssessmentType.CSRREV
}

private fun CsraReviewDetail.FinalResult.toNomisLevel(): AssessmentLevel = when (this) {
  CsraReviewDetail.FinalResult.HIGH -> AssessmentLevel.HI
  CsraReviewDetail.FinalResult.HIGH_GENERAL -> AssessmentLevel.HI
  CsraReviewDetail.FinalResult.HIGH_SPECIFIC -> AssessmentLevel.HI
  CsraReviewDetail.FinalResult.STANDARD -> AssessmentLevel.STANDARD
}

private fun CsraReviewDetail.InterimResult.toNomisLevel(): AssessmentLevel = when (this) {
  CsraReviewDetail.InterimResult.HIGH -> AssessmentLevel.HI
  CsraReviewDetail.InterimResult.HIGH_GENERAL -> AssessmentLevel.HI
  CsraReviewDetail.InterimResult.HIGH_SPECIFIC -> AssessmentLevel.HI
  CsraReviewDetail.InterimResult.STANDARD -> AssessmentLevel.STANDARD
}
