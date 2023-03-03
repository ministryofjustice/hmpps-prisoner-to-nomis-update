package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient

abstract class SynchronisationService(
  internal val objectMapper: ObjectMapper,
  internal val telemetryClient: TelemetryClient,
  private val retryQueueService: RetryQueueService,
) {
  abstract suspend fun retryCreateMapping(message: String)

  internal inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)

  suspend fun tryCreateMapping(
    mapping: Any,
    telemetry: MappingTelemetry,
    createMapping: suspend (mapping: Any) -> Unit,
  ) {
    kotlin.runCatching {
      createMapping(mapping)
    }
      .onFailure {
        retryQueueService.sendMessage(mapping, telemetry.attributes)
      }
      .onSuccess {
        telemetryClient.trackEvent(
          telemetry.name,
          telemetry.attributes,
          null,
        )
      }
  }
}

data class MappingTelemetry(
  val name: String,
  val attributes: Map<String, String>,
)
