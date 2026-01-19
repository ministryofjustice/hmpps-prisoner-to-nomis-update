package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Deferred
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.DuplicateErrorContent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.DuplicateMappingException
import kotlin.reflect.full.memberProperties

class SynchroniseBuilder<MAPPING_DTO>(
  private var transform: suspend () -> MAPPING_DTO? = { null },
  private var fetchMapping: suspend () -> Any? = { null },
  private var postMapping: suspend (MAPPING_DTO) -> Unit = {},
  var name: String = "unknown",
  var eventTelemetry: Map<String, String> = mapOf(),
  var telemetryClient: TelemetryClient? = null,
  var retryQueueService: RetryQueueService? = null,
) {

  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun process() {
    fetchMapping()?.let {
      telemetryClient?.trackEvent(
        "$name-create-duplicate",
        eventTelemetry,
        null,
      )
    } ?: let {
      runCatching {
        transform()
      }.onFailure {
        telemetryClient?.trackEvent(
          "$name-create-failed",
          eventTelemetry,
          null,
        )
        throw it
      }.onSuccess {
        it.also { mapping ->
          mapping?.run {
            createMapping(
              mapping = mapping,
              telemetryClient = telemetryClient,
              postMapping = postMapping,
              eventTelemetry = eventTelemetry,
              retryQueueService = retryQueueService,
              name = name,
              log = log,
            ).onSuccess {
              telemetryClient?.trackEvent(
                "$name-create-success",
                eventTelemetry + mapping.asMap(),
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
  }

  fun checkMappingDoesNotExist(fetchMapping: suspend () -> Any? = { false }): SynchroniseBuilder<MAPPING_DTO> {
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

suspend fun <MAPPING_DTO> createMapping(
  mapping: MAPPING_DTO & Any,
  telemetryClient: TelemetryClient?,
  postMapping: suspend (MAPPING_DTO) -> Unit,
  eventTelemetry: Map<String, String>,
  retryQueueService: RetryQueueService?,
  name: String,
  log: Logger,
): Result<Unit> {
  val telemetryAttributes = eventTelemetry + mapping.asMap()
  return runCatching {
    postMapping(mapping)
  }
    .onFailure { e ->
      telemetryClient?.trackEvent(
        "$name-mapping-create-failed",
        mutableMapOf("reason" to (e.message ?: "Unknown error")) + telemetryAttributes,
        null,
      )
      when (e) {
        is DuplicateMappingException -> {
          telemetryClient?.trackEvent(
            "to-nomis-synch-$name-duplicate",
            telemetryAttributes + e.error.moreInfo.asTelemetry(),
            null,
          )
        }

        else -> {
          retryQueueService?.sendMessage(mapping, telemetryAttributes, name)
          log.error("Failed to create $name mapping", e)
        }
      }
    }
}

private fun DuplicateErrorContent.asTelemetry(): Map<String, String> = this.duplicate.entries.associate { it.asPrefixTelemetry("duplicate") } +
  (this.existing?.entries?.associate { it.asPrefixTelemetry("existing") } ?: emptyMap())

private fun Map.Entry<String, *>.asPrefixTelemetry(prefix: String) = "$prefix${key.replaceFirstChar { it.titlecase() }}" to value.toString()

suspend fun <MAPPING_DTO> synchronise(init: SynchroniseBuilder<MAPPING_DTO>.() -> Unit): SynchroniseBuilder<MAPPING_DTO> {
  val builder = SynchroniseBuilder<MAPPING_DTO>()
  builder.init()
  return builder.also { it.process() }
}

fun Any.asMap(): Map<String, String> = this::class.memberProperties
  .filter { it.getter.call(this) != null }
  .associate { it.name to it.getter.call(this).toString() }

suspend fun <A, B> Pair<Deferred<A>, Deferred<B>>.awaitBoth(): Pair<A, B> = this.first.await() to this.second.await()

fun Long.asPages(pageSize: Long): Array<Pair<Long, Long>> = (0..(this / pageSize)).map { it to pageSize }.toTypedArray()
fun Int.asPages(pageSize: Int): Array<Pair<Int, Int>> = (0..(this / pageSize)).map { it to pageSize }.toTypedArray()

enum class CreatingSystem {
  NOMIS,
  DPS,
}
