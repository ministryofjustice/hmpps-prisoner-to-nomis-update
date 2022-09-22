package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CancelVisitDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateVisitDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpdateQueueService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpdateVisitDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.VisitContext
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.validation.ValidationException

@Service
class PrisonVisitsService(
  private val visitsApiService: VisitsApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: VisitsMappingService,
  private val updateQueueService: UpdateQueueService,
  private val telemetryClient: TelemetryClient
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun createVisit(visitBookedEvent: VisitBookedEvent) {
    visitsApiService.getVisit(visitBookedEvent.reference).run {

      val telemetryMap = mutableMapOf(
        "offenderNo" to prisonerId,
        "prisonId" to prisonId,
        "visitId" to reference,
        "startDateTime" to startTimestamp.format(DateTimeFormatter.ISO_DATE_TIME),
        "endTime" to endTimestamp.format(DateTimeFormatter.ISO_TIME),
      )

      if (mappingService.getMappingGivenVsipId(visitBookedEvent.reference) != null) {
        telemetryClient.trackEvent("visit-booked-get-map-failed", telemetryMap)
        log.warn("Mapping already exists for VSIP id $reference")
        return
      }

      val nomisId = try {
        nomisApiService.createVisit(
          CreateVisitDto(
            offenderNo = this.prisonerId,
            prisonId = this.prisonId,
            startDateTime = this.startTimestamp,
            endTime = this.endTimestamp.toLocalTime(),
            visitorPersonIds = this.visitors.map { it.nomisPersonId },
            issueDate = visitBookedEvent.bookingDate,
            visitType = when (this.visitType) {
              "SOCIAL" -> "SCON"
              "FAMILY" -> "SCON"
              else -> throw ValidationException("Invalid visit type ${this.visitType}")
            },
            visitComment = "Created by Book A Prison Visit. Reference: ${this.reference}",
            visitOrderComment = "Created by Book A Prison Visit for visit with reference: ${this.reference}",
            room = this.visitRoom,
            openClosedStatus = this.visitRestriction,
          )
        )
      } catch (e: Exception) {
        telemetryClient.trackEvent("visit-booked-create-failed", telemetryMap)
        log.error("createVisit() Unexpected exception", e)
        throw e
      }

      val mapWithNomisId = telemetryMap.plus(Pair("nomisVisitId", nomisId))

      try {
        mappingService.createMapping(
          VisitMappingDto(nomisId = nomisId, vsipId = visitBookedEvent.reference, mappingType = "ONLINE")
        )
      } catch (e: Exception) {
        telemetryClient.trackEvent("visit-booked-create-map-failed", mapWithNomisId)
        log.error("Unexpected exception, queueing retry", e)
        updateQueueService.sendMessage(VisitContext(nomisId = nomisId, vsipId = visitBookedEvent.reference))
        return
      }

      telemetryClient.trackEvent("visit-booked-event", mapWithNomisId)
    }
  }

  fun createVisitRetry(context: VisitContext) {
    mappingService.createMapping(
      VisitMappingDto(nomisId = context.nomisId, vsipId = context.vsipId, mappingType = "ONLINE")
    )
  }

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
          outcome = getNomisOutcomeOrDefault(this)
        )
      )

      telemetryClient.trackEvent(
        "visit-cancelled-event",
        telemetryProperties.plus(Pair("nomisVisitId", mappingDto.nomisId))
      )
    }
  }

  fun updateVisit(visitCancelledEvent: VisitChangedEvent) {
    val telemetryProperties = mutableMapOf(
      "offenderNo" to visitCancelledEvent.prisonerId,
      "visitId" to visitCancelledEvent.reference,
    )

    val mappingDto =
      mappingService.getMappingGivenVsipId(visitCancelledEvent.reference)
        ?: throw ValidationException("No mapping exists for VSIP id ${visitCancelledEvent.reference}")
          .also { telemetryClient.trackEvent("visit-changed-mapping-failed", telemetryProperties) }

    visitsApiService.getVisit(visitCancelledEvent.reference).run {

      nomisApiService.updateVisit(
        visitCancelledEvent.prisonerId,
        mappingDto.nomisId,
        UpdateVisitDto(
          startDateTime = this.startTimestamp,
          endTime = this.endTimestamp.toLocalTime(),
          visitorPersonIds = this.visitors.map { it.nomisPersonId },
          room = this.visitRoom,
          openClosedStatus = this.visitRestriction,
        )
      )

      telemetryClient.trackEvent(
        "visit-changed-event",
        telemetryProperties.plus(Pair("nomisVisitId", mappingDto.nomisId))
      )
    }
  }

  private fun getNomisOutcomeOrDefault(vsipVisit: VisitDto): String =
    vsipVisit.outcomeStatus?.runCatching {
      VsipOutcomeStatus.valueOf(this)
    }?.getOrNull()?.let {
      vsipToNomisOutcomeMap[it]?.name
    } ?: NomisCancellationOutcome.ADMIN.name
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
    val reference: String
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
  BATCH_CANC,
  ADMIN_CANCEL,
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
  VsipOutcomeStatus.BATCH_CANCELLATION to NomisCancellationOutcome.BATCH_CANC,
  VsipOutcomeStatus.ADMINISTRATIVE_CANCELLATION to NomisCancellationOutcome.ADMIN_CANCEL,
  VsipOutcomeStatus.PRISONER_REFUSED_TO_ATTEND to NomisCancellationOutcome.REFUSED,
)
