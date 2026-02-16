package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incidents

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.History
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.HistoryQuestion
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.HistoryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.IncidentAgencyId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.IncidentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.IncidentStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.IncidentsCount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.IncidentsReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.Offender
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderParty
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.Question
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.Requirement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.Response
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.Staff
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffParty
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertIncidentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.pageContent
import java.time.LocalDate
import java.time.LocalDateTime
@Component
class IncidentsNomisApiMockServer(private val jsonMapper: JsonMapper) {
  companion object {
    fun upsertIncidentRequest(): UpsertIncidentRequest = UpsertIncidentRequest(
      title = "An incident occurred",
      description = "Fighting and shouting occurred in the prisoner's cell and a chair was thrown.",
      descriptionAmendments = listOf(),
      location = "BXI",
      statusCode = "AWAN",
      typeCode = "INACTIVE",
      incidentDateTime = LocalDateTime.parse("2023-12-30T13:45"),
      reportedDateTime = LocalDateTime.parse("2024-01-02T09:30"),
      reportedBy = "A_USER",
      requirements = listOf(),
      staffParties = listOf(),
      offenderParties = listOf(),
      questions = listOf(),
      history = listOf(),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)

  private fun incidentAgencies() = listOf(IncidentAgencyId("ASI"), IncidentAgencyId("BFI"), IncidentAgencyId("WWI"))

  private fun incidentAgencyCount(agencyId: String, open: Long, closed: Long) = IncidentsReconciliationResponse(agencyId = agencyId, IncidentsCount(openIncidents = open, closedIncidents = closed))

  fun stubGetReconciliationOpenIncidentIds(agencyId: String, start: Int = 33, finish: Long = 35) {
    nomisApi.stubFor(
      get(urlPathMatching("/incidents/reconciliation/agency/$agencyId/ids"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(
              incidentIdsPagedResponse(
                totalElements = 40,
                ids = (start..finish).map { it },
                pageNumber = 2,
                pageSize = 3,
              ),
            ),
        ),
    )
  }

  fun incidentIdsPagedResponse(
    totalElements: Long = 10,
    ids: List<Long> = (0L..10L).toList(),
    pageSize: Long = 10,
    pageNumber: Long = 0,
  ): String {
    val content = ids.map { """{ "incidentId": $it }""" }.joinToString { it }
    return pageContent(content, pageSize, pageNumber, totalElements, ids.size)
  }

  fun stubGetIncidentAgencies() {
    nomisApi.stubFor(
      get(urlPathEqualTo("/incidents/reconciliation/agencies"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(incidentAgencies()),
        ),
    )
  }
  fun stubGetReconciliationAgencyIncidentCounts(agencyId: String = "ASI", open: Long = 3, closed: Long = 3) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/incidents/reconciliation/agency/$agencyId/counts"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(
              incidentAgencyCount(
                agencyId,
                open,
                closed,
              ),
            ),
        ),
    )
  }
  fun stubGetIncident(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlPathMatching("/incidents/\\d+")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(error),
      ),
    )
  }

  fun stubGetIncident(
    nomisIncidentId: Long = 1234,
    offenderParty: String = "A1234BC",
    status: String = "AWAN",
    reportedDateTime: LocalDateTime = LocalDateTime.parse("2021-07-07T10:35:17"),
    type: String = "ATT_ESC_E",
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/incidents/$nomisIncidentId"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(
              incidentResponse(
                nomisIncidentId = nomisIncidentId,
                offenderPartyPrisonNumber = offenderParty,
                status = status,
                reportedDateTime = reportedDateTime,
                type = type,
              ),
            ),
        ),
    )
  }

  fun stubGetIncident(incidentResponse: IncidentResponse = incidentResponse()) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/incidents/${incidentResponse.incidentId}"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(incidentResponse),
        ),
    )
  }

  fun stubGetIncidents(startIncidentId: Long, endIncidentId: Long) {
    (startIncidentId..endIncidentId).forEach { nomisIncidentId ->
      stubGetIncident(nomisIncidentId)
    }
  }
  fun stubGetMismatchIncident() {
    nomisApi.stubFor(
      get(urlPathEqualTo("/incidents/33"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(
              incidentResponse(
                status = "INREQ",
                offenderPartyPrisonNumber = "Z4321YX",
                nomisIncidentId = 33,
              )
                .copy(
                  questions =
                  listOf(
                    Question(
                      questionId = 1234,
                      sequence = 4,
                      question = "Was anybody hurt?",
                      answers = listOf(),
                      createDateTime = LocalDateTime.parse("2021-07-05T10:35:17"),
                      createdBy = "JSMITH",
                      hasMultipleAnswers = false,
                    ),
                  ),
                  requirements = listOf(),
                  staffParties = listOf(),
                ),
            ),
        ),
    )
  }

  fun stubGetMismatchResponsesForIncident() {
    nomisApi.stubFor(
      get(urlPathEqualTo("/incidents/33"))
        .willReturn(
          aResponse().withHeader("Content-Type", "application/json").withStatus(HttpStatus.OK.value())
            .withBody(
              incidentResponse()
                .copy(
                  incidentId = 33,
                  questions =
                  listOf(
                    Question(
                      questionId = 1234,
                      sequence = 1,
                      question = "Was anybody hurt?",
                      answers = listOf(answer(), answer2),
                      createDateTime = LocalDateTime.parse("2021-07-05T10:35:17"),
                      createdBy = "JSMITH",
                      hasMultipleAnswers = true,
                    ),
                    Question(
                      questionId = 12345,
                      sequence = 2,
                      question = "Where was the drone?",
                      answers = listOf(answer(3)),
                      createDateTime = LocalDateTime.parse("2021-07-05T10:35:17"),
                      createdBy = "JSMITH",
                      hasMultipleAnswers = true,
                    ),
                  ),
                ),
            ),
        ),
    )
  }

  fun stubUpsertIncident(
    incidentId: Long = 123456,
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/incidents/$incidentId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value()),
      ),
    )
  }

  fun stubDeleteIncident(
    incidentId: Long = 123456,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/incidents/$incidentId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }
  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder = this.withBody(jsonMapper.writeValueAsString(body))
}

fun incidentResponse(
  nomisIncidentId: Long = 1234,
  offenderPartyPrisonNumber: String = "A1234BC",
  status: String = "AWAN",
  reportedDateTime: LocalDateTime = LocalDateTime.parse("2021-07-07T10:35:17"),
  type: String = "ATT_ESC_E",
): IncidentResponse = IncidentResponse(
  incidentId = nomisIncidentId,
  questionnaireId = 45456,
  title = "This is a test incident",
  description = "On 12/04/2023 approx 16:45 Mr Smith tried to escape.",
  status = IncidentStatus(
    code = status,
    description = "Awaiting Analysis",
    listSequence = 1,
    standardUser = true,
    enhancedUser = true,
  ),
  agency = CodeDescription(
    code = "BXI",
    description = "Brixton",
  ),
  type = type,
  lockedResponse = false,
  incidentDateTime = LocalDateTime.parse("2017-04-12T16:45:00"),
  reportingStaff = Staff(
    username = "FSTAFF_GEN",
    staffId = 485572,
    firstName = "FRED",
    lastName = "STAFF",
  ),
  followUpDate = LocalDate.parse("2017-04-12"),
  createDateTime = LocalDateTime.parse("2021-07-23T10:35:17"),
  createdBy = "JIM SMITH",
  lastModifiedBy = "JIM_ADM",
  lastModifiedDateTime = LocalDateTime.parse("2021-07-23T10:35:17"),
  reportedDateTime = reportedDateTime,
  staffParties =
  listOf(
    StaffParty(
      staff = Staff(
        username = "DJONES",
        staffId = 485577,
        firstName = "DAVE",
        lastName = "JONES",
      ),
      sequence = 1,
      role = CodeDescription("ACT", "Actively Involved"),
      comment = "Dave was hit",
      createDateTime = LocalDateTime.parse("2021-07-23T10:35:17"),
      createdBy = "JIM SMITH",
    ),
    StaffParty(
      staff = Staff(
        username = "DSMITH",
        staffId = 485578,
        firstName = "DAVE",
        lastName = "SMITH",
      ),
      sequence = 2,
      role = CodeDescription("CRS", "C&R Supervisor"),
      comment = "Dave 2 was also hit",
      createDateTime = LocalDateTime.parse("2021-07-24T10:35:17"),
      createdBy = "JIM SMITH",
    ),
  ),
  offenderParties = listOf(offenderParty(offenderPartyPrisonNumber), offenderParty("A1234BD", 2)),
  requirements = listOf(
    Requirement(
      agencyId = "ASI",
      staff = Staff(
        username = "DJONES",
        staffId = 485577,
        firstName = "DAVE",
        lastName = "JONES",
      ),
      sequence = 1,
      comment = "Complete the incident report",
      createDateTime = LocalDateTime.parse("2021-07-05T10:35:17"),
      createdBy = "JSMITH",
      recordedDate = LocalDateTime.parse("2021-08-06T10:01:02"),
    ),
  ),
  questions = listOf(questionWith1Answer, question2With2Answers),
  history = listOf(
    History(
      questionnaireId = 1234,
      type = "ATT_ESC_E",
      description = "Escape Attempt",
      incidentChangeDateTime = LocalDateTime.parse("2021-07-05T10:35:17"),
      incidentChangeStaff = jimSmithStaff,
      createDateTime = LocalDateTime.parse("2021-07-05T10:35:17"),
      createdBy = "JSMITH",
      questions = listOf(
        HistoryQuestion(
          questionId = 1234,
          sequence = 1,
          question = "Was anybody hurt?",
          answers = listOf(
            HistoryResponse(
              responseSequence = 1,
              recordingStaff = jimSmithStaff,
              questionResponseId = null,
              answer = "Yes",
              responseDate = null,
              comment = null,
            ),
          ),
        ),
      ),
    ),
  ),
)

val jimSmithStaff = Staff(
  username = "JSMITH",
  staffId = 485572,
  firstName = "JIM",
  lastName = "SMITH",
)

fun offenderParty(offenderNo: String = "A1234BC", sequence: Int = 1) = OffenderParty(
  offender = Offender(offenderNo, firstName = "Fred", lastName = "smith"),
  sequence = sequence,
  role = CodeDescription("ABS", "Absconder"),
  createDateTime = LocalDateTime.parse("2024-02-06T12:36:00"),
  createdBy = "JIM",
  comment = "This is a comment",
  outcome = CodeDescription("AAA", "SOME OUTCOME"),
)

fun answer(sequence: Int = 1) = Response(
  sequence = sequence,
  recordingStaff = jimSmithStaff,
  createDateTime = LocalDateTime.parse("2021-07-05T10:35:17"),
  createdBy = "JSMITH",
  questionResponseId = 321,
  answer = "Yes",
  responseDate = null,
  comment = null,
)
val answer2 = Response(
  sequence = 2,
  recordingStaff = jimSmithStaff,
  createDateTime = LocalDateTime.parse("2021-07-05T10:35:17"),
  createdBy = "JSMITH",
  questionResponseId = 432,
  answer = "Outside",
  responseDate = null,
  comment = null,
)

val nomisQuestionWithNoAnswers = Question(
  questionId = 5678,
  sequence = 3,
  question = "Was anybody hurt?",
  hasMultipleAnswers = false,
  answers = listOf(),
  createDateTime = LocalDateTime.parse("2021-07-05T10:35:17"),
  createdBy = "JSMITH",
)
val questionWith1Answer = Question(
  questionId = 1234,
  sequence = 1,
  question = "Was anybody hurt?",
  hasMultipleAnswers = false,
  answers = listOf(answer()),
  createDateTime = LocalDateTime.parse("2021-07-05T10:35:17"),
  createdBy = "JSMITH",
)

val question2With2Answers = Question(
  questionId = 12345,
  sequence = 2,
  question = "Where was the drone?",
  answers = listOf(
    Response(
      sequence = 2,
      recordingStaff = jimSmithStaff,
      createDateTime = LocalDateTime.parse("2021-07-05T10:35:17"),
      createdBy = "JSMITH",
      questionResponseId = 456,
      answer = "No",
      responseDate = null,
      comment = "some comment",
    ),
    Response(
      sequence = 3,
      recordingStaff = jimSmithStaff,
      createDateTime = LocalDateTime.parse("2021-07-05T10:35:17"),
      createdBy = "JSMITH",
      questionResponseId = 789,
      answer = "Bob",
      responseDate = null,
      comment = "some additional comment",
    ),
  ),
  createDateTime = LocalDateTime.parse("2021-07-05T10:35:17"),
  createdBy = "JSMITH",
  hasMultipleAnswers = true,
)

val question1WithInvalidAnswerCount = Question(
  questionId = 1234,
  sequence = 1,
  question = "Was anybody hurt?",
  hasMultipleAnswers = false,
  answers = listOf(
    Response(
      sequence = 1,
      recordingStaff = Staff(
        username = "JSMITH",
        staffId = 485572,
        firstName = "JIM",
        lastName = "SMITH",
      ),
      createDateTime = LocalDateTime.parse("2021-07-05T10:35:17"),
      createdBy = "JSMITH",
    ),
    Response(
      questionResponseId = 123,
      sequence = 2,
      answer = "Yes",
      recordingStaff = Staff(
        username = "JSMITH",
        staffId = 485572,
        firstName = "JIM",
        lastName = "SMITH",
      ),
      createDateTime = LocalDateTime.parse("2021-07-05T10:35:17"),
      createdBy = "JSMITH",
    ),
  ),
  createDateTime = LocalDateTime.parse("2021-07-05T10:35:17"),
  createdBy = "JSMITH",
)
