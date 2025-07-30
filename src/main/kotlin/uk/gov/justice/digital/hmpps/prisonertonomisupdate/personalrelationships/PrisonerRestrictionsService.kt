package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PrisonerRestrictionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.PrisonerRestrictionMappingDto.MappingType.DPS_CREATED
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreatePrisonerRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonService.Companion.MappingTypes.PRISONER_RESTRICTION
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.model.SyncPrisonerRestriction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.synchronise

@Service
class PrisonerRestrictionsService(
  private val telemetryClient: TelemetryClient,
  private val contactPersonRetryQueueService: ContactPersonRetryQueueService,
  private val mappingApiService: ContactPersonMappingApiService,
  private val dpsApiService: ContactPersonDpsApiService,
  private val nomisApiService: ContactPersonNomisApiService,

) {
  suspend fun restrictionCreated(event: PrisonerRestrictionEvent) {
    val entityName = PRISONER_RESTRICTION.entityName

    val dpsRestrictionId = event.additionalInformation.prisonerRestrictionId
    val offenderNo = event.prisonerNumber()
    val telemetryMap = mutableMapOf(
      "dpsRestrictionId" to dpsRestrictionId.toString(),
      "offenderNo" to offenderNo,
    )

    if (event.didOriginateInDPS()) {
      synchronise {
        name = entityName
        telemetryClient = this@PrisonerRestrictionsService.telemetryClient
        retryQueueService = contactPersonRetryQueueService
        eventTelemetry = telemetryMap

        checkMappingDoesNotExist {
          mappingApiService.getByDpsPrisonerRestrictionIdOrNull(dpsRestrictionId)
        }
        transform {
          val dpsRestriction = dpsApiService.getPrisonerRestriction(dpsRestrictionId)
          nomisApiService.createPrisonerRestriction(offenderNo = offenderNo, dpsRestriction.toNomisCreateRequest()).also {
            telemetryMap["nomisRestrictionId"] = it.id.toString()
          }.let {
            PrisonerRestrictionMappingDto(
              dpsId = dpsRestrictionId.toString(),
              nomisId = it.id,
              offenderNo = offenderNo,
              mappingType = DPS_CREATED,
            )
          }
        }
        saveMapping { mappingApiService.createPrisonerRestrictionMapping(it) }
      }
    } else {
      telemetryClient.trackEvent("$entityName-create-ignored", telemetryMap)
    }
  }
  suspend fun restrictionUpdated(event: PrisonerRestrictionEvent) {
    val entityName = PRISONER_RESTRICTION.entityName

    val dpsRestrictionId = event.additionalInformation.prisonerRestrictionId
    val offenderNo = event.prisonerNumber()
    val telemetryMap = mutableMapOf(
      "dpsRestrictionId" to dpsRestrictionId.toString(),
      "offenderNo" to offenderNo,
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

private fun SyncPrisonerRestriction.toNomisCreateRequest() = CreatePrisonerRestrictionRequest(
  typeCode = this.restrictionType,
  effectiveDate = this.effectiveDate,
  expiryDate = this.expiryDate,
  comment = this.commentText,
  enteredStaffUsername = this.createdBy,
  authorisedStaffUsername = this.authorisedUsername,
)
