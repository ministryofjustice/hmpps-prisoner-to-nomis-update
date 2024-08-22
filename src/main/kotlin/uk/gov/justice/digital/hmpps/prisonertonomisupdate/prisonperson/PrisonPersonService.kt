package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent

@Service
class PrisonPersonService(
  private val dpsApi: PrisonPersonDpsApiService,
  private val nomisApi: PrisonPersonNomisApiService,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun updatePhysicalAttributesEvent(event: PhysicalAttributesDomainEvent) {
    if (event.additionalInformation.source == "DPS") {
      updatePhysicalAttributes(event.additionalInformation.prisonerNumber)
    } else {
      telemetryClient.trackEvent(
        "physical-attributes-update-ignored",
        mutableMapOf("offenderNo" to event.additionalInformation.prisonerNumber),
      )
    }
  }

  suspend fun updatePhysicalAttributes(offenderNo: String) {
    val telemetry = mutableMapOf("offenderNo" to offenderNo)

    runCatching {
      dpsApi.getPhysicalAttributes(offenderNo)!!
        .let { nomisApi.upsertPhysicalAttributes(offenderNo, it.height?.value, it.weight?.value) }
        .also {
          telemetry["bookingId"] = it.bookingId.toString()
          telemetry["created"] = it.created.toString()
        }
    }.onSuccess {
      telemetryClient.trackEvent("physical-attributes-update-success", telemetry)
    }.onFailure { e ->
      telemetry["reason"] = e.message ?: "Unknown error"
      telemetryClient.trackEvent("physical-attributes-update-failed", telemetry)
      throw e
    }
  }
}

data class PhysicalAttributesDomainEvent(
  val eventType: String,
  val version: String,
  val description: String,
  val additionalInformation: PhysicalAttributesAdditionalInformation,
)

data class PhysicalAttributesAdditionalInformation(
  val source: String,
  val prisonerNumber: String,
)
