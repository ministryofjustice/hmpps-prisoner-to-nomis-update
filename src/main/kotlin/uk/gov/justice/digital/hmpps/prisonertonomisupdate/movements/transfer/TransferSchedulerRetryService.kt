package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer

import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerRetryService.Companion.MappingTypes.SCHEDULE
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable

@Service
class TransferSchedulerRetryService(
  private val jsonMapper: JsonMapper,
  private val scheduleService: TransferSchedulerScheduleService,
) : CreateMappingRetryable {
  companion object {
    enum class MappingTypes(val entityName: String) {
      SCHEDULE("transfer-scheduler-schedule"),
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
      SCHEDULE -> scheduleService.createTransferScheduleMapping(message.fromJson())
    }
  }

  private inline fun <reified T> String.fromJson(): T = jsonMapper.readValue(this)
}
