package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjustments

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable

@Service
class AdjudicationsService : CreateMappingRetryable {

  override suspend fun retryCreateMapping(message: String) {}
}
