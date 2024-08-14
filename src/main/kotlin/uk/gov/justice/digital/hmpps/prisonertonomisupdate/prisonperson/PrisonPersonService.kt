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
  suspend fun updatePhysicalAttributes(offenderNo: String) {
    val telemetry = mutableMapOf("offenderNo" to offenderNo)

    runCatching {
      dpsApi.getPrisonPerson(offenderNo).physicalAttributes
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
