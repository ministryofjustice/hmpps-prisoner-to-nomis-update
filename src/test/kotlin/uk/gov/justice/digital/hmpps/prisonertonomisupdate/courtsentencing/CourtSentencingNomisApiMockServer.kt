package uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtsentencing.CourtSentencingApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingCourtCaseCloneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseIdentifierResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ConvertToRecallResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtAppearanceRepairRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtCaseRepairRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtCaseRepairResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtEventChargeRepairRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CourtEventResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCourtAppearanceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateCourtCaseResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateSentenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateSentenceTermResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderChargeIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderChargeRepairRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.SentenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateCourtAppearanceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class CourtSentencingNomisApiMockServer {
  companion object {
    fun courtCaseRepairRequest(): CourtCaseRepairRequest {
      val appearance = CourtAppearanceRepairRequest(
        eventDateTime = LocalDateTime.now(),
        courtEventType = "SENT",
        courtId = "MDI",
        courtEventCharges = listOf(
          CourtEventChargeRepairRequest(
            id = "1",
          ),
        ),
      )
      val charge = OffenderChargeRepairRequest(
        id = "1",
        offenceCode = "RS86000",
      )
      return CourtCaseRepairRequest(
        startDate = LocalDate.now(),
        legalCaseType = "A",
        courtId = "MDI",
        status = "A",
        courtAppearances = listOf(appearance),
        offenderCharges = listOf(charge),
      )
    }
  }

  fun courtCaseRepairResponse(): CourtCaseRepairResponse = CourtCaseRepairResponse(
    caseId = 1234,
    courtAppearanceIds = emptyList(),
    offenderChargeIds = emptyList(),
    bookingId = 54321,
  )

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
    sentences: List<SentenceResponse> = emptyList(),
    combinedCaseId: Long? = null,
    caseStatus: CodeDescription = CodeDescription("A", "Active"),
    beginDate: LocalDate = LocalDate.now(),
    response: CourtCaseResponse = CourtCaseResponse(
      bookingId = bookingId,
      id = caseId,
      offenderNo = offenderNo,
      caseSequence = 22,
      caseStatus = caseStatus,
      legalCaseType = CodeDescription("A", "Adult"),
      courtId = "MDI",
      courtEvents = courtEvents,
      offenderCharges = emptyList(),
      createdDateTime = LocalDateTime.now(),
      createdByUsername = "Q1251T",
      primaryCaseInfoNumber = "caseRef1",
      caseInfoNumbers = caseIndentifiers,
      combinedCaseId = combinedCaseId,
      sourceCombinedCaseIds = emptyList(),
      beginDate = beginDate,
      sentences = sentences,
    ),
  ) {
    stubGet("/court-cases/$caseId", response)
  }

  fun stubGetCourtCaseForReconciliation(
    caseId: Long,
    response: CourtCaseResponse,
  ) {
    stubGet("/court-cases/$caseId", response)
  }

  fun stubGetCourtCaseIdsByOffenderNo(
    offenderNo: String = "G4803UT",
    response: List<Long> = emptyList(),
  ) {
    stubGet("/prisoners/$offenderNo/sentencing/court-cases/ids", response)
  }

  fun stubCourtAppearanceCreate(offenderNo: String, courtCaseId: Long, response: CreateCourtAppearanceResponse) {
    stubPost("/prisoners/$offenderNo/sentencing/court-cases/$courtCaseId/court-appearances", response = response)
  }

  fun stubCourtAppearanceUpdate(offenderNo: String, courtCaseId: Long, courtAppearanceId: Long, response: UpdateCourtAppearanceResponse) {
    stubPutWithResponse("/prisoners/$offenderNo/sentencing/court-cases/$courtCaseId/court-appearances/$courtAppearanceId", response)
  }

  fun stubCourtChargeCreate(offenderNo: String, courtCaseId: Long, response: OffenderChargeIdResponse) {
    stubPost("/prisoners/$offenderNo/sentencing/court-cases/$courtCaseId/charges", response = response)
  }

  fun stubCourtChargeUpdate(offenderChargeId: Long, courtAppearanceId: Long, offenderNo: String, courtCaseId: Long) {
    stubPut("/prisoners/$offenderNo/sentencing/court-cases/$courtCaseId/court-appearances/$courtAppearanceId/charges/$offenderChargeId")
  }

  fun stubCourtChargeCreateWithError(offenderNo: String, courtCaseId: Long, status: Int = 500) {
    stubPostWithError("/prisoners/$offenderNo/sentencing/court-cases/{caseId}/charges", status)
  }

  fun stubCourtCaseDelete(offenderNo: String, nomisCourtCaseId: Long) {
    stubDelete("/prisoners/$offenderNo/sentencing/court-cases/$nomisCourtCaseId")
  }

  fun stubCourtAppearanceDelete(offenderNo: String, nomisCourtCaseId: Long, nomisEventId: Long) {
    stubDelete("/prisoners/$offenderNo/sentencing/court-cases/$nomisCourtCaseId/court-appearances/$nomisEventId")
  }

  fun stubCaseReferenceRefresh(offenderNo: String, courtCaseId: Long) {
    nomisApi.stubFor(
      post("/prisoners/$offenderNo/sentencing/court-cases/$courtCaseId/case-identifiers").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubSentenceCreate(offenderNo: String, caseId: Long, response: CreateSentenceResponse) {
    stubPost("/prisoners/$offenderNo/court-cases/$caseId/sentences", response = response)
  }

  fun stubSentenceUpdate(offenderNo: String, caseId: Long, sentenceSeq: Long) {
    stubPut("/prisoners/$offenderNo/court-cases/$caseId/sentences/$sentenceSeq")
  }

  fun stubSentenceDelete(offenderNo: String, caseId: Long, sentenceSeq: Long) {
    stubDelete("/prisoners/$offenderNo/court-cases/$caseId/sentences/$sentenceSeq")
  }

  fun stubSentenceTermCreate(offenderNo: String, caseId: Long, response: CreateSentenceTermResponse, sentenceSeq: Long) {
    stubPost("/prisoners/$offenderNo/court-cases/$caseId/sentences/$sentenceSeq/sentence-terms", response = response)
  }

  fun stubSentenceTermUpdate(offenderNo: String, caseId: Long, sentenceSeq: Long, termSeq: Long) {
    stubPut("/prisoners/$offenderNo/court-cases/$caseId/sentences/$sentenceSeq/sentence-terms/$termSeq")
  }

  fun stubSentenceTermDelete(offenderNo: String, caseId: Long, sentenceSeq: Long, termSeq: Long) {
    stubDelete("/prisoners/$offenderNo/court-cases/$caseId/sentences/$sentenceSeq/sentence-terms/$termSeq")
  }

  fun stubRecallSentences(offenderNo: String, response: ConvertToRecallResponse = ConvertToRecallResponse(courtEventIds = emptyList(), sentenceAdjustmentsActivated = emptyList())) {
    nomisApi.stubFor(
      post("/prisoners/$offenderNo/sentences/recall").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200)
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }
  fun stubUpdateRecallSentences(offenderNo: String) {
    nomisApi.stubFor(
      put("/prisoners/$offenderNo/sentences/recall").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubRevertRecallSentences(offenderNo: String) {
    nomisApi.stubFor(
      put("/prisoners/$offenderNo/sentences/recall/restore-previous").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubDeleteRecallSentences(offenderNo: String) {
    nomisApi.stubFor(
      put("/prisoners/$offenderNo/sentences/recall/restore-original").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubCloneCourtCase(
    offenderNo: String,
    courtCaseId: Long,
    response: BookingCourtCaseCloneResponse = BookingCourtCaseCloneResponse(
      courtCases = emptyList(),
      sentenceAdjustments = emptyList(),
    ),
  ) {
    stubPost("/prisoners/$offenderNo/sentencing/court-cases/clone/$courtCaseId", response = response)
  }

  fun stubRepairCourtCase(
    offenderNo: String,
    courtCaseId: Long,
    response: CourtCaseRepairResponse = courtCaseRepairResponse(),
  ) {
    stubPost("/prisoners/$offenderNo/sentencing/court-cases/$courtCaseId/repair", response = response)
  }

  // helper methods

  private fun stubGet(url: String, response: Any) = nomisApi.stubFor(
    get(url).willReturn(
      aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(jsonMapper.writeValueAsString(response))
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
        .withBody(jsonMapper.writeValueAsString(response))
        .withStatus(201),
    ),
  )

  private fun stubPut(url: String) = nomisApi.stubFor(
    put(url).willReturn(
      aResponse()
        .withHeader("Content-Type", "application/json")
        .withStatus(200),
    ),
  )

  private fun stubPutWithResponse(url: String, response: Any) = nomisApi.stubFor(
    put(url).willReturn(
      aResponse()
        .withHeader("Content-Type", "application/json")
        .withBody(jsonMapper.writeValueAsString(response))
        .withStatus(200),
    ),
  )

  private fun stubDelete(url: String) = nomisApi.stubFor(
    delete(url).willReturn(
      aResponse()
        .withHeader("Content-Type", "application/json")
        .withStatus(204),
    ),
  )

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
  fun postCountFor(url: String) = nomisApi.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo(url))).count()
  fun putCountFor(url: String) = nomisApi.findAll(WireMock.putRequestedFor(WireMock.urlEqualTo(url))).count()
}
