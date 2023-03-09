package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateIncentiveDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.format.DateTimeFormatter

private const val incentiveCreatedInNomsByUser = "USER_CREATED_NOMIS"

@Service
class IncentivesService(
  private val incentivesApiService: IncentivesApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: IncentivesMappingService,
  private val incentivesUpdateQueueService: IncentivesUpdateQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {

  private fun IepDetail.toNomisIncentive(): CreateIncentiveDto = CreateIncentiveDto(
    comments = comments,
    iepDateTime = iepDate.atTime(iepTime.toLocalTime()),
    userId = userId,
    prisonId = agencyId,
    iepLevel = iepCode,
  )

  suspend fun createIncentive(event: IncentiveCreatedEvent) {
    synchronise {
      name = "incentive"
      telemetryClient = this@IncentivesService.telemetryClient
      retryQueueService = incentivesUpdateQueueService
      eventTelemetry = mapOf(
        "id" to event.additionalInformation.id.toString(),
        "offenderNo" to (event.additionalInformation.nomsNumber ?: "unknown"),
      )

      checkMappingDoesNotExist {
        mappingService.getMappingGivenIncentiveId(event.additionalInformation.id)
      }
      transform {
        incentivesApiService.getIncentive(event.additionalInformation.id)
          .takeUnless { event.additionalInformation.reason == incentiveCreatedInNomsByUser }?.let { incentive ->
            eventTelemetry += mapOf(
              "prisonId" to incentive.agencyId,
              "iep" to incentive.iepCode,
              "iepDate" to incentive.iepDate.format(DateTimeFormatter.ISO_DATE),
              "iepTime" to incentive.iepTime.format(DateTimeFormatter.ISO_TIME),
            )
            nomisApiService.createIncentive(
              incentive.bookingId,
              incentive.toNomisIncentive(),
            ).let { nomisResponse ->
              IncentiveMappingDto(
                nomisBookingId = nomisResponse.bookingId,
                nomisIncentiveSequence = nomisResponse.sequence.toInt(),
                incentiveId = event.additionalInformation.id,
                mappingType = "INCENTIVE_CREATED",
              )
            }
          }
      }
      saveMapping { mappingService.createMapping(it) }
    }
  }

  fun retryCreateIncentiveMapping(context: CreateMappingRetryMessage<IncentiveMapping>) {
    mappingService.createMapping(
      IncentiveMappingDto(
        nomisBookingId = context.mapping.nomisBookingId,
        nomisIncentiveSequence = context.mapping.nomisIncentiveSequence,
        incentiveId = context.mapping.incentiveId,
        mappingType = "INCENTIVE_CREATED",
      ),
    )
  }

  override suspend fun retryCreateMapping(message: String) = retryCreateIncentiveMapping(message.fromJson())

  data class AdditionalInformation(
    val id: Long,
    val nomsNumber: String? = null,
    val reason: String? = null,
  )

  data class IncentiveCreatedEvent(
    val additionalInformation: AdditionalInformation,
  )

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}
