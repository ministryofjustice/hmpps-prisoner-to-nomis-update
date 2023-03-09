package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import jakarta.validation.ValidationException
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CancelVisitDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateVisitDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
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
        mappingService.getMappingGivenVsipId(visitBookedEvent.reference)
      }
      transform {
        visitsApiService.getVisit(visitBookedEvent.reference).let { visit ->
          eventTelemetry += mapOf(
            "prisonId" to visit.prisonId,
            "startDateTime" to visit.startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME),
            "endTime" to visit.endTimestamp.format(DateTimeFormatter.ISO_TIME),
          )

          nomisApiService.createVisit(
            CreateVisitDto(
              offenderNo = visit.prisonerId,
              prisonId = visit.prisonId,
              startDateTime = visit.startTimestamp,
              endTime = visit.endTimestamp.toLocalTime(),
              visitorPersonIds = visit.visitors.map { it.nomisPersonId },
              issueDate = visitBookedEvent.bookingDate,
              visitType = when (visit.visitType) {
                "SOCIAL" -> "SCON"
                "FAMILY" -> "SCON"
                else -> throw ValidationException("Invalid visit type ${visit.visitType}")
              },
              visitComment = "Created by Book A Prison Visit. Reference: ${visit.reference}",
              visitOrderComment = "Created by Book A Prison Visit for visit with reference: ${visit.reference}",
              room = visit.visitRoom,
              openClosedStatus = visit.visitRestriction,
            ),
          ).let { nomisId ->
            VisitMappingDto(nomisId = nomisId, vsipId = visitBookedEvent.reference, mappingType = "ONLINE")
          }
        }
      }
      saveMapping { mappingService.createMapping(it) }
    }
  }

  fun retryCreateVisitMapping(context: CreateMappingRetryMessage<VisitMapping>) {
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

  fun cancelVisit(visitCancelledEvent: VisitCancelledEvent) {
    val telemetryProperties = mutableMapOf(
      "offenderNo" to visitCancelledEvent.prisonerId,
      "visitId" to visitCancelledEvent.reference,
    )

    val mappingDto =
      mappingService.getMappingGivenVsipId(visitCancelledEvent.reference)
        ?: throw ValidationException("No mapping exists for VSIP id ${visitCancelledEvent.reference}")
          .also { telemetryClient.trackEvent("visit-cancelled-mapping-failed", telemetryProperties) }

    visitsApiService.getVisit(visitCancelledEvent.reference).run {
      nomisApiService.cancelVisit(
        CancelVisitDto(
          offenderNo = visitCancelledEvent.prisonerId,
          nomisVisitId = mappingDto.nomisId,
          outcome = getNomisOutcomeOrDefault(this),
        ),
      )

      telemetryProperties["nomisVisitId"] = mappingDto.nomisId

      telemetryClient.trackEvent("visit-cancelled-event", telemetryProperties)
    }
  }

  fun updateVisit(visitChangedEvent: VisitChangedEvent) {
    val telemetryProperties = mutableMapOf(
      "offenderNo" to visitChangedEvent.prisonerId,
      "visitId" to visitChangedEvent.reference,
    )

    val mappingDto =
      mappingService.getMappingGivenVsipId(visitChangedEvent.reference)
        ?: throw ValidationException("No mapping exists for VSIP id ${visitChangedEvent.reference}")
          .also { telemetryClient.trackEvent("visit-changed-mapping-failed", telemetryProperties) }

    visitsApiService.getVisit(visitChangedEvent.reference).run {
      nomisApiService.updateVisit(
        visitChangedEvent.prisonerId,
        mappingDto.nomisId,
        UpdateVisitDto(
          startDateTime = this.startTimestamp,
          endTime = this.endTimestamp.toLocalTime(),
          visitorPersonIds = this.visitors.map { it.nomisPersonId },
          room = this.visitRoom,
          openClosedStatus = this.visitRestriction,
        ),
      )

      telemetryProperties["nomisVisitId"] = mappingDto.nomisId

      telemetryClient.trackEvent("visit-changed-event", telemetryProperties)
    }
  }

  private fun getNomisOutcomeOrDefault(vsipVisit: VisitDto): String =
    vsipVisit.outcomeStatus?.runCatching {
      VsipOutcomeStatus.valueOf(this)
    }?.getOrNull()?.let {
      vsipToNomisOutcomeMap[it]?.name
    } ?: NomisCancellationOutcome.ADMIN.name

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
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
