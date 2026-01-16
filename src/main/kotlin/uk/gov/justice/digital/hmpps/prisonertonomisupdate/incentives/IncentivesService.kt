package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives.model.IncentiveReviewDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.IncentiveMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.IncentiveMappingDto.MappingType.INCENTIVE_CREATED
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateIncentiveRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise
import java.time.format.DateTimeFormatter

private const val INCENTIVE_CREATED_IN_NOMS_BY_USER = "USER_CREATED_NOMIS"

@Service
class IncentivesService(
  private val incentivesApiService: IncentivesApiService,
  private val nomisApiService: NomisApiService,
  private val mappingService: IncentivesMappingService,
  private val incentivesUpdateQueueService: IncentivesUpdateQueueService,
  private val telemetryClient: TelemetryClient,
  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {

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
        mappingService.getMappingGivenIncentiveIdOrNull(event.additionalInformation.id)
      }
      transform {
        incentivesApiService.getIncentive(event.additionalInformation.id)
          .takeUnless { event.additionalInformation.reason == INCENTIVE_CREATED_IN_NOMS_BY_USER }?.let { incentive ->
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
                nomisIncentiveSequence = nomisResponse.sequence,
                incentiveId = event.additionalInformation.id,
                mappingType = INCENTIVE_CREATED,
              )
            }
          }
      }
      saveMapping { mappingService.createMapping(it) }
    }
  }

  suspend fun retryCreateIncentiveMapping(context: CreateMappingRetryMessage<IncentiveMapping>) {
    mappingService.createMapping(
      IncentiveMappingDto(
        nomisBookingId = context.mapping.nomisBookingId,
        nomisIncentiveSequence = context.mapping.nomisIncentiveSequence.toLong(),
        incentiveId = context.mapping.incentiveId,
        mappingType = INCENTIVE_CREATED,
      ),
    ).also {
      telemetryClient.trackEvent(
        "incentive-retry-success",
        mapOf("id" to context.mapping.toString()),
        null,
      )
    }
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

  private inline fun <reified T> String.fromJson(): T = objectMapper.readValue(this)
}

fun IncentiveReviewDetail.toNomisIncentive() = CreateIncentiveRequest(
  comments = comments,
  iepDateTime = iepDate.atTime(iepTime.toLocalTime()),
  userId = userId,
  prisonId = agencyId,
  iepLevel = iepCode,
)
