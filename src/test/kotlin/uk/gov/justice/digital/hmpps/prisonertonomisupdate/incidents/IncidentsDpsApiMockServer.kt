package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.exactly
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.CorrectionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.DescriptionAddendum
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.HistoricalQuestion
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.HistoricalResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.History
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.IncidentTypeHistory
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.PrisonerInvolvement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.Question
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.ReportBasic
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.ReportWithDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.Response
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.SimplePageReportBasic
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.StaffInvolvement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents.model.StatusHistory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.math.min

class IncidentsDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val incidentsDpsApi = IncidentsDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
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
      get(urlEqualTo("/incident-reports/${incident.id}/with-details"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(incident)
            .withStatus(200),
        ),
    )
  }

  fun stubGetIncidentByNomisId(nomisIncidentId: Long = 1234, response: ReportWithDetails = dpsIncident().copy(reportReference = nomisIncidentId.toString())) {
    stubFor(
      get(urlMatching("/incident-reports/reference/$nomisIncidentId/with-details")).willReturn(
        aResponse()
          .withStatus(HttpStatus.OK.value())
          .withHeader("Content-Type", APPLICATION_JSON_VALUE)
          .withBody(response),
      ),
    )
  }

  fun stubGetIncidents(startIncidentId: Long, endIncidentId: Long) {
    (startIncidentId..endIncidentId).forEach { nomisIncidentId ->
      stubGetIncidentByNomisId(nomisIncidentId)
    }
  }
  fun stubGetIncidentsWithError(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    stubFor(
      get(urlPathMatching("/incident-reports"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(error),
        ),
    )
  }

  fun stubGetIncidentCounts(totalElements: Long = 3, pageSize: Long = 20, urlMatch: MappingBuilder = get(urlPathMatching("/incident-reports"))) {
    val content: List<ReportBasic> = (1..min(pageSize, totalElements)).map {
      dpsBasicIncident(dpsIncidentId = UUID.randomUUID().toString())
    }
    stubFor(
      urlMatch
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              SimplePageReportBasic(
                content = content,
                number = 0,
                propertySize = pageSize.toInt(),
                totalElements = totalElements,
                sort = listOf("incidentDateAndTime,DESC"),
                numberOfElements = pageSize.toInt(),
                totalPages = totalElements.toInt(),
              ),
            ),
        ),
    )
  }
  fun stubGetASIClosedIncidentCounts() = stubGetIncidentCounts(totalElements = 8, urlMatch = get(urlPathMatching("/incident-reports")).withQueryParam("location", matching("ASI")).withQueryParam("status", matching("CLOSED")))

  fun verifyGetIncidentCounts(times: Int = 1) = verify(exactly(times), getRequestedFor(urlPathMatching("/incident-reports")))

  fun verifyGetIncidentDetail(times: Int = 1) = verify(exactly(times), getRequestedFor(urlMatching("/incident-reports/reference/[0-9]+/with-details")))

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder {
    this.withBody(IncidentsDpsApiExtension.jsonMapper.writeValueAsString(body))
    return this
  }
}

fun dpsBasicIncident(dpsIncidentId: String = "fb4b2e91-91e7-457b-aa17-797f8c5c2f42", prisonId: String = "ASI") = ReportBasic(
  id = UUID.fromString(dpsIncidentId),
  reportReference = "1234",
  type = ReportBasic.Type.SELF_HARM_1,
  incidentDateAndTime = LocalDateTime.parse("2021-07-05T10:35:17"),
  location = prisonId,
  title = "There was an incident in the exercise yard",
  description = "Fred and Jimmy were fighting outside.",
  reportedBy = "JSMITH",
  reportedAt = LocalDateTime.parse("2021-07-05T10:35:17.12345"),
  status = ReportBasic.Status.DRAFT,
  createdAt = LocalDateTime.parse("2021-07-05T10:35:17"),
  modifiedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
  modifiedBy = "JSMITH",
  createdInNomis = true,
  lastModifiedInNomis = true,
)

fun dpsIncident(): ReportWithDetails = ReportWithDetails(
  id = UUID.randomUUID(),
  reportReference = "1234",
  nomisType = "ATT_ESC_E",
  type = ReportWithDetails.Type.ATTEMPTED_ESCAPE_FROM_ESCORT_1,
  incidentDateAndTime = LocalDateTime.parse("2021-07-05T10:35:17"),
  location = "ASI",
  title = "There was an incident in the exercise yard",
  description = "Fred and Jimmy were fighting outside.",
  nomisStatus = "AWAN",
  status = ReportWithDetails.Status.AWAITING_REVIEW,
  reportedBy = "FSTAFF_GEN",
  reportedAt = LocalDateTime.parse("2021-07-07T10:35:17.12345"),
  questions = listOf(
    Question(
      code = "1234",
      question = "Was anybody hurt?",
      additionalInformation = null,
      sequence = 1,
      responses = listOf(
        Response(
          code = "123",
          response = "Yes",
          label = "Yes",
          recordedBy = "JSMITH",
          responseDate = LocalDate.parse("2021-06-05"),
          recordedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
          additionalInformation = null,
          sequence = 1,
        ),
      ),
      label = "Was anybody hurt?",
    ),
    Question(
      code = "12345",
      question = "Where was the drone?",
      additionalInformation = null,
      sequence = 2,
      responses = listOf(
        Response(
          code = "456",
          label = "No",
          response = "No",
          recordedBy = "JSMITH",
          recordedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
          additionalInformation = "some comment",
          sequence = 2,
        ),
        Response(
          code = "789",
          label = "Billy Bob",
          response = "Bob",
          recordedBy = "JSMITH",
          recordedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
          additionalInformation = "some additional comment",
          sequence = 3,
        ),
      ),
      label = "Where was the drone?",
    ),
  ),
  history = listOf(
    History(
      type = History.Type.ABSCOND_1,
      changedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
      changedBy = "JSMITH",
      questions = listOf(
        HistoricalQuestion(
          code = "998",
          question = "Were tools involved?",
          responses = listOf(
            HistoricalResponse(
              code = "123",
              response = "Yes",
              label = "Yes",
              sequence = 1,
              recordedBy = "Fred Jones",
              recordedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
              additionalInformation = "more info",
            ),
          ),
          additionalInformation = "some info",
          sequence = 1,
          label = "Were tools involved?",
        ),
        HistoricalQuestion(
          code = "999",
          question = "Was paper involved?",
          responses = listOf(
            HistoricalResponse(
              code = "456",
              response = "Yes",
              label = "Yes",
              sequence = 1,
              recordedBy = "Fred Jones",
              recordedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
            ),
          ),
          sequence = 2,
          label = "Was paper involved?",
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
      staffUsername = "DJONES",
      staffRole = StaffInvolvement.StaffRole.ACTIVELY_INVOLVED,
      comment = "Dave was hit",
      firstName = "Dave",
      lastName = "Jones",
    ),
    StaffInvolvement(
      sequence = 2,
      staffUsername = "DSMITH",
      staffRole = StaffInvolvement.StaffRole.CR_SUPERVISOR,
      comment = "Dave wasn't hit",
      firstName = "Dave",
      lastName = "Smith",
    ),
    StaffInvolvement(
      sequence = 2,
      staffRole = StaffInvolvement.StaffRole.CR_SUPERVISOR,
      comment = "DPS User that isn't held in Nomis - is ignored as no staffUsername set",
      firstName = "Dave",
      lastName = "Smith",
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
      prisonerNumber = "A1234BD",
      prisonerRole = PrisonerInvolvement.PrisonerRole.FIGHTER,
      outcome = PrisonerInvolvement.Outcome.POLICE_INVESTIGATION,
      firstName = "Dave",
      lastName = "Jones",
    ),
  ),
  correctionRequests = listOf(
    CorrectionRequest(
      sequence = 0,
      descriptionOfChange = "There was a change",
      correctionRequestedBy = "Fred Black",
      correctionRequestedAt = LocalDateTime.parse("2021-07-05T10:35:17"),
      location = "MDI",
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
  incidentTypeHistory = listOf(
    IncidentTypeHistory(
      type = IncidentTypeHistory.Type.BARRICADE_1,
      changedAt = LocalDateTime.parse("2021-07-05T11:35:17"),
      changedBy = "JSMITH",
    ),
  ),
)

val dpsQuestionWithNoAnswers = Question(
  code = "5678",
  question = "Was anybody hurt?",
  additionalInformation = null,
  sequence = 3,
  responses = listOf(),
  label = "Was anybody hurt?",
)
