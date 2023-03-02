package uk.gov.justice.digital.hmpps.prisonertonomisupdate.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient

abstract class SynchronisationService<DPSID, DPSENTITY, NOMISCREATEID, NOMISCREATEENTITY, NOMISCREATERESPONSE, MAPPING>(
  internal val objectMapper: ObjectMapper,
  internal val mappingService: MappingService<DPSID, *>,
  internal val dpsService: DPSService<DPSID, DPSENTITY>,
  internal val telemetryClient: TelemetryClient,
  internal val queueService: UpdateQueueService<MAPPING>,
  val name: String,
) {
  abstract suspend fun retryCreateMapping(message: String)

  internal inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)

  suspend fun create(message: String) {
    val id: DPSID = dpsIdFromCreateEvent(message)
    mappingService.getMappingFromDPSId(id)?.let {
      telemetryClient.trackEvent(
        "$name-create-duplicate",
        createEventToTelemetry(message),
        null
      )
    } ?: let {
      dpsService.getEntityFromDPS(id).let { entity ->
        if (shouldCreate(entity)) {
          mapDPSCreateToNOMISCreate(entity).also {
            createNOMISEntity(mapDPSCreateToNOMISId(entity), it).also { response ->
              val nomisId = mapDPSCreateToNOMISId(entity)
              val mapping = mapping(id, nomisId, response)
              kotlin.runCatching {
                createMapping(mapping)
              }
                .onFailure {
                  queueService.sendMessage(createEventToTelemetry(message), mapping)
                }
                .onSuccess {
                  telemetryClient.trackEvent(
                    "$name-create-success",
                    createEventToTelemetry(message, id, nomisId, response),
                    null
                  )
                }
            }
          }
        } else {
          telemetryClient.trackEvent(
            "$name-create-ignored",
            createEventToTelemetry(message),
            null
          )
        }
      }
    }
  }

  abstract fun createEventToTelemetry(message: String): Map<String, String>
  abstract fun createEventToTelemetry(
    message: String,
    dpsId: DPSID,
    nomisId: NOMISCREATEID,
    nomisCreateResponse: NOMISCREATERESPONSE
  ): Map<String, String>

  abstract fun dpsIdFromCreateEvent(message: String): DPSID
  abstract fun mapDPSCreateToNOMISCreate(entity: DPSENTITY): NOMISCREATEENTITY
  abstract fun mapDPSCreateToNOMISId(entity: DPSENTITY): NOMISCREATEID

  abstract suspend fun createNOMISEntity(nomisCreateId: NOMISCREATEID, entity: NOMISCREATEENTITY): NOMISCREATERESPONSE

  abstract fun mapping(dpsId: DPSID, nomisCreateId: NOMISCREATEID, entity: NOMISCREATERESPONSE): MAPPING

  abstract suspend fun createMapping(mapping: MAPPING)

  internal open fun shouldCreate(entity: DPSENTITY) = true
  suspend fun doRetryCreateMapping(message: String) = retryCreateMapping(message).also {
    val retryMessage: CreateMappingRetryMessage<*> = message.fromJson()
    telemetryClient.trackEvent(
      "sentencing-adjustment-create-success",
      retryMessage.telemetry,
      null
    )
  }
}
