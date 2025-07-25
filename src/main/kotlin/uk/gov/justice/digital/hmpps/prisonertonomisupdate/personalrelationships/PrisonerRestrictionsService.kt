package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonService.Companion.MappingTypes.PRISONER_RESTRICTION

@Service
class PrisonerRestrictionsService(
  private val telemetryClient: TelemetryClient,

) {
  suspend fun restrictionCreated(event: PrisonerRestrictionEvent) {
    val entityName = PRISONER_RESTRICTION.entityName

    val dpsRestrictionId = event.additionalInformation.prisonerRestrictionId
    val telemetryMap = mutableMapOf(
      "dpsRestrictionId" to dpsRestrictionId.toString(),
      "offenderNo" to event.prisonerNumber(),
    )

    if (event.didOriginateInDPS()) {
      throw UnsupportedOperationException("DPS created restrictions are not currently supported")
    } else {
      telemetryClient.trackEvent("$entityName-create-ignored", telemetryMap)
    }
  }
  suspend fun restrictionUpdated(event: PrisonerRestrictionEvent) {
    val entityName = PRISONER_RESTRICTION.entityName

    val dpsRestrictionId = event.additionalInformation.prisonerRestrictionId
    val telemetryMap = mutableMapOf(
      "dpsRestrictionId" to dpsRestrictionId.toString(),
      "offenderNo" to event.prisonerNumber(),
    )

    if (event.didOriginateInDPS()) {
      throw UnsupportedOperationException("DPS updated restrictions are not currently supported")
    } else {
      telemetryClient.trackEvent("$entityName-update-ignored", telemetryMap)
    }
  }
  suspend fun restrictionDeleted(event: PrisonerRestrictionEvent) {
    val entityName = PRISONER_RESTRICTION.entityName

    val dpsRestrictionId = event.additionalInformation.prisonerRestrictionId
    val telemetryMap = mutableMapOf(
      "dpsRestrictionId" to dpsRestrictionId.toString(),
      "offenderNo" to event.prisonerNumber(),
    )

    if (event.didOriginateInDPS()) {
      throw UnsupportedOperationException("DPS deleted restrictions are not currently supported")
    } else {
      telemetryClient.trackEvent("$entityName-delete-ignored", telemetryMap)
    }
  }
}

private fun PrisonerRestrictionEvent.didOriginateInDPS() = this.additionalInformation.source == "DPS"
