package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable

@Suppress("unused")
@Service
class ContactPersonService(
  private val mappingApiService: ContactPersonMappingApiService,
  private val nomisApiService: ContactPersonNomisApiService,
  private val dpsApiService: ContactPersonDpsApiService,
  private val telemetryClient: TelemetryClient,

) : CreateMappingRetryable {
  override suspend fun retryCreateMapping(message: String) {
    TODO("Not yet implemented")
  }

  suspend fun contactCreated(event: ContactCreatedEvent) {
    TODO("Not yet implemented")
  }
}
