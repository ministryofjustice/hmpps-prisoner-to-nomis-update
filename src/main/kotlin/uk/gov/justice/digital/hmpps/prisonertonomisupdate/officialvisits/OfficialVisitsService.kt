package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable

@Service
class OfficialVisitsService : CreateMappingRetryable {
  override suspend fun retryCreateMapping(message: String) {
    TODO("Not yet implemented")
  }
}
