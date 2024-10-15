package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CSIPChildMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CSIPFullMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CSIPComponent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertCSIPResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryMessage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreateMappingRetryable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise

@Service
class CSIPService(
  private val telemetryClient: TelemetryClient,
  private val nomisApiService: CSIPNomisApiService,
  private val dpsApiService: CSIPDpsApiService,
  private val mappingApiService: CSIPMappingApiService,
  private val csipRetryQueueService: CSIPRetryQueueService,

  private val objectMapper: ObjectMapper,
) : CreateMappingRetryable {
  private companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun createCSIPReport(csipEvent: CSIPEvent) {
    val dpsCsipReportId = csipEvent.additionalInformation.recordUuid
    val offenderNo = requireNotNull(csipEvent.personReference.findNomsNumber())
    val telemetryMap = mutableMapOf(
      "dpsCSIPReportId" to dpsCsipReportId,
      "offenderNo" to offenderNo,
    )

    synchronise {
      name = "csip"
      telemetryClient = this@CSIPService.telemetryClient
      retryQueueService = csipRetryQueueService
      eventTelemetry = telemetryMap

      checkMappingDoesNotExist {
        mappingApiService.getOrNullByDpsId(dpsCsipReportId)
      }
      transform {
        dpsApiService.getCsipReport(dpsCsipReportId)
          .let { dpsCsipRecord ->
            nomisApiService.upsertCsipReport(dpsCsipRecord.toNomisUpsertRequest())
              .let {
                it.createNewMappingDto(dpsCsipReportId)
              }
          }
      }
      saveMapping { mappingApiService.createMapping(it) }
    }
  }

  fun UpsertCSIPResponse.createNewMappingDto(dpsCsipReportId: String) =
    CSIPFullMappingDto(
      dpsCSIPReportId = dpsCsipReportId,
      nomisCSIPReportId = nomisCSIPReportId,
      mappingType = CSIPFullMappingDto.MappingType.DPS_CREATED,
      attendeeMappings = filterMappingsByType(dpsCsipReportId, CSIPComponent.Component.ATTENDEE),
      factorMappings = filterMappingsByType(dpsCsipReportId, CSIPComponent.Component.FACTOR),
      interviewMappings = filterMappingsByType(dpsCsipReportId, CSIPComponent.Component.INTERVIEW),
      planMappings = filterMappingsByType(dpsCsipReportId, CSIPComponent.Component.PLAN),
      reviewMappings = filterMappingsByType(dpsCsipReportId, CSIPComponent.Component.REVIEW),
    )

  fun UpsertCSIPResponse.filterMappingsByType(dpsCSIPReportId: String, childType: CSIPComponent.Component) =
    components.filter { it.component == childType }
      .map {
        CSIPChildMappingDto(
          dpsCSIPReportId = dpsCSIPReportId,
          nomisId = it.nomisId,
          dpsId = it.dpsId,
          mappingType = CSIPChildMappingDto.MappingType.DPS_CREATED,
        )
      }

  suspend fun updateCSIPReport(csipEvent: CSIPEvent) {
    val dpsCsipReportId = csipEvent.additionalInformation.recordUuid
    val offenderNo = requireNotNull(csipEvent.personReference.findNomsNumber())
    val telemetryMap = mutableMapOf(
      "dpsCSIPReportId" to dpsCsipReportId,
      "offenderNo" to offenderNo,
    )

    runCatching {
      val mapping = mappingApiService.getOrNullByDpsId(dpsCsipReportId)
        ?: throw IllegalStateException("Tried to update an csip that has never been created")
      telemetryMap["nomisCSIPReportId"] = mapping.nomisCSIPReportId.toString()

      val dpsCsipRecord = dpsApiService.getCsipReport(dpsCsipReportId)
      nomisApiService.upsertCsipReport(dpsCsipRecord.toNomisUpsertRequest(mapping))
        .also {
          if (it.components.isNotEmpty()) {
            it.createNewMappingDto(dpsCsipReportId)
          }
        }
      // TODO send child mappings just created !!
      // mapping.createChildMappings()
      telemetryClient.trackEvent("csip-updated-success", telemetryMap)
    }.onFailure { e ->
      telemetryClient.trackEvent("csip-updated-failed", telemetryMap)
      throw e
    }
  }

  suspend fun createMapping(message: CreateMappingRetryMessage<CSIPFullMappingDto>) =
    mappingApiService.createMapping(message.mapping).also {
      telemetryClient.trackEvent(
        "csip-create-success",
        message.telemetryAttributes,
        null,
      )
    }

  override suspend fun retryCreateMapping(message: String) = createMapping(message.fromJson())

  suspend fun deleteCsipReport(csipEvent: CSIPEvent) {
    val dpsCsipReportId = csipEvent.additionalInformation.recordUuid
    val offenderNo = requireNotNull(csipEvent.personReference.findNomsNumber())
    val telemetryMap = mutableMapOf(
      "dpsCSIPReportId" to dpsCsipReportId,
      "offenderNo" to offenderNo,
    )

    runCatching {
      mappingApiService.getOrNullByDpsId(dpsCsipReportId)?.also { mapping ->

        nomisApiService.deleteCsipReport(csipReportId = mapping.nomisCSIPReportId)
        tryToDeletedMapping(dpsCsipReportId)
        telemetryClient.trackEvent("csip-deleted-success", telemetryMap)
      } ?: also {
        telemetryClient.trackEvent("csip-deleted-skipped", telemetryMap)
      }
    }.onFailure { e ->
      telemetryClient.trackEvent("csip-deleted-failed", telemetryMap)
      throw e
    }
  }

  private suspend fun tryToDeletedMapping(dpsCsipId: String) = kotlin.runCatching {
    mappingApiService.deleteByDpsId(dpsCsipId)
  }.onFailure { e ->
    telemetryClient.trackEvent("csip-mapping-deleted-failed", mapOf("dpsCSIPReportId" to dpsCsipId))
    log.warn("Unable to delete mapping for csip $dpsCsipId. Please delete manually", e)
  }

  private inline fun <reified T> String.fromJson(): T =
    objectMapper.readValue(this)
}

data class CSIPEvent(
  val description: String?,
  val eventType: String,
  val personReference: PersonReference,
  val additionalInformation: CSIPAdditionalInformation,
)

data class CSIPAdditionalInformation(
  val recordUuid: String,
)

data class PersonReference(val identifiers: List<Identifier> = listOf()) {
  operator fun get(key: String) = identifiers.find { it.type == key }?.value
  fun findNomsNumber() = get(NOMS_NUMBER_TYPE)

  companion object {
    const val NOMS_NUMBER_TYPE = "NOMS"
    fun withNomsNumber(prisonNumber: String) = PersonReference(listOf(Identifier(NOMS_NUMBER_TYPE, prisonNumber)))
  }

  data class Identifier(val type: String, val value: String)
}
