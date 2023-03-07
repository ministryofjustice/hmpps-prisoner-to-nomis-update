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
