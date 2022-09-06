package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateIncentiveDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.IncentiveContext
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.IncentiveMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.MappingService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisCodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpdateQueueService
import java.time.format.DateTimeFormatter

@Service
class IncentivesService(
  private val incentivesApiService: IncentivesApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: MappingService,
  private val updateQueueService: UpdateQueueService,
  private val telemetryClient: TelemetryClient
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private fun IepDetail.toNomisIncentive(): CreateIncentiveDto = CreateIncentiveDto(
    bookingId = bookingId,
    incentiveSequence = sequence,
    commentText = comments,
    iepDateTime = iepDate.atTime(iepTime.toLocalTime()),
    userId = userId,
    prisonId = agencyId,
    iepLevel = NomisCodeDescription(code = iepLevel, description = ""),
    currentIep = true,
  )

  fun createIncentive(event: IncentiveCreatedEvent) {
    incentivesApiService.getIncentive(event.incentiveId).run {

      val telemetryMap = mutableMapOf<String, String>(
        "offenderNo" to prisonerNumber!!,
        "prisonId" to agencyId,
        "id" to id.toString(),
        "iepDate" to iepDate.format(DateTimeFormatter.ISO_DATE),
        "iepTime" to iepTime.format(DateTimeFormatter.ISO_TIME),
      )

      if (mappingService.getIncentiveMappingGivenIncentiveId(event.incentiveId) != null) {
        telemetryClient.trackEvent("incentive-get-map-failed", telemetryMap)
        log.warn("Mapping already exists for incentive id $event.id")
        return
      }

      val nomisResponse = try {
        nomisApiService.createIncentive(this.toNomisIncentive())
      } catch (e: Exception) {
        telemetryClient.trackEvent("incentive-create-failed", telemetryMap)
        log.error("Unexpected exception", e)
        throw e
      }

      val mapWithNomisId = telemetryMap
        .plus(Pair("nomisBookingId", nomisResponse.nomisBookingId.toString()))
        .plus(Pair("nomisIncentiveSequence", nomisResponse.nomisIncentiveSequence.toString()))

      try {
        mappingService.createIncentiveMapping(
          IncentiveMappingDto(
            nomisBookingId = nomisResponse.nomisBookingId,
            nomisIncentiveSequence = nomisResponse.nomisIncentiveSequence,
            incentiveId = event.incentiveId,
            mappingType = "INCENTIVE_CREATED",
          )
        )
      } catch (e: Exception) {
        telemetryClient.trackEvent("incentive-create-map-failed", mapWithNomisId)
        log.error("Unexpected exception, queueing retry", e)
        updateQueueService.sendMessage(
          IncentiveContext(
            nomisBookingId = nomisResponse.nomisBookingId,
            nomisIncentiveSequence = nomisResponse.nomisIncentiveSequence,
            incentiveId = event.incentiveId
          )
        )
        return
      }

      telemetryClient.trackEvent("incentive-event", mapWithNomisId)
    }
  }

  fun createIncentiveRetry(context: IncentiveContext) {
    mappingService.createIncentiveMapping(
      IncentiveMappingDto(
        nomisBookingId = context.nomisBookingId,
        nomisIncentiveSequence = context.nomisIncentiveSequence,
        incentiveId = context.incentiveId,
        mappingType = "INCENTIVE_CREATED",
      )
    )
  }

  data class IncentiveCreatedEvent(
    // TBD
    val incentiveId: Long,
  )
}
