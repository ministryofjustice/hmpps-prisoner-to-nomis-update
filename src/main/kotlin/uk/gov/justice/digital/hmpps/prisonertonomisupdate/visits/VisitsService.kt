package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.applicationinsights.TelemetryClient
import jakarta.validation.ValidationException
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitRequest.OpenClosedStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitRequest.VisitType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CancelVisitDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService.CreateVisitDuplicateResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpdateVisitDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

@Service
class VisitsService(
  private val visitsApiService: VisitsApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: VisitsMappingService,
  private val visitsUpdateQueueService: VisitsUpdateQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {

  suspend fun resynchronisePrisonerVisit(offenderNo: String, visitId: String) = createVisit(
    VisitBookedEvent(
      additionalInformation = VisitBookedEvent.VisitInformation(
        reference = visitId,
      ),
      occurredAt = OffsetDateTime.now(),
      prisonerId = offenderNo,
    ),
  )
  suspend fun createVisit(visitBookedEvent: VisitBookedEvent) {
    synchronise {
      name = "visit"
      telemetryClient = this@VisitsService.telemetryClient
      retryQueueService = visitsUpdateQueueService
      eventTelemetry = mapOf(
        "visitId" to visitBookedEvent.reference,
        "offenderNo" to visitBookedEvent.prisonerId,
      )

      checkMappingDoesNotExist {
        mappingService.getMappingGivenVsipIdOrNull(visitBookedEvent.reference)
      }
      transform {
        visitsApiService.getVisit(visitBookedEvent.reference).let { visit ->
          eventTelemetry += mapOf(
            "prisonId" to visit.prisonId,
            "startDateTime" to visit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME),
            "endTime" to visit.endTimestamp.format(DateTimeFormatter.ISO_TIME),
          )

          runCatching {
            nomisApiService.createVisit(
              visit.prisonerId,
              CreateVisitRequest(
                prisonId = visit.prisonId,
                startDateTime = visit.startTimestamp,
                endTime = visit.endTimestamp.toLocalTime().toString(),
                visitorPersonIds = visit.visitors.map { it.nomisPersonId },
                issueDate = visitBookedEvent.bookingDate,
                visitType = VisitType.valueOf(
                  when (visit.visitType) {
                    "SOCIAL" -> "SCON"
                    "FAMILY" -> "SCON"
                    else -> throw ValidationException("Invalid visit type ${visit.visitType}")
                  },
                ),
                visitComment = visit.visitorSupport?.let {
                  "DPS booking reference: ${visit.reference} - ${it.description}"
                } ?: "DPS booking reference: ${visit.reference}",
                visitOrderComment = "DPS booking reference: ${visit.reference}",
                room = visit.visitRoom,
                openClosedStatus = OpenClosedStatus.valueOf(visit.visitRestriction),
              ),
            )
          }.map {
            VisitMappingDto(nomisId = it.visitId, vsipId = visitBookedEvent.reference, mappingType = "ONLINE")
          }.recover {
            when (it) {
              is CreateVisitDuplicateResponse -> {
                VisitMappingDto(
                  nomisId = it.nomisVisitId,
                  vsipId = visitBookedEvent.reference,
                  mappingType = "ONLINE",
                ).takeIf { doesMappingStillNotExist(visitBookedEvent.reference) }
              }
              else -> throw it
            }
          }.getOrNull()
        }
      }
      saveMapping { mappingService.createMapping(it) }
    }
  }

  suspend fun doesMappingStillNotExist(vsipId: String): Boolean = mappingService.getMappingGivenVsipIdOrNull(vsipId) == null

  suspend fun retryCreateVisitMapping(context: CreateMappingRetryMessage<VisitMapping>) {
    mappingService.createMapping(
      VisitMappingDto(nomisId = context.mapping.nomisId, vsipId = context.mapping.vsipId, mappingType = "ONLINE"),
    ).also {
      telemetryClient.trackEvent(
        "visit-retry-success",
        mapOf("id" to context.mapping.vsipId, "nomisId" to context.mapping.nomisId),
      )
    }
  }

  override suspend fun retryCreateMapping(message: String) = retryCreateVisitMapping(message.fromJson())

  suspend fun cancelVisit(visitCancelledEvent: VisitCancelledEvent) {
    val vsipVisitId = visitCancelledEvent.reference
    val offenderNo = visitCancelledEvent.prisonerId
    val telemetryProperties = mutableMapOf(
      "offenderNo" to offenderNo,
      "visitId" to vsipVisitId,
    )

    runCatching {
      val nomisVisitId = mappingService.getMappingGivenVsipId(vsipVisitId).nomisId.also {
        telemetryProperties["nomisVisitId"] = it
      }
      visitsApiService.getVisit(vsipVisitId).run {
        nomisApiService.cancelVisit(
          CancelVisitDto(
            offenderNo = offenderNo,
            nomisVisitId = nomisVisitId,
            outcome = getNomisOutcomeOrDefault(this),
          ),
        )
      }
    }.onSuccess {
      telemetryClient.trackEvent("visit-cancelled-success", telemetryProperties)
    }.onFailure { e ->
      telemetryClient.trackEvent("visit-cancelled-failed", telemetryProperties)
      throw e
    }
  }

  suspend fun updateVisit(visitChangedEvent: VisitChangedEvent) {
    val vsipVisitId = visitChangedEvent.reference
    val offenderNo = visitChangedEvent.prisonerId
    val telemetryProperties = mutableMapOf(
      "offenderNo" to offenderNo,
      "visitId" to vsipVisitId,
    )

    runCatching {
      val nomisVisitId = mappingService.getMappingGivenVsipId(vsipVisitId).nomisId.also {
        telemetryProperties["nomisVisitId"] = it
      }
      visitsApiService.getVisit(vsipVisitId).run {
        nomisApiService.updateVisit(
          visitChangedEvent.prisonerId,
          nomisVisitId,
          UpdateVisitDto(
            startDateTime = this.startTimestamp,
            endTime = this.endTimestamp.toLocalTime(),
            visitorPersonIds = this.visitors.map { it.nomisPersonId },
            room = this.visitRoom,
            openClosedStatus = this.visitRestriction,
            visitComment = this.visitorSupport?.let {
              "DPS booking reference: ${this.reference} - ${it.description}"
            } ?: "DPS booking reference: ${this.reference}",
          ),
        )
      }
    }.onSuccess {
      telemetryClient.trackEvent("visit-changed-success", telemetryProperties)
    }.onFailure { e ->
      telemetryClient.trackEvent("visit-changed-failed", telemetryProperties)
      throw e
    }
  }

  private fun getNomisOutcomeOrDefault(vsipVisit: VisitDto): String = vsipVisit.outcomeStatus?.runCatching {
    VsipOutcomeStatus.valueOf(this)
  }?.getOrNull()?.let {
    vsipToNomisOutcomeMap[it]?.name
  } ?: NomisCancellationOutcome.ADMIN.name

  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this, object : TypeReference<T>() {})
}

data class VisitBookedEvent(
  val additionalInformation: VisitInformation,
  val occurredAt: OffsetDateTime,
  val prisonerId: String,
) {
  val bookingDate: LocalDate
    get() = occurredAt.toLocalDate()

  val reference: String
    get() = additionalInformation.reference

  data class VisitInformation(
    val reference: String,
  )
}

data class VisitCancelledEvent(
  val additionalInformation: VisitInformation,
  val occurredAt: OffsetDateTime,
  val prisonerId: String,
) {

  val reference: String
    get() = additionalInformation.reference

  data class VisitInformation(
    val reference: String,
  )
}

data class VisitChangedEvent(
  val additionalInformation: VisitInformation,
  val occurredAt: OffsetDateTime,
  val prisonerId: String,
) {

  val reference: String
    get() = additionalInformation.reference

  data class VisitInformation(
    val reference: String,
  )
}

enum class VsipOutcomeStatus {
  ADMINISTRATIVE_CANCELLATION,
  ADMINISTRATIVE_ERROR,
  BATCH_CANCELLATION,
  CANCELLATION,
  COMPLETED_NORMALLY,
  ESTABLISHMENT_CANCELLED,
  NOT_RECORDED,
  NO_VISITING_ORDER,
  PRISONER_CANCELLED,
  PRISONER_COMPLETED_EARLY,
  PRISONER_REFUSED_TO_ATTEND,
  TERMINATED_BY_STAFF,
  VISITOR_CANCELLED,
  VISITOR_COMPLETED_EARLY,
  VISITOR_DECLINED_ENTRY,
  VISITOR_DID_NOT_ARRIVE,
  VISITOR_FAILED_SECURITY_CHECKS,
  VISIT_ORDER_CANCELLED,
}

enum class NomisCancellationOutcome {
  ADMIN,
  HMP,
  NO_ID,
  NO_VO,
  NSHOW,
  OFFCANC,
  REFUSED,
  VISCANC,
  VO_CANCEL,
}

private val vsipToNomisOutcomeMap = mutableMapOf(
  VsipOutcomeStatus.ADMINISTRATIVE_CANCELLATION to NomisCancellationOutcome.ADMIN,
  VsipOutcomeStatus.ADMINISTRATIVE_ERROR to NomisCancellationOutcome.ADMIN,
  VsipOutcomeStatus.ESTABLISHMENT_CANCELLED to NomisCancellationOutcome.HMP,
  VsipOutcomeStatus.VISITOR_FAILED_SECURITY_CHECKS to NomisCancellationOutcome.NO_ID,
  VsipOutcomeStatus.NO_VISITING_ORDER to NomisCancellationOutcome.NO_VO,
  VsipOutcomeStatus.VISITOR_DID_NOT_ARRIVE to NomisCancellationOutcome.NSHOW,
  VsipOutcomeStatus.PRISONER_CANCELLED to NomisCancellationOutcome.OFFCANC,
  VsipOutcomeStatus.PRISONER_REFUSED_TO_ATTEND to NomisCancellationOutcome.REFUSED,
  VsipOutcomeStatus.VISITOR_CANCELLED to NomisCancellationOutcome.VISCANC,
  VsipOutcomeStatus.VISIT_ORDER_CANCELLED to NomisCancellationOutcome.VO_CANCEL,
  VsipOutcomeStatus.BATCH_CANCELLATION to NomisCancellationOutcome.ADMIN,
  VsipOutcomeStatus.PRISONER_REFUSED_TO_ATTEND to NomisCancellationOutcome.REFUSED,
)
