package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson

import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.contact.CorePersonContactService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion.ReligionService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable

@Service
class CorePersonRetryService(
  private val jsonMapper: JsonMapper,
  private val religionService: ReligionService,
  private val corePersonContactService: CorePersonContactService,
) : CreateMappingRetryable {
  private val religionEntityNames = ReligionService.Companion.MappingTypes.entries.map { it.entityName }
  private val contactEntityNames = CorePersonContactService.Companion.MappingTypes.entries.map { it.entityName }

  override suspend fun retryCreateMapping(message: String) {
    val baseMapping: CreateMappingRetryMessage<*> = jsonMapper.readValue(message)
    when (baseMapping.entityName) {
      in religionEntityNames -> religionService.retryCreateMapping(message)
      in contactEntityNames -> corePersonContactService.retryCreateMapping(message)
      else -> throw IllegalStateException("Mapping type ${baseMapping.entityName} does not exist")
    }
  }
}
