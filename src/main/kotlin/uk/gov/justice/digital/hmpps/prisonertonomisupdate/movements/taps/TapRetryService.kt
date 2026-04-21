package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps

import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps.TapRetryService.Companion.MappingTypes.APPLICATION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps.TapRetryService.Companion.MappingTypes.SCHEDULE_CREATE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps.TapRetryService.Companion.MappingTypes.SCHEDULE_UPDATE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable

@Service
class TapRetryService(
  private val jsonMapper: JsonMapper,
  private val tapAuthorisationService: TapAuthorisationService,
  private val tapOccurrenceService: TapOccurrenceService,
) : CreateMappingRetryable {
  companion object {
    enum class MappingTypes(val entityName: String) {
      APPLICATION("temporary-absence-application"),
      SCHEDULE_CREATE("temporary-absence-schedule-create"),
      SCHEDULE_UPDATE("temporary-absence-schedule-update"),
      ;

      companion object {
        fun fromEntityName(entityName: String) = entries.find { it.entityName == entityName }
          ?: throw IllegalStateException("Mapping type $entityName does not exist")
      }
    }
  }

  override suspend fun retryCreateMapping(message: String) {
    val baseMapping: CreateMappingRetryMessage<*> = message.fromJson()
    when (MappingTypes.fromEntityName(baseMapping.entityName)) {
      APPLICATION -> tapAuthorisationService.createApplicationMapping(message.fromJson())
      SCHEDULE_CREATE -> tapOccurrenceService.createScheduledMovementMapping(message.fromJson())
      SCHEDULE_UPDATE -> tapOccurrenceService.updateScheduledMovementMapping(message.fromJson())
    }
  }

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}
