package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.CombinedOutcomeDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.HearingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.HearingOutcomeDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.IncidentDetailsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.IncidentRoleDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.IncidentStatementDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OffenceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OffenceRuleDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OutcomeDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.OutcomeHistoryDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.PunishmentDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedAdjudicationDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedAdjudicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedDamageDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications.model.ReportedEvidenceDto
import java.time.LocalDateTime
import java.util.UUID

class AdjudicationsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val adjudicationsApiServer = AdjudicationsApiMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
    adjudicationsApiServer.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    adjudicationsApiServer.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    adjudicationsApiServer.stop()
  }
}

class AdjudicationsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8089
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

  fun stubChargeGet(
    chargeNumber: String,
    offenderNo: String = "A7937DY",
    damages: List<ReportedDamageDto> = listOf(),
    evidence: List<ReportedEvidenceDto> = listOf(),
    outcomes: List<OutcomeHistoryDto> = listOf(),
    punishments: List<PunishmentDto> = listOf(),
    status: String = "UNSCHEDULED",
    hearingId: Long = 345,
    hearingLocationUuid: UUID = UUID.randomUUID(),
    incidentLocationUuid: UUID = UUID.randomUUID(),
    hearings: List<HearingDto> = listOf(
      HearingDto(
        id = hearingId,
        locationUuid = hearingLocationUuid,
        dateTimeOfHearing = LocalDateTime.parse("2023-08-23T14:25:00"),
        oicHearingType = HearingDto.OicHearingType.GOV_ADULT,
        agencyId = "MDI",
        outcome = HearingOutcomeDto(
          id = 962,
          adjudicator = "JBULLENGEN",
          code = HearingOutcomeDto.Code.COMPLETE,
          plea = HearingOutcomeDto.Plea.GUILTY,
        ),
      ),
    ),
  ) {
    val adjudicationDto = ReportedAdjudicationDto(
      chargeNumber = chargeNumber,
      prisonerNumber = offenderNo,
      gender = ReportedAdjudicationDto.Gender.MALE,
      incidentDetails = IncidentDetailsDto(
        locationUuid = incidentLocationUuid,
        dateTimeOfIncident = LocalDateTime.parse("2023-07-11T09:00:00"),
        dateTimeOfDiscovery = LocalDateTime.parse("2023-07-11T09:00:00"),
        handoverDeadline = LocalDateTime.parse("2023-07-13T09:00:00"),
      ),
      isYouthOffender = false,
      incidentRole = IncidentRoleDto(),
      offenceDetails = OffenceDto(
        offenceCode = 16001,
        offenceRule = OffenceRuleDto(
          paragraphNumber = "1",
          paragraphDescription = "Commits any assault",
          nomisCode = "51:1B",
        ),
        protectedCharacteristics = emptyList(),
      ),
      incidentStatement = IncidentStatementDto(
        statement = "12",
        completed = true,
      ),
      createdByUserId = "TWRIGHT",
      createdDateTime = LocalDateTime.parse("2023-07-25T15:19:37"),
      status = ReportedAdjudicationDto.Status.valueOf(status),
      reviewedByUserId = "AMARKE_GEN",
      statusReason = "",
      statusDetails = "",
      damages = damages,
      evidence = evidence,
      witnesses = emptyList(),
      hearings = hearings,
      disIssueHistory = emptyList(),
      outcomes = outcomes,
      punishments = punishments,
      punishmentComments = emptyList(),
      linkedChargeNumbers = emptyList(),
      outcomeEnteredInNomis = false,
      originatingAgencyId = "MDI",
      canActionFromHistory = false,
    )
    stubFor(
      get("/reported-adjudications/$chargeNumber/v2").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(AdjudicationsApiExtension.jsonMapper.writeValueAsString(ReportedAdjudicationResponse(reportedAdjudication = adjudicationDto)))
          .withStatus(200),
      ),
    )
  }

  fun stubChargeGetWithCompletedOutcome(
    hearingId: Long = 123,
    chargeNumber: String,
    offenderNo: String = "A7937DY",
    outcomeFindingCode: String = "CHARGE_PROVED",
  ) {
    val outcomes = listOf(
      OutcomeHistoryDto(
        hearing = HearingDto(
          id = hearingId,
          locationUuid = UUID.randomUUID(),
          dateTimeOfHearing = LocalDateTime.parse("2023-04-27T17:45:00"),
          oicHearingType = HearingDto.OicHearingType.GOV_ADULT,
          outcome = HearingOutcomeDto(
            id = 407,
            adjudicator = "SWATSON_GEN",
            code = HearingOutcomeDto.Code.COMPLETE,
            plea = HearingOutcomeDto.Plea.GUILTY,
          ),
          agencyId = "MDI",
        ),
        outcome = CombinedOutcomeDto(
          outcome = OutcomeDto(
            id = 591,
            code = OutcomeDto.Code.valueOf(outcomeFindingCode),
            details = null,
            canRemove = true,
          ),
        ),
      ),
    )
    stubChargeGet(chargeNumber = chargeNumber, offenderNo = offenderNo, outcomes = outcomes)
  }

  fun stubChargeGetWithHearingAndReferIndependentAdjudicatorOutcome(
    hearingId: Long = 123,
    chargeNumber: String,
    offenderNo: String = "A7937DY",
  ) {
    stubChargeGetWithHearingAndSeparateOutcomeBlock(
      hearingId = hearingId,
      outcomeCode = "REFER_INAD",
      chargeNumber = chargeNumber,
      offenderNo = offenderNo,
    )
  }

  fun stubChargeGetWithHearingAndReferPoliceOutcome(
    hearingId: Long = 123,
    chargeNumber: String,
    offenderNo: String = "A7937DY",
  ) {
    stubChargeGetWithHearingAndSeparateOutcomeBlock(
      hearingId = hearingId,
      outcomeCode = "REFER_POLICE",
      chargeNumber = chargeNumber,
      offenderNo = offenderNo,
    )
  }

  fun stubChargeGetWithHearingAndNotProceedOutcome(
    hearingId: Long = 123,
    chargeNumber: String,
    offenderNo: String = "A7937DY",
  ) {
    stubChargeGetWithHearingAndSeparateOutcomeBlock(
      hearingId = hearingId,
      outcomeCode = "NOT_PROCEED",
      hearingOutcomeCode = "COMPLETE",
      chargeNumber = chargeNumber,
      offenderNo = offenderNo,
    )
  }

  private fun stubChargeGetWithHearingAndSeparateOutcomeBlock(
    outcomeCode: String,
    hearingOutcomeCode: String? = null,
    hearingId: Long = 123,
    chargeNumber: String,
    offenderNo: String = "A7937DY",
  ) {
    val outcomes = listOf(
      OutcomeHistoryDto(
        hearing = HearingDto(
          id = hearingId,
          locationUuid = UUID.randomUUID(),
          dateTimeOfHearing = LocalDateTime.parse("2023-10-04T13:20:00"),
          oicHearingType = HearingDto.OicHearingType.GOV_ADULT,
          outcome = HearingOutcomeDto(
            id = 975,
            adjudicator = "JBULLENGEN",
            code = hearingOutcomeCode ?.let { HearingOutcomeDto.Code.valueOf(it) } ?: HearingOutcomeDto.Code.valueOf(outcomeCode),
            details = "pdfs",
          ),
          agencyId = "MDI",
        ),
        outcome = CombinedOutcomeDto(
          outcome = OutcomeDto(
            id = 1238,
            code = OutcomeDto.Code.valueOf(outcomeCode),
            details = "pdfs",
            canRemove = true,
          ),
        ),
      ),

    )
    stubChargeGet(chargeNumber = chargeNumber, offenderNo = offenderNo, outcomes = outcomes)
  }

  fun stubChargeGetWithReferralOutcome(
    outcomeCode: String = "REFER_POLICE",
    referralOutcomeCode: String = "PROSECUTION",
    hearingId: Long = 123,
    chargeNumber: String,
    offenderNo: String = "A7937DY",
    hearingType: String = "INAD_ADULT",
  ) {
    val outcomes = listOf(
      OutcomeHistoryDto(
        hearing = HearingDto(
          id = hearingId,
          locationUuid = UUID.randomUUID(),
          dateTimeOfHearing = LocalDateTime.parse("2023-10-12T14:00:00"),
          oicHearingType = HearingDto.OicHearingType.valueOf(hearingType),
          outcome = HearingOutcomeDto(
            id = 1031,
            adjudicator = "jack_b",
            code = HearingOutcomeDto.Code.valueOf(outcomeCode),
            details = "yuiuy",
          ),
          agencyId = "MDI",
        ),
        outcome = CombinedOutcomeDto(
          outcome = OutcomeDto(
            id = 1319,
            code = OutcomeDto.Code.valueOf(outcomeCode),
            details = "yuiuy",
            canRemove = true,
          ),
          referralOutcome = OutcomeDto(
            id = 1320,
            code = OutcomeDto.Code.valueOf(referralOutcomeCode),
            canRemove = true,
          ),
        ),
      ),

    )
    stubChargeGet(chargeNumber = chargeNumber, offenderNo = offenderNo, outcomes = outcomes)
  }

  // no separate outcome block for Adjourn
  fun stubChargeGetWithAdjournOutcome(hearingId: Long = 123, chargeNumber: String, offenderNo: String = "A7937DY") {
    val outcomes = listOf(
      OutcomeHistoryDto(
        hearing = HearingDto(
          id = hearingId,
          locationUuid = UUID.randomUUID(),
          dateTimeOfHearing = LocalDateTime.parse("2023-10-04T13:20:00"),
          oicHearingType = HearingDto.OicHearingType.GOV_ADULT,
          outcome = HearingOutcomeDto(
            id = 976,
            adjudicator = "JBULLENGEN",
            code = HearingOutcomeDto.Code.ADJOURN,
            reason = HearingOutcomeDto.Reason.RO_ATTEND,
            details = "cxvcx",
            plea = HearingOutcomeDto.Plea.UNFIT,
          ),
          agencyId = "MDI",
        ),
        outcome = null,
      ),

    )
    stubChargeGet(chargeNumber = chargeNumber, offenderNo = offenderNo, outcomes = outcomes)
  }

  fun stubChargeGetWithPoliceReferral(chargeNumber: String, offenderNo: String = "A7937DY") {
    val outcomes = listOf(
      OutcomeHistoryDto(
        hearing = null,
        outcome = CombinedOutcomeDto(
          outcome =
          OutcomeDto(
            id = 1411,
            code = OutcomeDto.Code.REFER_POLICE,
            details = "eewr",
            canRemove = true,
          ),
        ),
      ),

    )
    stubChargeGet(chargeNumber = chargeNumber, offenderNo = offenderNo, outcomes = outcomes)
  }

  fun stubChargeGetWithNotProceedReferral(chargeNumber: String, offenderNo: String = "A7937DY") {
    val outcomes = listOf(
      OutcomeHistoryDto(
        hearing = null,
        outcome = CombinedOutcomeDto(
          outcome =
          OutcomeDto(
            id = 1412,
            code = OutcomeDto.Code.NOT_PROCEED,
            details = "dssds",
            reason = OutcomeDto.Reason.EXPIRED_NOTICE,
            canRemove = true,
          ),
        ),
      ),

    )
    stubChargeGet(chargeNumber = chargeNumber, offenderNo = offenderNo, outcomes = outcomes)
  }

  fun stubChargeGetWithHearingFollowingReferral(chargeNumber: String, offenderNo: String = "A7937DY") {
    val outcomes = listOf(
      OutcomeHistoryDto(
        outcome = CombinedOutcomeDto(
          outcome = OutcomeDto(
            id = 1492,
            code = OutcomeDto.Code.REFER_POLICE,
            details = "fdggre",
            canRemove = true,
          ),
          referralOutcome = OutcomeDto(
            id = 1493,
            code = OutcomeDto.Code.SCHEDULE_HEARING,
            canRemove = true,
          ),
        ),
      ),
      OutcomeHistoryDto(
        hearing = HearingDto(
          id = 816,
          locationUuid = UUID.randomUUID(),
          dateTimeOfHearing = LocalDateTime.parse("2023-10-26T16:10:00"),
          oicHearingType = HearingDto.OicHearingType.INAD_ADULT,
          agencyId = "MDI",
        ),
      ),
    )
    stubChargeGet(chargeNumber = chargeNumber, offenderNo = offenderNo, outcomes = outcomes)
  }

  fun stubChargeGetWithError(chargeNumber: String, status: Int) {
    stubFor(
      get("/reported-adjudications/$chargeNumber/v2").willReturn(
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
  }

  fun stubGetAdjudicationsByBookingId(bookingId: Long, adjudications: List<ReportedAdjudicationDto> = emptyList()) {
    stubFor(
      get(
        WireMock.urlPathEqualTo("/reported-adjudications/all-by-booking/$bookingId"),
      )
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(adjudications),
        ),
    )
  }

  fun stubGetAdjudicationsByBookingIdWithError(bookingId: Long, status: Int) {
    stubFor(
      get(
        WireMock.urlPathEqualTo("/reported-adjudications/all-by-booking/$bookingId"),
      ).willReturn(
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
  }

  fun getCountFor(url: String) = this.findAll(WireMock.getRequestedFor(WireMock.urlEqualTo(url))).count()

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder {
    this.withBody(AdjudicationsApiExtension.jsonMapper.writeValueAsString(body))
    return this
  }
}
