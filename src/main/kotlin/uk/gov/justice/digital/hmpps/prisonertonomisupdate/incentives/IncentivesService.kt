package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateIncentiveDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import java.time.format.DateTimeFormatter

private const val incentiveCreatedInNomsByUser = "USER_CREATED_NOMIS"

@Service
class IncentivesService(
  private val incentivesApiService: IncentivesApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: IncentivesMappingService,
  private val incentivesUpdateQueueService: IncentivesUpdateQueueService,
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

      // to protect against repeated create messages for same incentive
      if (mappingService.getMappingGivenIncentiveId(event.additionalInformation.id) != null) {
        log.warn("Mapping already exists for incentive id $event.additionalInformation.id")
        return
      }

      val nomisResponse = try {
        nomisApiService.createIncentive(this.bookingId, this.toNomisIncentive())
      } catch (e: Exception) {
        telemetryClient.trackEvent("incentive-create-failed", telemetryMap)
        log.error("createIncentive() Unexpected exception", e)
        throw e
      }

      telemetryMap["nomisBookingId"] = nomisResponse.bookingId.toString()
      telemetryMap["nomisIncentiveSequence"] = nomisResponse.sequence.toString()

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
        telemetryClient.trackEvent("incentive-create-map-failed", telemetryMap)
        log.error("Unexpected exception, queueing retry", e)
        incentivesUpdateQueueService.sendMessage(
          IncentiveContext(
            nomisBookingId = nomisResponse.bookingId,
            nomisIncentiveSequence = nomisResponse.sequence.toInt(),
            incentiveId = event.additionalInformation.id
          )
        )
        return
      }

      telemetryClient.trackEvent("incentive-created-event", telemetryMap)
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
