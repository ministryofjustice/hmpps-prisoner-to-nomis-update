package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.telemetryOf
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.CorrectionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.DescriptionAddendum
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.PrisonerInvolvement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.ReportWithDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.ReportWithDetails.Status
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.StaffInvolvement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertDescriptionAmendmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertIncidentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertIncidentRequirementRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertOffenderPartyRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertStaffPartyRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreatingSystem

@Service
class IncidentsService(
  private val nomisApiService: IncidentsNomisApiService,
  private val dpsApiService: IncidentsDpsApiService,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun incidentUpsert(event: IncidentEvent) {
    val dpsId = event.dpsId
    val nomisId = event.nomisId
    val telemetryMap = telemetryOf(
      "dpsIncidentId" to dpsId.toString(),
      "nomisIncidentId" to nomisId,
    )

    if (event.didOriginateInDPS()) {
      try {
        val dps = dpsApiService.getIncident(dpsId)
        nomisApiService.upsertIncident(
          nomisId = nomisId,
          dps.toNomisUpsertRequest(),
        )
        telemetryClient.trackEvent("incident-upsert-success", telemetryMap)
      } catch (e: IncidentStatusIgnoredException) {
        telemetryClient.trackEvent("incident-upsert-status-ignored", telemetryMap + ("status" to e.status.value))
      }
    } else {
      telemetryClient.trackEvent("incident-upsert-ignored", telemetryMap)
    }
  }
  suspend fun incidentDeleted(event: IncidentEvent) {
    val dpsId = event.dpsId
    val nomisId = event.nomisId
    val telemetryMap = mutableMapOf(
      "dpsIncidentId" to dpsId.toString(),
      "nomisIncidentId" to nomisId,
    )

    if (event.didOriginateInDPS()) {
      nomisApiService.deleteIncident(nomisId = nomisId)
      telemetryClient.trackEvent("incident-delete-success", telemetryMap)
    } else {
      telemetryClient.trackEvent("incident-delete-ignored", telemetryMap)
    }
  }
}
data class IncidentEvent(
  val eventType: String,
  val additionalInformation: IncidentAdditionalInformation,
) {
  val dpsId: String
    get() = additionalInformation.id
  val nomisId: Long
    get() = additionalInformation.reportReference
}
data class IncidentAdditionalInformation(
  val id: String,
  val reportReference: Long,
  val source: CreatingSystem,
  // TODO - check - we may not need this
  val whatChanged: String?,
)

private fun IncidentEvent.didOriginateInDPS() = this.additionalInformation.source == CreatingSystem.DPS

private fun ReportWithDetails.toNomisUpsertRequest(): UpsertIncidentRequest = UpsertIncidentRequest(
  title = this.title,
  description = this.description,
  descriptionAmendments = this.descriptionAddendums.map { it.toNomisUpsertDescriptionAmendmentRequest() },
  typeCode = this.type.mapDps(),
  location = this.location,
  statusCode = this.status.mapDps(),
  reportedDateTime = this.reportedAt,
  reportedBy = this.reportedBy,
  incidentDateTime = this.incidentDateAndTime,
  requirements = this.correctionRequests.map { it.toNomisUpsertIncidentRequirementRequest() },
  offenderParties = this.prisonersInvolved.map { it.toNomisUpsertOffenderPartyRequest() },
  // only interested in staff that are actually in NOMIS
  staffParties = this.staffInvolved.filter { it.staffUsername != null }.map { it.toNomisUpsertStaffPartyRequest() },
)

private fun DescriptionAddendum.toNomisUpsertDescriptionAmendmentRequest(): UpsertDescriptionAmendmentRequest = UpsertDescriptionAmendmentRequest(
  createdDateTime = this.createdAt,
  firstName = this.firstName,
  lastName = this.lastName,
  text = this.text,
)

private fun CorrectionRequest.toNomisUpsertIncidentRequirementRequest(): UpsertIncidentRequirementRequest = UpsertIncidentRequirementRequest(
  date = this.correctionRequestedAt,
  username = this.correctionRequestedBy,
  location = this.location!!,
  comment = this.descriptionOfChange,
)

private fun PrisonerInvolvement.toNomisUpsertOffenderPartyRequest(): UpsertOffenderPartyRequest = UpsertOffenderPartyRequest(
  comment = this.comment,
  prisonNumber = this.prisonerNumber,
  outcome = this.outcome?.mapDps(),
  role = this.prisonerRole.mapDps(),
)

private fun StaffInvolvement.toNomisUpsertStaffPartyRequest(): UpsertStaffPartyRequest = UpsertStaffPartyRequest(
  comment = this.comment,
  username = this.staffUsername!!,
  role = this.staffRole.mapDps(),
)

class IncidentStatusIgnoredException(val status: Status) : Exception(status.value)
