package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.objectMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseIdentifierResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class CourtSentencingNomisApiMockServer {
  fun stubCourtCaseCreate(offenderNo: String, response: CreateCourtCaseResponse) {
    stubPost("/prisoners/$offenderNo/sentencing/court-cases", response = response)
  }

  fun stubCourtCaseCreateWithError(offenderNo: String, status: Int = 500) {
    stubPostWithError("/prisoners/$offenderNo/sentencing/court-cases", status)
  }

  fun stubCourtCaseCreateWithErrorFollowedBySlowSuccess(offenderNo: String, response: String) {
    nomisApi.stubFor(
      post("/prisoners/$offenderNo/sentencing/court-cases")
        .inScenario("Retry NOMIS Court Case Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause NOMIS Court Case Success"),
    )

    nomisApi.stubFor(
      post("/prisoners/$offenderNo/sentencing/court-cases")
        .inScenario("Retry NOMIS Court Case Scenario")
        .whenScenarioStateIs("Cause NOMIS Court Case Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(response)
            .withStatus(200)
            .withFixedDelay(1500),
        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubHealthPing(status: Int) {
    nomisApi.stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  fun stubGetCourtCase(
    caseId: Long,
    bookingId: Long = 2,
    offenderNo: String = "G4803UT",
    courtId: String = "BATHMC",
    caseInfoNumber: String? = "caseRef1",
    caseIndentifiers: List<CaseIdentifierResponse> = emptyList(),
    courtEvents: List<CourtEventResponse> = emptyList(),
    combinedCaseId: Long? = null,
    caseStatus: uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription = uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription("A", "Active"),
    beginDate: LocalDate = LocalDate.now(),
    response: CourtCaseResponse = CourtCaseResponse(
      bookingId = bookingId,
      id = caseId,
      offenderNo = offenderNo,
      caseSequence = 22,
      caseStatus = caseStatus,
      legalCaseType = uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription("A", "Adult"),
      courtId = "MDI",
      courtEvents = courtEvents,
      offenderCharges = emptyList(),
      createdDateTime = LocalDateTime.now(),
      createdByUsername = "Q1251T",
      lidsCaseNumber = 1,
      primaryCaseInfoNumber = "caseRef1",
      caseInfoNumbers = caseIndentifiers,
      combinedCaseId = combinedCaseId,
      beginDate = beginDate,
      sentences = emptyList(),
    ),
  ) {
    stubGet("/court-cases/$caseId", response)
  }

  fun stubGetCourtCasesByOffenderNo(
    offenderNo: String = "G4803UT",
    response: List<CourtCaseResponse> = emptyList(),
  ) {
    stubGet("/prisoners/$offenderNo/sentencing/court-cases", response)
  }

  private fun stubGet(url: String, response: Any) = nomisApi.stubFor(
    get(url).willReturn(
      aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(objectMapper().writeValueAsString(response))
        .withStatus(200),
    ),
  )

  private fun stubGetWithError(url: String, status: Int = 500) = nomisApi.stubFor(
    get(url).willReturn(
      aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(
          """
              {
                "error": "some error"
              }
          """.trimIndent(),
        )
        .withStatus(status),
    ),
  )

  private fun stubPostWithError(url: String, status: Int = 500) = nomisApi.stubFor(
    post(url).willReturn(
      aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(
          """
              {
                "error": "some error"
              }
          """.trimIndent(),
        )
        .withStatus(status),
    ),
  )

  private fun stubPost(url: String, response: Any) = nomisApi.stubFor(
    post(url).willReturn(
      aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(objectMapper().writeValueAsString(response))
        .withStatus(201),
    ),
  )
}
