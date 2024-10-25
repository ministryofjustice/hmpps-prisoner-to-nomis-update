package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.Actions
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.Attendee
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CSIPComponent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CSIPComponent.Component.FACTOR
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CSIPComponent.Component.PLAN
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CSIPFactorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CSIPResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.Decision
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.InterviewDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.InvestigationDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.Offender
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.Plan
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.PrisonerCSIPsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.ReportDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.Review
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.SaferCustodyScreening
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpsertCSIPResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate

@Component
class CSIPNomisApiMockServer(private val objectMapper: ObjectMapper) {
  fun stubPutCSIP(
    csipResponse: UpsertCSIPResponse = upsertCSIPResponse(),
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/csip")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value())
          .withBody(objectMapper.writeValueAsString(csipResponse)),
      ),
    )
  }

  fun stubDeleteCSIP(
    nomisCSIPReportId: Long = 12345678,
  ) {
    nomisApi.stubFor(
      delete(urlEqualTo("/csip/$nomisCSIPReportId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubGetCSIPsForReconciliation(
    offenderNo: String,
    response: PrisonerCSIPsResponse = PrisonerCSIPsResponse(offenderCSIPs = emptyList()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$offenderNo/csip/reconciliation")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun upsertCSIPResponse(nomisCSIPReportId: Long = 12345) = UpsertCSIPResponse(
  nomisCSIPReportId = nomisCSIPReportId,
  offenderNo = "A1234BC",
  components = listOf(
    CSIPComponent(FACTOR, 111, "8cdadcf3-b003-4116-9956-c99bd8df6111"),
    CSIPComponent(PLAN, 222, "8cdadcf3-b003-4116-9956-c99bd8df6333"),
  ),
)

fun nomisCSIPFactor(nomisCSIPFactorId: Long = 43) =
  CSIPFactorResponse(
    id = nomisCSIPFactorId,
    type = CodeDescription(code = "BUL", description = "Bullying"),
    comment = "Offender causes trouble",
    createDateTime = "2024-04-01T10:00:00",
    createdBy = "CFACTOR",
  )

fun nomisSCS() = SaferCustodyScreening(
  outcome = CodeDescription(
    code = "CUR",
    description = "Progress to CSIP",
  ),
  recordedBy = "FRED_ADM",
  recordedByDisplayName = "Fred Admin",
  recordedDate = LocalDate.parse("2024-04-08"),
  reasonForDecision = "There is a reason for the decision - it goes here",
)

fun nomisCSIPResponse(nomisCSIPId: Long = 1234, remainOnCSIP: Boolean = true, closeCSIP: Boolean = true) =
  CSIPResponse(
    id = nomisCSIPId,
    offender = Offender("A1234BC", firstName = "Fred", lastName = "Smith"),
    bookingId = 1214478L,
    type = CodeDescription(code = "INT", description = "Intimidation"),
    location = CodeDescription(code = "LIB", description = "Library"),
    areaOfWork = CodeDescription(code = "EDU", description = "Education"),
    reportedDate = LocalDate.parse("2024-04-04"),
    reportedBy = "JIM_ADM",
    proActiveReferral = true,
    staffAssaulted = true,
    staffAssaultedName = "Fred Jones",
    reportDetails = ReportDetails(
      factors = listOf(nomisCSIPFactor()),
      saferCustodyTeamInformed = false,
      referralComplete = true,
      referralCompletedBy = "JIM_ADM",
      referralCompletedByDisplayName = "",
      referralCompletedDate = LocalDate.parse("2024-04-04"),
      involvement = CodeDescription(code = "PER", description = "Perpetrator"),
      concern = "There was a worry about the offender",
      knownReasons = "known reasons details go in here",
      otherInformation = "other information goes in here",
      releaseDate = LocalDate.parse("2026-06-06"),
    ),
    saferCustodyScreening = nomisSCS(),
    investigation = InvestigationDetails(
      staffInvolved = "some people",
      evidenceSecured = "A piece of pipe",
      reasonOccurred = "bad behaviour",
      usualBehaviour = "Good person",
      trigger = "missed meal",
      protectiveFactors = "ensure taken to canteen",
      interviews = listOf(
        InterviewDetails(
          id = 3343,
          interviewee = "Bill Black",
          date = LocalDate.parse("2024-06-06"),
          role = CodeDescription(code = "WITNESS", description = "Witness"),
          createDateTime = "2024-04-04T15:12:32.00462",
          createdBy = "AA_ADM",
          createdByDisplayName = "ADAM SMITH",
          comments = "Saw a pipe in his hand",
          lastModifiedDateTime = "2024-08-12T11:32:15",
          lastModifiedBy = "BB_ADM",
          lastModifiedByDisplayName = "Bebe SMITH",
        ),
      ),
    ),
    decision = Decision(
      actions = Actions(
        openCSIPAlert = false,
        nonAssociationsUpdated = true,
        observationBook = true,
        unitOrCellMove = false,
        csraOrRsraReview = false,
        serviceReferral = true,
        simReferral = false,
      ),
      decisionOutcome = CodeDescription(code = "OPE", description = "Progress to Investigation"),
      recordedBy = "FRED_ADM",
      recordedByDisplayName = "Fred Admin",
      recordedDate = LocalDate.parse("2024-04-08"),
      otherDetails = "Some other info here",
      conclusion = "Offender needs help",
      signedOffRole = CodeDescription("CUSTMAN", description = "Custodial Manager"),

    ),
    plans = listOf(
      Plan(
        id = 65,
        identifiedNeed = "they need help",
        intervention = "dd",
        createdDate = LocalDate.parse("2024-04-16"),
        targetDate = LocalDate.parse("2024-08-20"),
        closedDate = LocalDate.parse("2024-04-17"),
        progression = "there was some improvement",
        referredBy = "Jason",
        createDateTime = "2024-03-16T11:32:15",
        createdBy = "PPLAN",
        createdByDisplayName = "Peter Plan",
      ),
    ),
    reviews = listOf(
      Review(
        id = 67,
        reviewSequence = 1,
        attendees = listOf(
          Attendee(
            id = 221,
            name = "same jones",
            role = "person",
            attended = true,
            contribution = "talked about things",
            createDateTime = "2024-08-20T10:33:48.946787",
            createdBy = "DBULL_ADM",
            createdByDisplayName = "DOM BULL",
          ),
        ),
        remainOnCSIP = remainOnCSIP,
        csipUpdated = false,
        caseNote = false,
        closeCSIP = closeCSIP,
        peopleInformed = false,
        closeDate = LocalDate.parse("2024-04-16"),
        recordedDate = LocalDate.parse("2024-04-01"),
        createdBy = "FJAMES",
        createDateTime = "2024-04-01T10:00:00",
        createdByDisplayName = "FRED JAMES",
        recordedBy = "JSMITH",
        recordedByDisplayName = "JOHN SMITH",
      ),
    ),
    documents = listOf(),
    createDateTime = "2024-04-01T10:32:12.867081",
    createdBy = "JSMITH",
    createdByDisplayName = "JSMITH",
    originalAgencyId = "MDI",
    logNumber = "ASI-001",
    incidentDate = LocalDate.parse("2024-06-12"),
    incidentTime = "10:32:12",
    caseManager = "C Jones",
    planReason = "helper",
    firstCaseReviewDate = LocalDate.parse("2024-04-15"),
  )
