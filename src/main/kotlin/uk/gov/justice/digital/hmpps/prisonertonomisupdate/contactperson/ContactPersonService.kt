package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable

@Service
class ContactPersonService : CreateMappingRetryable {
  override suspend fun retryCreateMapping(message: String) {
    TODO("Not yet implemented")
  }
}
