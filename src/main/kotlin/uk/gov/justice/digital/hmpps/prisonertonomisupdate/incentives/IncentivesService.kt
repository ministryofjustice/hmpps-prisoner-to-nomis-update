package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateIncentiveDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.IncentiveContext
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.UpdateQueueService
import java.time.format.DateTimeFormatter

private const val incentiveCreatedInNomsByUser = "USER_CREATED_NOMIS"

@Service
class IncentivesService(
  private val incentivesApiService: IncentivesApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: IncentivesMappingService,
  private val updateQueueService: UpdateQueueService,
  private val telemetryClient: TelemetryClient
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  private fun IepDetail.toNomisIncentive(): CreateIncentiveDto = CreateIncentiveDto(
    comments = comments,
    iepDateTime = iepDate.atTime(iepTime.toLocalTime()),
    userId = userId,
    prisonId = agencyId,
    iepLevel = iepCode,
  )

  fun createIncentive(event: IncentiveCreatedEvent) {
    incentivesApiService.getIncentive(event.additionalInformation.id).run {

      val telemetryMap = mutableMapOf<String, String>(
        "offenderNo" to prisonerNumber!!,
        "prisonId" to agencyId,
        "id" to id.toString(),
        "iep" to iepCode,
        "iepDate" to iepDate.format(DateTimeFormatter.ISO_DATE),
        "iepTime" to iepTime.format(DateTimeFormatter.ISO_TIME),
      )

      if (event.additionalInformation.reason == incentiveCreatedInNomsByUser) {
        log.debug("Incentive id $event.additionalInformation.id created in NOMIS so ignoring")
        return
      }

      val nomisResponse = try {
        nomisApiService.createIncentive(this.bookingId, this.toNomisIncentive())
      } catch (e: Exception) {
        telemetryClient.trackEvent("incentive-create-failed", telemetryMap)
        log.error("createIncentive() Unexpected exception", e)
        throw e
      }

      val mapWithNomisId = telemetryMap
        .plus(Pair("nomisBookingId", nomisResponse.bookingId.toString()))
        .plus(Pair("nomisIncentiveSequence", nomisResponse.sequence.toString()))

      try {
        mappingService.createMapping(
          IncentiveMappingDto(
            nomisBookingId = nomisResponse.bookingId,
            nomisIncentiveSequence = nomisResponse.sequence.toInt(),
            incentiveId = event.additionalInformation.id,
            mappingType = "INCENTIVE_CREATED",
          )
        )
      } catch (e: Exception) {
        telemetryClient.trackEvent("incentive-create-map-failed", mapWithNomisId)
        log.error("Unexpected exception, queueing retry", e)
        updateQueueService.sendMessage(
          IncentiveContext(
            nomisBookingId = nomisResponse.bookingId,
            nomisIncentiveSequence = nomisResponse.sequence.toInt(),
            incentiveId = event.additionalInformation.id
          )
        )
        return
      }

      telemetryClient.trackEvent("incentive-created-event", mapWithNomisId)
    }
  }

  fun createIncentiveRetry(context: IncentiveContext) {
    mappingService.createMapping(
      IncentiveMappingDto(
        nomisBookingId = context.nomisBookingId,
        nomisIncentiveSequence = context.nomisIncentiveSequence,
        incentiveId = context.incentiveId,
        mappingType = "INCENTIVE_CREATED",
      )
    )
  }

  data class AdditionalInformation(
    val id: Long,
    val nomsNumber: String? = null,
    val reason: String? = null,
  )

  data class IncentiveCreatedEvent(
    val additionalInformation: AdditionalInformation,
  )
}
