package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.telemetryOf
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.CorrectionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.DescriptionAddendum
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.ReportWithDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.ReportWithDetails.Status
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.ReportWithDetails.Type
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertDescriptionAmendmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertIncidentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertIncidentRequirementRequest
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
  typeCode = mapDpsType(this.type),
  location = this.location,
  statusCode = mapDpsStatus(this.status),
  reportedDateTime = this.reportedAt,
  reportedBy = this.reportedBy,
  incidentDateTime = this.incidentDateAndTime,
  requirements = this.correctionRequests.map { it.toNomisUpsertIncidentRequirementRequest() },
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
  location = this.location!!, // TODO (PGP): Confirm that this can be nullable and work out what to default to if is
  comment = this.descriptionOfChange,
)

private fun ReportWithDetails.mapDpsType(type: Type): String = when (type) {
  Type.ABSCOND_1 -> "ABSCOND"
  Type.ASSAULT_1 -> "ASSAULT"
  Type.ASSAULT_2 -> "ASSAULTS"
  Type.ASSAULT_3 -> "ASSAULTS1"
  Type.ASSAULT_4 -> "ASSAULTS2"
  Type.ASSAULT_5 -> "ASSAULTS3"
  Type.ATTEMPTED_ESCAPE_FROM_PRISON_1 -> "ATT_ESCAPE"
  Type.ATTEMPTED_ESCAPE_FROM_ESCORT_1 -> "ATT_ESC_E"
  Type.BARRICADE_1 -> "BARRICADE"
  Type.BOMB_1 -> "BOMB"
  Type.BREACH_OF_SECURITY_1 -> "BREACH"
  Type.CLOSE_DOWN_SEARCH_1 -> "CLOSE_DOWN"
  Type.CONCERTED_INDISCIPLINE_1 -> "CON_INDISC"
  Type.DAMAGE_1 -> "DAMAGE"
  Type.DEATH_PRISONER_1 -> "DEATH"
  Type.DEATH_OTHER_1 -> "DEATH_NI"
  Type.DISORDER_1 -> "DISORDER"
  Type.DISORDER_2 -> "DISORDER1"
  Type.DRONE_SIGHTING_1 -> "DRONE"
  Type.DRONE_SIGHTING_2 -> "DRONE1"
  Type.DRONE_SIGHTING_3 -> "DRONE2"
  Type.DRUGS_1 -> "DRUGS"
  Type.ESCAPE_FROM_PRISON_1 -> "ESCAPE_EST"
  Type.ESCAPE_FROM_ESCORT_1 -> "ESCAPE_ESC"
  Type.FIND_1 -> "FINDS"
  Type.FIND_2 -> "FIND"
  Type.FIND_3 -> "FIND1"
  Type.FIND_4 -> "FIND0322"
  Type.FIND_5 -> "FINDS1"
  Type.FIND_6 -> "FIND0422"
  Type.FIRE_1 -> "FIRE"
  Type.FIREARM_1 -> "FIREARM_ETC"
  Type.FOOD_REFUSAL_1 -> "FOOD_REF"
  Type.HOSTAGE_1 -> "HOSTAGE"
  Type.INCIDENT_AT_HEIGHT_1 -> "ROOF_CLIMB"
  Type.KEY_OR_LOCK_1 -> "KEY_LOCK"
  Type.KEY_OR_LOCK_2 -> "KEY_LOCKNEW"
  Type.MISCELLANEOUS_1 -> "MISC"
  Type.MOBILE_PHONE_1 -> "MOBILES"
  Type.RADIO_COMPROMISE_1 -> "RADIO_COMP"
  Type.RELEASE_IN_ERROR_1 -> "REL_ERROR"
  Type.SELF_HARM_1 -> "SELF_HARM"
  Type.TEMPORARY_RELEASE_FAILURE_1 -> "TRF"
  Type.TEMPORARY_RELEASE_FAILURE_2 -> "TRF1"
  Type.TEMPORARY_RELEASE_FAILURE_3 -> "TRF2"
  Type.TEMPORARY_RELEASE_FAILURE_4 -> "TRF3"
  Type.TOOL_LOSS_1 -> "TOOL_LOSS"
}

private fun ReportWithDetails.mapDpsStatus(status: Status): String = when (status) {
  Status.DRAFT -> throw IncidentStatusIgnoredException(status)
  Status.AWAITING_REVIEW -> "AWAN"
  Status.ON_HOLD -> "INAN"
  Status.NEEDS_UPDATING -> "INREQ"
  Status.UPDATED -> "INAME"
  Status.CLOSED -> "CLOSE"
  Status.POST_INCIDENT_UPDATE -> "PIU"
  Status.DUPLICATE, Status.NOT_REPORTABLE -> "DUP"
  Status.REOPENED, Status.WAS_CLOSED -> throw IncidentStatusIgnoredException(status)
}

class IncidentStatusIgnoredException(val status: Status) : Exception(status.value)
