package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.CorrectionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.DescriptionAddendum
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.Event
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.HistoricalQuestion
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.HistoricalResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.History
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.PrisonerInvolvement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.Question
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.ReportWithDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.Response
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.StaffInvolvement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.StatusHistory
import java.time.LocalDateTime
import java.util.UUID

class IncidentsDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val incidentsDpsApi = IncidentsDpsApiMockServer()
    lateinit var objectMapper: ObjectMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
    incidentsDpsApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    incidentsDpsApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    incidentsDpsApi.stop()
  }
}

class IncidentsDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8091
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  fun stubGetIncident(incident: ReportWithDetails = dpsIncident()) {
    stubFor(
      get(urlMatching("/incident-reports/${incident.id}/with-details"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(incident)
            .withStatus(200),
        ),
    )
  }

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder {
    this.withBody(IncidentsDpsApiExtension.objectMapper.writeValueAsString(body))
    return this
  }
}

fun dpsIncident(): ReportWithDetails = ReportWithDetails(
  id = UUID.randomUUID(),
  reportReference = "1234",
  type = ReportWithDetails.Type.ATTEMPTED_ESCAPE_FROM_ESCORT_1,
  incidentDateAndTime = LocalDateTime.parse("2021-07-05T10:35:17"),
  prisonId = "ASI",
  location = "ASI",
  title = "There was an incident in the exercise yard",
  description = "Fred and Jimmy were fighting outside.",
  nomisType = "ATT_ESC_E",
  nomisStatus = "AWAN",
  event = Event(
    id = UUID.randomUUID(),
    eventReference = "1234",
    eventDateAndTime = LocalDateTime.parse("2021-07-05T10:35:17"),
    prisonId = "ASI",
    location = "ASI",
    title = "There was a problem",
    description = "Fighting was happening",
    createdAt = LocalDateTime.parse("2021-07-23T10:35:17"),
    modifiedAt = LocalDateTime.parse("2021-07-23T10:35:17"),
    modifiedBy = "JSMITH",
  ),
  reportedBy = "FSTAFF_GEN",
  reportedAt = LocalDateTime.parse("2021-07-07T10:35:17"),
  status = ReportWithDetails.Status.DRAFT,
  assignedTo = "BJONES",
  questions = listOf(
    Question(
      code = "1234",
      question = "Was anybody hurt?",
      additionalInformation = null,
      sequence = 1,
      responses = listOf(
        Response(
          response = "Yes",
          recordedBy = "JSMITH",
          recordedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
          additionalInformation = null,
          sequence = 1,
        ),
      ),
    ),
  ),
  history = listOf(
    History(
      type = History.Type.ABSCOND_1,
      changedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
      changedBy = "JSMITH",
      questions = listOf(
        HistoricalQuestion(
          code = "HQ1",
          question = "Were tools involved?",
          responses = listOf(
            HistoricalResponse(
              response = "Yes",
              sequence = 1,
              recordedBy = "Fred Jones",
              recordedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
              additionalInformation = "more info",
            ),
          ),
          additionalInformation = "some info",
          sequence = 1,
        ),
      ),
    ),
  ),
  historyOfStatuses = listOf(
    StatusHistory(
      status = StatusHistory.Status.DRAFT,
      changedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
      changedBy = "JSMITH",
    ),
  ),
  staffInvolved = listOf(
    StaffInvolvement(
      sequence = 1,
      staffUsername = "Dave Jones",
      staffRole = StaffInvolvement.StaffRole.ACTIVELY_INVOLVED,
      comment = "Dave was hit",
      firstName = "Dave",
      lastName = "Jones",
    ),
  ),
  prisonersInvolved = listOf(
    PrisonerInvolvement(
      sequence = 1,
      prisonerNumber = "A1234BC",
      prisonerRole = PrisonerInvolvement.PrisonerRole.ABSCONDER,
      outcome = PrisonerInvolvement.Outcome.PLACED_ON_REPORT,
      comment = "There were issues",
      firstName = "Dave",
      lastName = "Jones",
    ),
    PrisonerInvolvement(
      sequence = 2,
      prisonerNumber = "A1234BC",
      prisonerRole = PrisonerInvolvement.PrisonerRole.ABSCONDER,
      outcome = PrisonerInvolvement.Outcome.PLACED_ON_REPORT,
      firstName = "Dave",
      lastName = "Jones",
    ),
  ),
  correctionRequests = listOf(
    CorrectionRequest(
      sequence = 1,
      descriptionOfChange = "There was a change",
      correctionRequestedBy = "Fred Black",
      correctionRequestedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
    ),
  ),
  createdAt = LocalDateTime.parse("2021-07-05T10:35:17"),
  modifiedAt = LocalDateTime.parse("2021-07-15T10:35:17"),
  modifiedBy = "JSMITH",
  createdInNomis = false,
  lastModifiedInNomis = true,
  staffInvolvementDone = true,
  prisonerInvolvementDone = true,
  descriptionAddendums = listOf(
    DescriptionAddendum(
      sequence = 1,
      createdAt = LocalDateTime.parse("2021-07-05T10:35:17"),
      firstName = "Dave",
      lastName = "Jones",
      createdBy = "DJONES",
      text = "There was an amendment",
    ),
  ),
)
