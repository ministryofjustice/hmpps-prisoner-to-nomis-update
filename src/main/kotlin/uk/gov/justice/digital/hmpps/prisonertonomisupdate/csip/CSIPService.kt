package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable

@Service
class CSIPService : CreateMappingRetryable {

  override suspend fun retryCreateMapping(message: String) {
    TODO("Not yet implemented")
  }
}
