package uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.religion

import com.microsoft.applicationinsights.TelemetryClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.CorePersonCprApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.coreperson.CorePersonNomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.PersonReferenceList
import java.util.*
import kotlin.collections.first
import kotlin.collections.orEmpty

@Service
class ReligionService(
  private val telemetryClient: TelemetryClient,
  val corePersonCprApiService: CorePersonCprApiService,
  val mapping: ReligionMappingApiService,
  val corePersonNomisApiService: CorePersonNomisApiService,
) {
  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }

  suspend fun religionCreated(event: ReligionEvent) {
    val prisonNumber =
      event.personReference.identifiers.first { it.type == "prisonNumber" }.value
    val cprReligionId = event.additionalInformation.cprReligionId
    val telemetryMap = mutableMapOf(
      "prisonNumber" to prisonNumber,
      "cprReligionId" to cprReligionId,
    )
    // TODO create religion in nomis
    telemetryClient.trackEvent("cpr-religion-created-success", telemetryMap)
  }

  suspend fun mergeReligions(toPrisonNumber: String) {
    val telemetryMap = mutableMapOf(
      "prisonNumber" to toPrisonNumber,
    )
    val toPerson = corePersonCprApiService.getCorePerson(toPrisonNumber)
    val cprReligions = toPerson?.religionHistory.orEmpty()
    val cprReligionIds = cprReligions.map { it.cprReligionId!! }
    val mappings = mapping.getByCprIds(cprReligionIds)
    val missingMappings = cprReligionIds.toSet() - mappings.map { it.cprId }.toSet()
    if (missingMappings.isNotEmpty()) {
      throw IllegalStateException("Missing religion mappings for cpr religion ids: ${missingMappings.joinToString(", ")}")
    }
    val nomisIdsToReligions =
      mappings.map { outer -> outer.nomisId to cprReligions.first { it.cprReligionId == outer.cprId } }
    if (nomisIdsToReligions.isNotEmpty()) {
      corePersonNomisApiService.mergeReligions(toPrisonNumber, nomisIdsToReligions)
    }
    telemetryClient.trackEvent("cpr-religions-merged-success", telemetryMap)
  }

  data class ReligionEvent(
    val eventType: String,
    val additionalInformation: CprReligionCreatedInfo,
    val personReference: PersonReferenceList,
  )

  data class CprReligionCreatedInfo(val cprReligionId: UUID)
}
