package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.Attendee
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.ContributoryFactor
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.CsipRecord
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.DecisionAndActions
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.IdentifiedNeed
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.Interview
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.Investigation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.Plan
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.ReferenceData
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.Referral
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.Review
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.csip.model.SaferCustodyScreeningOutcome
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import java.time.LocalDate
import java.util.UUID

class CSIPDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val csipDpsApi = CSIPDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
    csipDpsApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    csipDpsApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    csipDpsApi.stop()
  }
}

class CSIPDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8098
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

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder {
    this.withBody(CSIPDpsApiExtension.jsonMapper.writeValueAsString(body))
    return this
  }

  fun stubGetCsipReport(dpsCsipReport: CsipRecord = dpsCsipRecordMinimal()) {
    stubFor(
      get(urlMatching("/csip-records/\\S+"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(dpsCsipReport)
            .withStatus(200),
        ),
    )
  }

  fun stubGetCSIPsForPrisoner(offenderNo: String, vararg csips: CsipRecord) {
    stubFor(
      get(urlPathEqualTo("/sync/csip-records/$offenderNo"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              csips.toList(),
            )
            .withStatus(200),
        ),
    )
  }

  fun stubGetCSIPsForPrisoner(offenderNo: String, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    stubFor(
      get(urlPathEqualTo("/sync/csip-records/$offenderNo"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(error),
        ),
    )
  }
}

fun dpsCsipRecordMinimal() = CsipRecord(
  recordUuid = UUID.fromString("8cdadcf3-b003-4116-9956-c99bd8df6a00"),
  prisonNumber = "A1234KT",
  prisonCodeWhenRecorded = "ASI",
  status = ReferenceData(code = "CSIP_OPEN"),
  referral = Referral(
    incidentDate = LocalDate.parse("2024-08-09"),
    incidentType = ReferenceData(code = "INT"),
    incidentLocation = ReferenceData(code = "LIB"),
    referredBy = "JIM_ADM",
    referralDate = LocalDate.parse("2024-10-01"),
    refererArea = ReferenceData(code = "EDU"),
    isSaferCustodyTeamInformed = Referral.IsSaferCustodyTeamInformed.NO,
    contributoryFactors = listOf(),
  ),
)

fun dpsCsipRecord(
  incidentType: String = "INT",
  scsOutcomeCode: String = "CUR",
  reviewOutcome: Set<Review.Actions> = setOf(Review.Actions.REMAIN_ON_CSIP, Review.Actions.CLOSE_CSIP),
  decisionSignedOffRole: String = "CUSTMAN",
) = CsipRecord(
  recordUuid = UUID.fromString("8cdadcf3-b003-4116-9956-c99bd8df6a00"),
  prisonNumber = "A1234KT",
  status = ReferenceData(code = "CSIP_OPEN"),
  referral = Referral(
    incidentDate = LocalDate.parse("2024-06-12"),
    incidentType = ReferenceData(code = incidentType),
    incidentLocation = ReferenceData(code = "LIB"),
    referredBy = "JIM_ADM",
    referralDate = LocalDate.parse("2024-10-01"),
    refererArea = ReferenceData(code = "EDU"),
    isSaferCustodyTeamInformed = Referral.IsSaferCustodyTeamInformed.NO,

    incidentTime = "10:32:12",
    isProactiveReferral = true,
    isStaffAssaulted = true,
    assaultedStaffName = "Fred Jones",
    incidentInvolvement = ReferenceData(code = "PER"),
    descriptionOfConcern = "There was a worry about the offender",
    knownReasons = "known reasons details go in here",
    otherInformation = "other information goes in here",
    isReferralComplete = true,
    referralCompletedDate = LocalDate.parse("2024-04-04"),
    referralCompletedBy = "JIM_ADM",
    referralCompletedByDisplayName = "",
    saferCustodyScreeningOutcome = SaferCustodyScreeningOutcome(
      outcome = ReferenceData(scsOutcomeCode),
      reasonForDecision = "There is a reason for the decision - it goes here",
      date = LocalDate.parse("2024-04-08"),
      recordedBy = "FRED_ADM",
      recordedByDisplayName = "Fred Admin",
      history = listOf(),
    ),

    investigation = Investigation(
      interviews = listOf(
        Interview(
          interviewUuid = UUID.fromString("8cdadcf3-b003-4116-9956-c99bd8df6222"),
          interviewee = "Bill Black",
          interviewDate = LocalDate.parse("2024-06-06"),
          intervieweeRole = ReferenceData("WITNESS"),
          interviewText = "Saw a pipe in his hand",
        ),
      ),
      staffInvolved = "some people",
      evidenceSecured = "A piece of pipe",
      occurrenceReason = "bad behaviour",
      personsUsualBehaviour = "Good person",
      personsTrigger = "missed meal",
      protectiveFactors = "ensure taken to canteen",
    ),
    decisionAndActions =
    DecisionAndActions(
      outcome = ReferenceData("OPE"),
      actions = setOf(
        DecisionAndActions.Actions.NON_ASSOCIATIONS_UPDATED,
        DecisionAndActions.Actions.OBSERVATION_BOOK,
        DecisionAndActions.Actions.SERVICE_REFERRAL,
      ),
      conclusion = "Offender needs help",
      signedOffByRole = ReferenceData(decisionSignedOffRole),
      date = LocalDate.parse("2024-04-08"),
      recordedBy = "FRED_ADM",
      recordedByDisplayName = "Fred Admin",
      nextSteps = null,
      actionOther = "Some other info here",
      history = listOf(),
    ),

    contributoryFactors = listOf(
      ContributoryFactor(
        factorUuid = UUID.fromString("8cdadcf3-b003-4116-9956-c99bd8df6111"),
        factorType = ReferenceData("BUL"),
        comment = "Offender causes trouble",
      ),
    ),
  ),
  logCode = "ASI-001",
  prisonCodeWhenRecorded = "MDI",

  plan = Plan(
    caseManager = "C Jones",
    reasonForPlan = "helper",
    firstCaseReviewDate = LocalDate.parse("2024-04-15"),
    identifiedNeeds = listOf(
      IdentifiedNeed(
        identifiedNeedUuid = UUID.fromString("8cdadcf3-b003-4116-9956-c99bd8df6333"),
        identifiedNeed = "they need help",
        responsiblePerson = "Jason",
        intervention = "dd",
        progression = "there was some improvement",
        targetDate = LocalDate.parse("2024-08-20"),
        closedDate = LocalDate.parse("2024-04-17"),
        createdDate = LocalDate.parse("2024-04-16"),
      ),
    ),
    reviews = listOf(
      Review(
        reviewUuid = UUID.fromString("8cdadcf3-b003-4116-9956-c99bd8df6444"),
        reviewSequence = 7,
        reviewDate = LocalDate.parse("2024-04-01"),
        nextReviewDate = null,
        csipClosedDate = LocalDate.parse("2024-04-16"),
        summary = null,
        recordedBy = "JSMITH",
        recordedByDisplayName = "JOHN SMITH",

        actions = reviewOutcome,
        attendees = listOf(
          Attendee(
            attendeeUuid = UUID.fromString("8cdadcf3-b003-4116-9956-c99bd8df6555"),
            name = "sam jones",
            role = "person",
            isAttended = true,
            contribution = "talked about things",
          ),
        ),
      ),
    ),
  ),
)
