package uk.gov.justice.digital.hmpps.prisonertonomisupdate.corporate

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable

@Service
class CorporateService : CreateMappingRetryable {
  override suspend fun retryCreateMapping(message: String) {
    TODO("Not yet implemented")
  }
}
