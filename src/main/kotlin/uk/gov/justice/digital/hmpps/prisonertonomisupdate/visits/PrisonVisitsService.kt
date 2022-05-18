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
            }
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
          MappingDto(nomisId = nomisId, vsipId = visitBookedEvent.reference, mappingType = "ONLINE")
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
      MappingDto(nomisId = context.nomisId, vsipId = context.vsipId, mappingType = "ONLINE")
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

    nomisApiService.cancelVisit(
      CancelVisitDto(
        offenderNo = visitCancelledEvent.prisonerId,
        nomisVisitId = mappingDto.nomisId,
        outcome = "VISCANC" // TODO mapping
      )
    )

    telemetryClient.trackEvent(
      "visit-cancelled-event",
      telemetryProperties.plus(Pair("nomisVisitId", mappingDto.nomisId))
    )
  }
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
