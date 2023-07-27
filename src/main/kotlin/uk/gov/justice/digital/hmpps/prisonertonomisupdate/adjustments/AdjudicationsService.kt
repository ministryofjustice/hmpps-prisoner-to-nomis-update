package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjustments

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise

@Service
class AdjudicationsService(
  private val telemetryClient: TelemetryClient,
  private val adjudicationRetryQueueService: AdjudicationsRetryQueueService,
  private val adjudicationsApiService: AdjudicationsApiService,
) : CreateMappingRetryable {
  suspend fun createAdjudication(createEvent: AdjudicationCreatedEvent) {
    val chargeNumber = createEvent.additionalInformation.chargeNumber
    val prisonId: String = createEvent.additionalInformation.prisonId
    val telemetryMap = mutableMapOf(
      "chargeNumber" to chargeNumber,
      "prisonId" to prisonId,
    )
    synchronise {
      name = "adjudication"
      telemetryClient = this@AdjudicationsService.telemetryClient
      retryQueueService = adjudicationRetryQueueService
      eventTelemetry = telemetryMap

      checkMappingDoesNotExist {
        null
      }
      transform {
        val adjudication = adjudicationsApiService.getCharge(chargeNumber, prisonId)
        telemetryMap["offenderNo"] = adjudication.prisonerNumber

        "TODO return mapping dto"
      }
      saveMapping { }
    }
  }

  override suspend fun retryCreateMapping(message: String) {}
}

data class AdjudicationAdditionalInformation(
  val chargeNumber: String,
  val prisonId: String,
)

data class AdjudicationCreatedEvent(
  val additionalInformation: AdjudicationAdditionalInformation,
)
