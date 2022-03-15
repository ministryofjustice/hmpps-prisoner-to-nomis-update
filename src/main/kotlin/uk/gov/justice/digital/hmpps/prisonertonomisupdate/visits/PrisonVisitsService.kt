package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CancelVisitDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateVisitDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.MappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.MappingService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpdateQueueService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.VisitContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.validation.ValidationException

@Service
class PrisonVisitsService(
  private val visitsApiService: VisitsApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: MappingService,
  private val updateQueueService: UpdateQueueService,
  private val telemetryClient: TelemetryClient
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  fun createVisit(visitBookedEvent: VisitBookedEvent) {
    visitsApiService.getVisit(visitBookedEvent.visitId).run {

      val telemetryMap = mutableMapOf(
        "offenderNo" to prisonerId,
        "prisonId" to prisonId,
        "visitId" to visitId,
        "startDateTime" to LocalDateTime.of(visitDate, startTime).format(DateTimeFormatter.ISO_DATE_TIME),
        "endTime" to endTime.format(DateTimeFormatter.ISO_TIME),
      )

      if (mappingService.getMappingGivenVsipId(visitBookedEvent.visitId) != null) {
        telemetryClient.trackEvent("visit-booked-get-map-failed", telemetryMap)
        log.warn("Mapping already exists for VSIP id $visitId")
        return
      }

      val nomisId = try {
        nomisApiService.createVisit(
          CreateVisitDto(
            offenderNo = this.prisonerId,
            prisonId = this.prisonId,
            startDateTime = LocalDateTime.of(this.visitDate, this.startTime),
            endTime = this.endTime,
            visitorPersonIds = this.visitors.map { it -> it.nomisPersonId },
            issueDate = visitBookedEvent.bookingDate,
            visitType = "SCON", // TODO mapping
          )
        )
      } catch (e: Exception) {
        telemetryClient.trackEvent("visit-booked-create-failed", telemetryMap)
        log.error("Unexpected exception", e)
        throw e
      }

      val mapWithNomisId = telemetryMap.plus(Pair("nomisVisitId", nomisId))

      try {
        mappingService.createMapping(
          MappingDto(nomisId = nomisId, vsipId = visitBookedEvent.visitId, mappingType = "ONLINE")
        )
      } catch (e: Exception) {
        telemetryClient.trackEvent("visit-booked-create-map-failed", mapWithNomisId)
        log.error("Unexpected exception, queueing retry", e)
        updateQueueService.sendMessage(VisitContext(nomisId = nomisId, vsipId = visitBookedEvent.visitId), 1)
        return
      }

      telemetryClient.trackEvent("visit-booked-event", mapWithNomisId)
    }
  }

  fun createVisitRetry(context: VisitContext) {
    mappingService.createMapping(
      MappingDto(nomisId = context.nomisId, vsipId = context.vsipId, mappingType = "ONLINE")
    )
  }

  fun cancelVisit(visitCancelledEvent: VisitCancelledEvent) {
    val telemetryProperties = mutableMapOf(
      "offenderNo" to visitCancelledEvent.prisonerId,
      "visitId" to visitCancelledEvent.visitId,
    )

    val mappingDto =
      mappingService.getMappingGivenVsipId(visitCancelledEvent.visitId)
        ?: throw ValidationException("No mapping exists for VSIP id ${visitCancelledEvent.visitId}")
          .also { telemetryClient.trackEvent("visit-cancelled-mapping-failed", telemetryProperties) }

    val mapWithNomisId = telemetryProperties.plus(Pair("nomisVisitId", mappingDto.nomisId))

    try {
      nomisApiService.cancelVisit(
        CancelVisitDto(
          offenderNo = visitCancelledEvent.prisonerId,
          nomisVisitId = mappingDto.nomisId,
          outcome = "VISCANC" // TODO mapping
        )
      )
    } catch (e: Exception) {
      telemetryClient.trackEvent("visit-cancelled-failed", mapWithNomisId)
      log.error("Unexpected exception", e)
      throw e
    }

    telemetryClient.trackEvent("visit-cancelled-event", mapWithNomisId)
  }
}

data class VisitBookedEvent(
  val additionalInformation: VisitInformation,
  val occurredAt: OffsetDateTime,
  val prisonerId: String,
) {
  val bookingDate: LocalDate
    get() = occurredAt.toLocalDate()

  val visitId: String
    get() = additionalInformation.visitId

  data class VisitInformation(
    val visitId: String
  )
}

data class VisitCancelledEvent(
  val additionalInformation: VisitInformation,
  val occurredAt: OffsetDateTime,
  val prisonerId: String,
) {

  val visitId: String
    get() = additionalInformation.visitId

  data class VisitInformation(
    val visitId: String,
  )
}
