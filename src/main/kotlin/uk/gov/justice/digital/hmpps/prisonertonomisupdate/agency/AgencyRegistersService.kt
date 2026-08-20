package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service

@Service
class AgencyRegistersService(
  private val telemetryClient: TelemetryClient,
) {

  suspend fun agencyUpdated(event: AgencyUpdatedEvent) {
    // TODO implement synchronisation of the agency register once mappings/requirements are defined
  }
}

data class AgencyUpdatedEvent(
  val eventType: String,
  val additionalInformation: AgencyUpdatedAdditionalInformation,
)

data class AgencyUpdatedAdditionalInformation(
  val agencyId: String,
)
