package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable

@Service
class CorePersonRetryService(
  private val corePersonSynchronisationService: CorePersonSynchronisationService,
) : CreateMappingRetryable {
  override suspend fun retryCreateMapping(message: String) {
    corePersonSynchronisationService.retryCreateMapping(message)
  }
}
