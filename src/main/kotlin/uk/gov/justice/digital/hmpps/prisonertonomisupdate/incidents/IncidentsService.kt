package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents
import com.microsoft.applicationinsights.TelemetryClient
import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.BadRequestException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.telemetryOf
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.trackEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.CorrectionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.CorrectionRequest.UserType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.DescriptionAddendum
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.HistoricalQuestion
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.HistoricalResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.History
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.PrisonerInvolvement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.Question
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.ReportWithDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.ReportWithDetails.Status
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.Response
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.StaffInvolvement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertDescriptionAmendmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertIncidentHistoryRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertIncidentQuestionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertIncidentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertIncidentRequirementRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertIncidentResponseRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertOffenderPartyRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertStaffPartyRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.CreatingSystem
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService

@Service
class IncidentsService(
  private val incidentsNomisApiService: IncidentsNomisApiService,
  private val nomisApiService: NomisApiService,
  private val dpsApiService: IncidentsDpsApiService,
  private val telemetryClient: TelemetryClient,
) {

  suspend fun incidentUpsert(event: IncidentEvent) {
    val dpsId = event.dpsId
    val nomisId = event.nomisId
    val telemetryMap = telemetryOf(
      "dpsIncidentId" to dpsId,
      "nomisIncidentId" to nomisId,
    )

    if (event.didOriginateInDPS()) {
      try {
        val dps = dpsApiService.getIncident(dpsId)
        if (nomisApiService.isAgencySwitchOnForAgency("INCIDENTS", dps.location)) {
          incidentsNomisApiService.upsertIncident(
            nomisId = nomisId,
            dps.toNomisUpsertRequest(),
          )
          telemetryClient.trackEvent("incident-upsert-success", telemetryMap)
        } else {
          telemetryClient.trackEvent("incident-upsert-location-ignored", telemetryMap + ("location" to dps.location))
        }
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
      "dpsIncidentId" to dpsId,
      "nomisIncidentId" to nomisId,
    )

    if (event.didOriginateInDPS()) {
      incidentsNomisApiService.deleteIncident(nomisId = nomisId)
      telemetryClient.trackEvent("incident-delete-success", telemetryMap)
    } else {
      telemetryClient.trackEvent("incident-delete-ignored", telemetryMap)
    }
  }

  suspend fun repairIncident(incidentId: Long) {
    val dps = dpsApiService.getIncidentDetailsByNomisId(incidentId)
    if (!nomisApiService.isAgencySwitchOnForAgency("INCIDENTS", dps.location)) {
      throw BadRequestException("Incidents not switched on for ${dps.location}")
    }
    incidentsNomisApiService.upsertIncident(
      nomisId = incidentId,
      dps.toNomisUpsertRequest(),
    )
    telemetryClient.trackEvent("incident-repair-success", telemetryOf("incidentId" to incidentId))
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

private fun ReportWithDetails.toNomisUpsertRequest(): UpsertIncidentRequest {
  // incident response sequence unique across all questions
  val sequence = IncidentResponseSequence(0)
  return UpsertIncidentRequest(
    title = this.title,
    description = this.description,
    descriptionAmendments = this.descriptionAddendums.map { it.toNomisUpsertDescriptionAmendmentRequest() },
    typeCode = this.type.mapDps(),
    location = this.location,
    statusCode = this.status.mapDps(),
    reportedDateTime = this.reportedAt,
    reportedBy = this.reportedBy,
    incidentDateTime = this.incidentDateAndTime,
    requirements = this.correctionRequests.map { it.toNomisUpsertIncidentRequirementRequest(this.location) },
    offenderParties = this.prisonersInvolved.map { it.toNomisUpsertOffenderPartyRequest() },
    // only interested in staff that are actually in NOMIS
    staffParties = this.staffInvolved.filter { it.staffUsername != null }.map { it.toNomisUpsertStaffPartyRequest() },
    questions = this.questions.map { it.toNomisUpsertIncidentQuestionRequest(sequence) },
    history = this.history.map { it.toNomisUpsertIncidentHistoryRequest() },
  )
}

// response sequence is unique across all the questions, rather than specific to a single question
data class IncidentResponseSequence(var value: Int)

private fun DescriptionAddendum.toNomisUpsertDescriptionAmendmentRequest(): UpsertDescriptionAmendmentRequest = UpsertDescriptionAmendmentRequest(
  createdDateTime = this.createdAt,
  firstName = this.firstName,
  lastName = this.lastName,
  text = this.text,
)

private fun CorrectionRequest.toNomisUpsertIncidentRequirementRequest(reportLocation: String): UpsertIncidentRequirementRequest = UpsertIncidentRequirementRequest(
  date = this.correctionRequestedAt,
  username = this.correctionRequestedBy,
  location = this.setLocation(reportLocation),
  comment = this.descriptionOfChange,
)

private fun CorrectionRequest.setLocation(reportLocation: String): String = location
  ?: if (userType == UserType.DATA_WARDEN) {
    "NOU"
  } else {
    reportLocation
  }

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

private fun Question.toNomisUpsertIncidentQuestionRequest(incidentResponseSequence: IncidentResponseSequence): UpsertIncidentQuestionRequest = UpsertIncidentQuestionRequest(
  questionId = this.code.toLong(),
  responses = this.responses.map { it.toNomisUpsertIncidentResponseRequest(incidentResponseSequence) },
)

private fun HistoricalQuestion.toNomisUpsertIncidentQuestionRequest(incidentResponseSequence: IncidentResponseSequence): UpsertIncidentQuestionRequest = UpsertIncidentQuestionRequest(
  questionId = this.code.toLong(),
  responses = this.responses.map { it.toNomisUpsertIncidentResponseRequest(incidentResponseSequence) },
)

private fun Response.toNomisUpsertIncidentResponseRequest(incidentResponseSequence: IncidentResponseSequence): UpsertIncidentResponseRequest = UpsertIncidentResponseRequest(
  answerId = this.code!!.toLong(),
  comment = this.additionalInformation,
  responseDate = this.responseDate,
  recordingUsername = this.recordedBy,
  sequence = incidentResponseSequence.value++,
)

private fun HistoricalResponse.toNomisUpsertIncidentResponseRequest(incidentResponseSequence: IncidentResponseSequence): UpsertIncidentResponseRequest = UpsertIncidentResponseRequest(
  answerId = this.code!!.toLong(),
  comment = this.additionalInformation,
  responseDate = this.responseDate,
  recordingUsername = this.recordedBy,
  sequence = incidentResponseSequence.value++,
)

private fun History.toNomisUpsertIncidentHistoryRequest(): UpsertIncidentHistoryRequest {
  val sequence = IncidentResponseSequence(0)
  return UpsertIncidentHistoryRequest(
    typeCode = this.type.mapDps(),
    incidentChangeDateTime = this.changedAt,
    incidentChangeUsername = this.changedBy,
    questions = this.questions.map { it.toNomisUpsertIncidentQuestionRequest(sequence) },
  )
}

class IncidentStatusIgnoredException(val status: Status) : Exception(status.value)
