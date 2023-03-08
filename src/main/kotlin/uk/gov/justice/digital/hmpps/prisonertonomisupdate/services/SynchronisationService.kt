package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import kotlin.reflect.full.memberProperties

@Deprecated("Use SynchroniseBuilder instead")
abstract class SynchronisationService(
  internal val objectMapper: ObjectMapper,
  internal val telemetryClient: TelemetryClient,
  internal val retryQueueService: RetryQueueService,
) : CreateMappingRetryable {
  internal inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)

  suspend fun <T : Any> tryCreateMapping(
    mapping: T,
    telemetry: MappingTelemetry = MappingTelemetry(),
    createMapping: suspend (mapping: T) -> Unit,
  ) {
    kotlin.runCatching {
      createMapping(mapping)
    }
      .onFailure {
        retryQueueService.sendMessage(mapping, telemetry.attributes)
        telemetry.failureName?.run {
          telemetryClient.trackEvent(
            telemetry.failureName,
            telemetry.attributes,
            null,
          )
        }
      }
      .onSuccess {
        telemetry.name.run {
          telemetryClient.trackEvent(
            telemetry.name,
            telemetry.attributes,
            null,
          )
        }
      }
  }
}

data class MappingTelemetry(
  val name: String? = null,
  val failureName: String? = null,
  val attributes: Map<String, String> = mapOf(),
)

class SynchroniseBuilder<MAPPING_DTO>(
  private var transform: suspend () -> MAPPING_DTO? = { null },
  private var fetchMapping: suspend () -> Any? = { null },
  private var postMapping: suspend (MAPPING_DTO) -> Unit = {},
  var name: String = "unknown",
  var eventTelemetry: Map<String, String> = mapOf(),
  var telemetryClient: TelemetryClient? = null,
  var retryQueueService: RetryQueueService? = null,
) {
  suspend fun process() {
    fetchMapping()?.let {
      telemetryClient?.trackEvent(
        "$name-create-duplicate",
        eventTelemetry,
        null,
      )
    } ?: let {
      transform().also { mapping ->
        mapping?.run {
          val telemetryAttributes = eventTelemetry + mapping.asMap()
          kotlin.runCatching {
            postMapping(mapping)
          }
            .onFailure {
              retryQueueService?.sendMessage(mapping, telemetryAttributes)
              telemetryClient?.trackEvent(
                "$name-mapping-create-failed",
                telemetryAttributes,
                null,
              )
            }
            .onSuccess {
              telemetryClient?.trackEvent(
                "$name-create-success",
                telemetryAttributes,
                null,
              )
            }
        } ?: kotlin.run {
          telemetryClient?.trackEvent(
            "$name-create-ignored",
            eventTelemetry,
            null,
          )
        }
      }
    }
  }

  fun checkMappingDoesNotExist(fetchMapping: suspend () -> Any?): SynchroniseBuilder<MAPPING_DTO> {
    this.fetchMapping = fetchMapping
    return this
  }

  infix fun transform(transform: suspend () -> MAPPING_DTO?): SynchroniseBuilder<MAPPING_DTO> {
    this.transform = transform
    return this
  }

  infix fun saveMapping(saveMapping: suspend (MAPPING_DTO) -> Unit) {
    postMapping = saveMapping
  }
}

suspend fun <MAPPING_DTO> synchronise(init: SynchroniseBuilder<MAPPING_DTO>.() -> Unit): SynchroniseBuilder<MAPPING_DTO> {
  val builder = SynchroniseBuilder<MAPPING_DTO>()
  builder.init()
  return builder.also { it.process() }
}

fun Any.asMap(): Map<String, String> {
  return this::class.memberProperties
    .filter { it.getter.call(this) != null }
    .associate { it.name to it.getter.call(this).toString() }
}
