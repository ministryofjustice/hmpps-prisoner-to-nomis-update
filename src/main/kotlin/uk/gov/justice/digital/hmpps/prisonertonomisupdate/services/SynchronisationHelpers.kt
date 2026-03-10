package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.reactive.awaitFirst
import org.openapitools.client.infrastructure.ApiClient
import org.openapitools.client.infrastructure.RequestConfig
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.ParameterizedTypeReference
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.DuplicateErrorContent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.DuplicateMappingException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ParentEntityNotFoundRetry
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
        when (it) {
          is AwaitParentEntityRetry -> {
            telemetryClient?.let { client ->
              it.message?.let { message -> eventTelemetry = eventTelemetry + ("reason" to message) }
              client.trackEvent("$name-create-awaiting-parent", eventTelemetry)
            }
          }

          else -> {
            telemetryClient?.trackEvent("$name-create-failed", eventTelemetry)
          }
        }
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

interface TelemetryEnabled {
  val telemetryClient: TelemetryClient
}

class AwaitParentEntityRetry(message: String) : ParentEntityNotFoundRetry(message)

inline fun TelemetryEnabled.track(name: String, telemetry: MutableMap<String, String>, transform: () -> Unit) {
  try {
    transform()
    telemetryClient.trackEvent("$name-success", telemetry)
  } catch (e: AwaitParentEntityRetry) {
    telemetry["reason"] = e.message.toString()
    telemetryClient.trackEvent("$name-awaiting-parent", telemetry)
    throw e
  } catch (e: Exception) {
    telemetry["error"] = e.message.toString()
    telemetryClient.trackEvent("$name-error", telemetry)
    throw e
  }
}

fun TelemetryClient.trackEvent(name: String, properties: Map<String, Any>) = this.trackEvent(
  name,
  properties.valuesAsStrings(),
  null,
)

fun Map<String, Any>.valuesAsStrings(): Map<String, String> = this.entries.associate { it.key to it.value.toString() }
suspend fun <T> tryFetchParent(message: String = "Expected parent entity not found, retrying", get: suspend () -> T?): T = get() ?: throw AwaitParentEntityRetry(
  message,
)

suspend inline fun <reified T : Any, reified E : Any> WebClient.ResponseSpec.awaitSuccessOrDuplicate(): SuccessOrDuplicate<T, E> = this.bodyToMono<T>()
  .map { SuccessOrDuplicate<T, E>(successResponse = it) }
  .onErrorResume(WebClientResponseException.Conflict::class.java) {
    Mono.just(SuccessOrDuplicate(duplicateResponse = it.getResponseBodyAs(object : ParameterizedTypeReference<DuplicateError<E>>() {})))
  }
  .awaitFirst()

suspend inline fun <reified T : Any, reified E : Any, reified C : Any> ApiClient.awaitSuccessOrDuplicate(requestConfig: RequestConfig<C>): SuccessOrDuplicate<T, E> = this.prepare(requestConfig).retrieve().awaitSuccessOrDuplicate()

data class SuccessOrDuplicate<T, E>(
  val duplicateResponse: DuplicateError<E>? = null,
  val successResponse: T? = null,
) {
  val isDuplicate
    get() = duplicateResponse != null
}

class DuplicateError<E>(
  val moreInfo: E,
  val developerMessage: String? = null,
)
