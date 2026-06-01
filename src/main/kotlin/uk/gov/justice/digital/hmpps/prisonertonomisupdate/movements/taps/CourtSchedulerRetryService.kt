package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps

import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable

@Service
class CourtSchedulerRetryService(
  private val jsonMapper: JsonMapper,
) : CreateMappingRetryable {

  override suspend fun retryCreateMapping(message: String) {
    val baseMapping: CreateMappingRetryMessage<*> = message.fromJson()
    // TODO Implement court scheduler mapping retry service
  }

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}
