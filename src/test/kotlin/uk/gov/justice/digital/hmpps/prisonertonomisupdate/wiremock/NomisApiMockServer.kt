package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AdjudicationADAAwardSummaryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.MergeDetail
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.Prison
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerIds
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.SentencingAdjustmentsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import kotlin.String
import kotlin.collections.List

class NomisApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val nomisApi = NomisApiMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
    nomisApi.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    nomisApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    nomisApi.stop()
  }
}

class NomisApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8082
    private const val ERROR_RESPONSE = """{ "status": 500, "error": "some error" }"""
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

  fun stubCheckAgencySwitchForPrisoner(serviceCode: String = "VISIT_ALLOCATION", prisonNumber: String = "A1234BC") {
    stubFor(
      get(urlPathEqualTo("/agency-switches/$serviceCode/prisoner/$prisonNumber")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }
  fun stubCheckAgencySwitchForPrisonerNotFound(serviceCode: String = "VISIT_ALLOCATION", prisonNumber: String = "A1234BC") {
    stubFor(
      get(urlPathEqualTo("/agency-switches/$serviceCode/prisoner/$prisonNumber")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NOT_FOUND.value()),
      ),
    )
  }

  fun stubCheckAgencySwitchForAgency(serviceCode: String = "VISIT_ALLOCATION", agencyId: String = "MDI") {
    stubFor(
      get(urlPathEqualTo("/agency-switches/$serviceCode/agency/$agencyId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }
  fun stubCheckAgencySwitchForAgencyNotFound(serviceCode: String = "VISIT_ALLOCATION", agencyId: String = "MDI") {
    stubFor(
      get(urlPathEqualTo("/agency-switches/$serviceCode/agency/$agencyId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NOT_FOUND.value()),
      ),
    )
  }

  fun stubGetActivePrisons(response: List<Prison> = activePrisons()) {
    stubFor(
      get(urlPathEqualTo("/prisons")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }
  fun stubGetActivePrisons(status: HttpStatus) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/prisons"),
      )
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody("""{"message":"Error"}"""),
        ),
    )
  }

  // *************************************************** Visits **********************************************

  fun stubVisitCreate(prisonerId: String, response: String = CREATE_VISIT_RESPONSE) {
    stubFor(
      post("/prisoners/$prisonerId/visits").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(201),
      ),
    )
  }

  fun stubVisitCreateWithError(prisonerId: String, status: Int = 500) {
    stubFor(
      post("/prisoners/$prisonerId/visits").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubVisitCreateWithDuplicateError(prisonerId: String, nomisVisitId: Long) {
    stubFor(
      post("/prisoners/$prisonerId/visits").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            // language=JSON
            """
              { 
                "status": 409, 
                "userMessage": "Visit already exists $nomisVisitId",
                "developerMessage": "Visit already exists $nomisVisitId",
                "moreInfo": "$nomisVisitId"
              }
            """.trimIndent(),
          )
          .withStatus(409),
      ),
    )
  }

  fun stubVisitCancel(prisonerId: String, visitId: String = "1234") {
    stubFor(
      put("/prisoners/$prisonerId/visits/$visitId/cancel").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubVisitUpdate(prisonerId: String, visitId: String = "1234") {
    stubFor(
      put("/prisoners/$prisonerId/visits/$visitId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubVisitCancelWithError(prisonerId: String, visitId: String, status: Int = 500) {
    stubFor(
      put("/prisoners/$prisonerId/visits/$visitId/cancel").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubVisitUpdateWithError(prisonerId: String, visitId: String, status: Int = 500) {
    stubFor(
      put("/prisoners/$prisonerId/visits/$visitId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  // *************************************************** Incentives **********************************************

  fun stubIncentiveCreate(bookingId: Long, response: String = CREATE_INCENTIVE_RESPONSE) {
    stubFor(
      post("/prisoners/booking-id/$bookingId/incentives").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(201),
      ),
    )
  }

  fun stubIncentiveCreateWithError(bookingId: Long, status: Int = 500) {
    stubFor(
      post("/prisoners/booking-id/$bookingId/incentives").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  // *************************************************** Activities **********************************************

  fun stubActivityCreate(response: String) {
    stubFor(
      post("/activities").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(201),
      ),
    )
  }

  fun stubActivityUpdate(nomisActivityId: Long, response: String) {
    stubFor(
      put("/activities/$nomisActivityId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200),
      ),
    )
  }

  fun stubActivityCreateWithError(status: Int = 500) {
    stubFor(
      post("/activities").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubActivityUpdateWithError(nomisActivityId: Long, status: Int = 500) {
    stubFor(
      put("/activities/$nomisActivityId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubActivityDelete(eventId: Long) {
    stubFor(
      delete("/activities/$eventId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubScheduleInstancesUpdate(nomisActivityId: Long) {
    stubFor(
      put("/activities/$nomisActivityId/schedules").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubScheduledInstanceUpdate(nomisActivityId: Long, response: String) {
    stubFor(
      put("/activities/$nomisActivityId/schedule").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200),
      ),
    )
  }

  fun stubScheduledInstanceUpdateWithError(nomisActivityId: Long, status: Int = 500) {
    stubFor(
      put("/activities/$nomisActivityId/schedule").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubAllocationUpsert(courseActivityId: Long) {
    stubFor(
      put("/activities/$courseActivityId/allocation").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "offenderProgramReferenceId": 1234, "created": true, "prisonId": "MDI" }""")
          .withStatus(200),
      ),
    )
  }

  fun stubAllocationUpsertWithError(courseActivityId: Long, status: Int = 500) {
    stubFor(
      put("/activities/$courseActivityId/allocation").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubUpsertAttendance(courseScheduleId: Long, bookingId: Long, response: String) {
    stubFor(
      put("/schedules/$courseScheduleId/booking/$bookingId/attendance").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200),
      ),
    )
  }

  fun stubUpsertAttendanceWithError(courseScheduleId: Long, bookingId: Long, status: Int = 500, body: String = ERROR_RESPONSE) {
    stubFor(
      put("/schedules/$courseScheduleId/booking/$bookingId/attendance").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(body)
          .withStatus(status),
      ),
    )
  }

  fun stubDeleteAttendance(courseScheduleId: Long, bookingId: Long) {
    stubFor(
      delete("/schedules/$courseScheduleId/booking/$bookingId/attendance").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubDeleteAttendanceWithError(courseScheduleId: Long, bookingId: Long, status: Int = 500, body: String = ERROR_RESPONSE) {
    stubFor(
      delete("/schedules/$courseScheduleId/booking/$bookingId/attendance").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(body)
          .withStatus(status),
      ),
    )
  }

  fun stubAllocationReconciliation(prisonId: String, response: String) {
    stubFor(
      get("/allocations/reconciliation/$prisonId")
        .willReturn(
          aResponse()
            .withHeader("Content-type", "application/json")
            .withBody(response)
            .withStatus(200),
        ),
    )
  }

  fun stubAllocationReconciliationWithError(prisonId: String, status: Int = 500) {
    stubFor(
      get("/allocations/reconciliation/$prisonId")
        .willReturn(
          aResponse()
            .withHeader("Content-type", "application/json")
            .withBody(ERROR_RESPONSE)
            .withStatus(status),
        ),
    )
  }

  fun stubSuspendedAllocationReconciliation(prisonId: String, response: String) {
    stubFor(
      get("/allocations/reconciliation/$prisonId?suspended=true")
        .willReturn(
          aResponse()
            .withHeader("Content-type", "application/json")
            .withBody(response)
            .withStatus(200),
        ),
    )
  }

  fun stubSuspendedAllocationReconciliationWithError(prisonId: String, status: Int = 500) {
    stubFor(
      get("/allocations/reconciliation/$prisonId?suspended=true")
        .willReturn(
          aResponse()
            .withHeader("Content-type", "application/json")
            .withBody(ERROR_RESPONSE)
            .withStatus(status),
        ),
    )
  }

  fun stubAttendanceReconciliation(prisonId: String, date: LocalDate, response: String) {
    stubFor(
      get("/attendances/reconciliation/$prisonId?date=$date")
        .willReturn(
          aResponse()
            .withHeader("Content-type", "application/json")
            .withBody(response)
            .withStatus(200),
        ),
    )
  }

  fun stubAttendanceReconciliationWithError(prisonId: String, date: LocalDate, status: Int = 500) {
    stubFor(
      get("/attendances/reconciliation/$prisonId?date=$date")
        .willReturn(
          aResponse()
            .withHeader("Content-type", "application/json")
            .withBody(ERROR_RESPONSE)
            .withStatus(status),
        ),
    )
  }

  fun stubGetServiceAgencies(serviceCode: String, response: String) {
    stubFor(
      get("/agency-switches/$serviceCode")
        .willReturn(
          aResponse()
            .withHeader("Content-type", "application/json")
            .withBody(response)
            .withStatus(200),
        ),
    )
  }

  fun stubGetServiceAgenciesWithError(serviceCode: String, status: Int = 500) {
    stubFor(
      get("/agency-switches/$serviceCode")
        .willReturn(
          aResponse()
            .withHeader("Content-type", "application/json")
            .withBody(ERROR_RESPONSE)
            .withStatus(status),
        ),
    )
  }

  fun stubGetActivitiesPrisonerDetails(response: String) {
    stubFor(
      post("/prisoners/bookings")
        .willReturn(
          aResponse()
            .withHeader("Content-type", "application/json")
            .withBody(response)
            .withStatus(200),
        ),
    )
  }

  fun stubGetPrisonerDetailsWithError(status: Int = 500) {
    stubFor(
      post("/prisoners/bookings")
        .willReturn(
          aResponse()
            .withHeader("Content-type", "application/json")
            .withBody(ERROR_RESPONSE)
            .withStatus(status),
        ),
    )
  }

  fun stubGetMaxCourseScheduleId(response: Long) {
    stubFor(
      get("/schedules/max-id")
        .willReturn(
          aResponse()
            .withHeader("Content-type", "application/json")
            .withBody(response)
            .withStatus(200),
        ),
    )
  }

  fun stubGetMaxCourseScheduleIdWithError(status: Int = 500) {
    stubFor(
      get("/schedules/max-id")
        .willReturn(
          aResponse()
            .withHeader("Content-type", "application/json")
            .withBody(ERROR_RESPONSE)
            .withStatus(status),
        ),
    )
  }

  // *************************************************** Appointments **********************************************

  fun stubAppointmentCreate(response: String) {
    stubFor(
      post("/appointments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(201),
      ),
    )
  }

  fun stubAppointmentCreateWithError(status: Int = 500) {
    stubFor(
      post("/appointments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubAppointmentCreateWithErrorFollowedBySlowSuccess(response: String) {
    stubFor(
      post("/appointments")
        .inScenario("Retry NOMIS Appointments Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause NOMIS Appointments Success"),
    )

    stubFor(
      post("/appointments")
        .inScenario("Retry NOMIS Appointments Scenario")
        .whenScenarioStateIs("Cause NOMIS Appointments Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(response)
            .withStatus(200)
            .withFixedDelay(1500),
        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubAppointmentUpdate(eventId: Long) {
    stubFor(
      put("/appointments/$eventId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubAppointmentUpdateWithError(eventId: Long, status: Int = 500) {
    stubFor(
      put("/appointments/$eventId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubAppointmentCancel(eventId: Long) {
    stubFor(
      put("/appointments/$eventId/cancel").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubAppointmentCancelWithError(eventId: Long, status: Int = 500) {
    stubFor(
      put("/appointments/$eventId/cancel").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubAppointmentCancelWithErrorFollowedBySlowSuccess(eventId: Long) {
    stubFor(
      put("/appointments/$eventId/cancel")
        .inScenario("Retry NOMIS Appointments Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause NOMIS Appointments Success"),
    )

    stubFor(
      put("/appointments/$eventId/cancel")
        .inScenario("Retry NOMIS Appointments Scenario")
        .whenScenarioStateIs("Cause NOMIS Appointments Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withFixedDelay(1500),
        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubAppointmentUncancel(eventId: Long) {
    stubFor(
      put("/appointments/$eventId/uncancel").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubAppointmentDelete(eventId: Long) {
    stubFor(
      delete("/appointments/$eventId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubAppointmentDeleteWithError(eventId: Long, status: Int = 500) {
    stubFor(
      delete("/appointments/$eventId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubAppointmentDeleteWithErrorFollowedBySlowSuccess(eventId: Long) {
    stubFor(
      delete("/appointments/$eventId")
        .inScenario("Retry NOMIS Appointments Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause NOMIS Appointments Success"),
    )

    stubFor(
      delete("/appointments/$eventId")
        .inScenario("Retry NOMIS Appointments Scenario")
        .whenScenarioStateIs("Cause NOMIS Appointments Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withFixedDelay(1500),
        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetAppointmentIds() {
    stubFor(
      get(
        urlPathEqualTo("/appointments/ids"),
      )
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              """
          {
              "content": [ { "eventId": 123456789 } ],
              "pageable": {
                  "sort": { "empty": false,  "sorted": true, "unsorted": false },
                  "offset": 0,
                  "pageSize": 10,
                  "pageNumber": 0,
                  "paged": true,
                  "unpaged": false
              },
              "last": false,
              "totalPages": 5,
              "totalElements": 41,
              "size": 10,
              "number": 0,
              "sort": { "empty": false,  "sorted": true, "unsorted": false },
              "first": true,
              "numberOfElements": 1,
              "empty": false
          }                
              """.trimIndent(),
            ),
        ),
    )
  }

  // *************************************************** Sentences **********************************************

  fun stubSentenceAdjustmentCreate(bookingId: Long, sentenceSequence: Long, adjustmentId: Long = 99L) {
    stubFor(
      post("/prisoners/booking-id/$bookingId/sentences/$sentenceSequence/adjustments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{"id": $adjustmentId}""")
          .withStatus(201),
      ),
    )
  }

  fun stubSentenceAdjustmentCreateWithError(bookingId: Long, sentenceSequence: Long, status: Int) {
    stubFor(
      post("/prisoners/booking-id/$bookingId/sentences/$sentenceSequence/adjustments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubSentenceAdjustmentUpdateWithError(adjustmentId: Long, status: Int) {
    stubFor(
      put("/sentence-adjustments/$adjustmentId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubSentenceAdjustmentCreateWithErrorFollowedBySlowSuccess(
    bookingId: Long,
    sentenceSequence: Long,
    adjustmentId: Long = 99L,
  ) {
    stubFor(
      post("/prisoners/booking-id/$bookingId/sentences/$sentenceSequence/adjustments")
        .inScenario("Retry NOMIS Adjustments Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause NOMIS Adjustments Success"),
    )

    stubFor(
      post("/prisoners/booking-id/$bookingId/sentences/$sentenceSequence/adjustments")
        .inScenario("Retry NOMIS Adjustments Scenario")
        .whenScenarioStateIs("Cause NOMIS Adjustments Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
                { "id": $adjustmentId }
                """,
            )
            .withStatus(200)
            .withFixedDelay(1500),
        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubSentenceAdjustmentUpdate(adjustmentId: Long) {
    stubFor(
      put("/sentence-adjustments/$adjustmentId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubSentenceAdjustmentDelete(adjustmentId: Long) {
    stubFor(
      delete("/sentence-adjustments/$adjustmentId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubSentenceAdjustmentDeleteWithError(adjustmentId: Long, status: Int) {
    stubFor(
      delete("/sentence-adjustments/$adjustmentId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubKeyDateAdjustmentCreate(bookingId: Long, adjustmentId: Long = 99L) {
    stubFor(
      post("/prisoners/booking-id/$bookingId/adjustments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{"id": $adjustmentId}""")
          .withStatus(201),
      ),
    )
  }

  fun stubKeyDateAdjustmentCreateWithError(bookingId: Long, status: Int) {
    stubFor(
      post("/prisoners/booking-id/$bookingId/adjustments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubKeyDateAdjustmentUpdate(adjustmentId: Long) {
    stubFor(
      put("/key-date-adjustments/$adjustmentId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubKeyDateAdjustmentUpdateWithError(adjustmentId: Long, status: Int) {
    stubFor(
      put("/key-date-adjustments/$adjustmentId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubKeyDateAdjustmentDelete(adjustmentId: Long) {
    stubFor(
      delete("/key-date-adjustments/$adjustmentId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubKeyDateAdjustmentDeleteWithError(adjustmentId: Long, status: Int) {
    stubFor(
      delete("/key-date-adjustments/$adjustmentId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubGetSentencingAdjustments(bookingId: Long, sentencingAdjustmentsResponse: SentencingAdjustmentsResponse = SentencingAdjustmentsResponse(emptyList(), emptyList())) {
    stubFor(
      get(
        urlPathEqualTo("/prisoners/booking-id/$bookingId/sentencing-adjustments"),
      )
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(sentencingAdjustmentsResponse),
        ),
    )
  }

  fun stubGetSentencingAdjustmentsWithError(bookingId: Int, status: Int) {
    stubFor(
      get(
        urlPathEqualTo("/prisoners/booking-id/$bookingId/sentencing-adjustments"),
      )
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status)
            .withBody("""{"message":"Error"}"""),
        ),
    )
  }

  // *************************************************** Incentive levels **********************************************

  fun stubNomisGlobalIncentiveLevel(incentiveLevelCode: String = "STD") {
    stubFor(
      get(WireMock.urlPathMatching("/incentives/reference-codes/$incentiveLevelCode")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            """
              {
                "domain": "IEP_LEVEL",
                "code": "$incentiveLevelCode",
                "description": "description for $incentiveLevelCode",
                "active": true,
                "systemDataFlag": false
              }
            """.trimIndent(),
          ),
      ),
    )
  }

  fun stubNomisGlobalIncentiveLevelCreate(incentiveLevelCode: String = "STD") {
    stubFor(
      post("/incentives/reference-codes").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              {
                "domain": "IEP_LEVEL",
                "code": "$incentiveLevelCode",
                "description": "description for $incentiveLevelCode",
                "active": true,
                "systemDataFlag": false
              }
            """.trimIndent(),
          )
          .withStatus(201),
      ),
    )
  }

  fun stubNomisGlobalIncentiveLevelUpdate(incentiveLevelCode: String = "STD") {
    stubFor(
      put("/incentives/reference-codes/$incentiveLevelCode").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              {
                "domain": "IEP_LEVEL",
                "code": "$incentiveLevelCode",
                "description": "description for $incentiveLevelCode",
                "active": true,
                "systemDataFlag": false
              }
            """.trimIndent(),
          )
          .withStatus(201),
      ),
    )
  }

  fun stubNomisGlobalIncentiveLevelNotFound(incentiveLevelCode: String = "STD") {
    stubFor(
      get(WireMock.urlPathMatching("/incentives/reference-codes/$incentiveLevelCode")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NOT_FOUND.value())
          .withBody("""{"message":"Not found"}"""),
      ),
    )
  }

  fun stubNomisGlobalIncentiveLevelReorder() {
    stubFor(
      post("/incentives/reference-codes/reorder").willReturn(
        aResponse().withStatus(200),
      ),
    )
  }

  fun stubGetActivePrisonersInitialCount(totalElements: Long) {
    stubFor(
      get(
        urlPathEqualTo("/prisoners/ids/active"),
      )
        .withQueryParam("page", equalTo("0"))
        .withQueryParam("size", equalTo("1"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(activePrisonersPagedResponse(totalElements = totalElements, pageSize = 1)),
        ),
    )
  }

  fun stubGetActivePrisonersPage(
    totalElements: Long,
    pageNumber: Long,
    numberOfElements: Long = 10,
    pageSize: Long = 10,
    fixedDelay: Int = 500,
  ) {
    stubFor(
      get(
        urlPathEqualTo("/prisoners/ids/active"),
      )
        .withQueryParam("page", equalTo(pageNumber.toString()))
        .withQueryParam("size", equalTo(pageSize.toString()))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withFixedDelay(fixedDelay)
            .withBody(
              activePrisonersPagedResponse(
                totalElements = totalElements,
                numberOfElements = numberOfElements,
                pageNumber = pageNumber,
                pageSize = pageSize,
              ),
            ),
        ),
    )
  }

  fun stubGetActivePrisonersPageWithError(pageNumber: Long, pageSize: Long? = null, responseCode: Int) {
    stubFor(
      get(
        urlPathEqualTo("/prisoners/ids/active"),
      )
        .withQueryParam("page", equalTo(pageNumber.toString()))
        .apply { pageSize?.also { withQueryParam("size", equalTo(it.toString())) } }
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(responseCode)
            .withBody("""{"message":"Error"}"""),
        ),
    )
  }

  fun stubGetAllPrisonersInitialCount(totalElements: Long, numberOfElements: Long) {
    stubFor(
      get(
        urlPathEqualTo("/prisoners/ids/all"),
      )
        .withQueryParam("page", equalTo("0"))
        .withQueryParam("size", equalTo("1"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(allPrisonersPagedResponse(totalElements = totalElements, numberOfElements = numberOfElements, pageSize = 1)),
        ),
    )
  }

  fun stubGetAllPrisonersPage1() {
    stubFor(
      get(urlPathEqualTo("/prisoners/ids/all-from-id"))
        .withQueryParam("offenderId", equalTo("0"))
        .withQueryParam("pageSize", equalTo("5"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              """
              { "prisonerIds": [ 
               {"offenderNo": "A0001BB"},
               {"offenderNo": "A0002BB"},
               {"offenderNo": "A0003BB"},
               {"offenderNo": "A0004BB"},
               {"offenderNo": "A0005BB"}
                ],
                "lastOffenderId": 5
              }
              """,
            ),
        ),
    )
  }

  fun stubGetAllPrisonersPage2() {
    stubFor(
      get(urlPathEqualTo("/prisoners/ids/all-from-id"))
        .withQueryParam("offenderId", equalTo("5"))
        .withQueryParam("pageSize", equalTo("5"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              """
              { "prisonerIds": [ 
               {"offenderNo": "A0006BB"},
               {"offenderNo": "A0007BB"},
               {"offenderNo": "A0008BB"}
                ],
                "lastOffenderId": 8
              }
              """,
            ),
        ),
    )
  }

  fun stubGetAllPrisoners(prisoners: List<String>, offenderId: Long, pageSize: Long) {
    stubFor(
      get(urlPathEqualTo("/prisoners/ids/all-from-id"))
        .withQueryParam("offenderId", equalTo("$offenderId"))
        .withQueryParam("pageSize", equalTo("$pageSize"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              """
              {
                "prisonerIds": [${prisoners.joinToString { """{"offenderNo": "$it"}""" }}],
                "lastOffenderId": ${prisoners.size}
              }
              """,
            ),
        ),
    )
  }

  fun stubGetAllPrisonersPageWithError(pageNumber: Long, pageSize: Long? = null, responseCode: Int) {
    stubFor(
      get(
        urlPathEqualTo("/prisoners/ids/all"),
      )
        .withQueryParam("page", equalTo(pageNumber.toString()))
        .apply { pageSize?.also { withQueryParam("size", equalTo(it.toString())) } }
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(responseCode)
            .withBody("""{"message":"Error"}"""),
        ),
    )
  }

  fun stubGetPrisonerDetails(offenderNo: String, details: PrisonerDetails) {
    stubFor(
      get("/prisoners/$offenderNo")
        .willReturn(
          aResponse()
            .withHeader("Content-type", "application/json")
            .withBody(jsonMapper.writeValueAsString(details))
            .withStatus(200),
        ),
    )
  }

  fun stubCurrentIncentiveGet(bookingId: Long, iepCode: String) {
    stubFor(
      get("/incentives/booking-id/$bookingId/current").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            {
              "offenderNo": "A1234AA",
              "bookingId": $bookingId,
              "incentiveSequence": 1,
              "iepDateTime": "2021-01-01T12:00:00",
              "prisonId": "MDI",
              "iepLevel": { "code": "$iepCode", "description": "description for $iepCode" },
              "currentIep": true,
              "auditModule": "PRISON_API",
              "whenCreated": "2021-01-01T12:00:00"
            }
            """.trimIndent(),
          )
          .withFixedDelay(500)
          .withStatus(200),
      ),
    )
  }

  fun stubCurrentIncentiveGetWithError(bookingId: Long, responseCode: Int) {
    stubFor(
      get("/incentives/booking-id/$bookingId/current").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(responseCode)
          .withBody("""{"message":"Error"}"""),
      ),
    )
  }

  private fun activePrisonersPagedResponse(
    totalElements: Long = 10,
    numberOfElements: Long = 10,
    pageSize: Long = 10,
    pageNumber: Long = 0,
  ): String {
    val activePrisonerId = (1..numberOfElements).map { it + (pageNumber * pageSize) }
      .map { PrisonerIds(bookingId = it, offenderNo = generateOffenderNo(sequence = it)) }
    val content = activePrisonerId.map { """{ "bookingId": ${it.bookingId}, "offenderNo": "${it.offenderNo}" }""" }
      .joinToString { it }
    return """
{
    "content": [
        $content
    ],
    "pageable": {
        "sort": {
            "empty": false,
            "sorted": true,
            "unsorted": false
        },
        "offset": 0,
        "pageSize": $pageSize,
        "pageNumber": $pageNumber,
        "paged": true,
        "unpaged": false
    },
    "last": false,
    "totalPages": ${totalElements / pageSize + 1},
    "totalElements": $totalElements,
    "size": $pageSize,
    "number": $pageNumber,
    "sort": {
        "empty": false,
        "sorted": true,
        "unsorted": false
    },
    "first": true,
    "numberOfElements": ${activePrisonerId.size},
    "empty": false
}                
      
    """.trimIndent()
  }

  private fun allPrisonersPagedResponse(
    totalElements: Long = 10,
    numberOfElements: Long = 10,
    pageSize: Long = 10,
    pageNumber: Long = 0,
  ): String {
    val prisonerIdList = (1..numberOfElements).map { it + (pageNumber * pageSize) }
      .map { PrisonerId(offenderNo = generateOffenderNo(sequence = it)) }
    val content = prisonerIdList.map { """{ "offenderNo": "${it.offenderNo}" }""" }
      .joinToString { it }
    return """
{
    "content": [
        $content
    ],
    "pageable": {
        "sort": {
            "empty": false,
            "sorted": true,
            "unsorted": false
        },
        "offset": 0,
        "pageSize": $pageSize,
        "pageNumber": $pageNumber,
        "paged": true,
        "unpaged": false
    },
    "last": false,
    "totalPages": ${totalElements / pageSize + 1},
    "totalElements": $totalElements,
    "size": $pageSize,
    "number": $pageNumber,
    "sort": {
        "empty": false,
        "sorted": true,
        "unsorted": false
    },
    "first": true,
    "numberOfElements": ${prisonerIdList.size},
    "empty": false
}                
      
    """.trimIndent()
  }

  fun stubNomisPrisonIncentiveLevel(prisonId: String = "MDI", incentiveLevelCode: String = "STD") {
    stubFor(
      get(WireMock.urlPathMatching("/incentives/prison/$prisonId/code/$incentiveLevelCode")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            """
              {
                  "prisonId": "$prisonId",
                  "iepLevelCode": "$incentiveLevelCode",
                  "visitOrderAllowance": 2,
                  "privilegedVisitOrderAllowance": 2,
                  "defaultOnAdmission": false,
                  "remandTransferLimitInPence": 4750,
                  "remandSpendLimitInPence": 47500,
                  "convictedTransferLimitInPence": 1550,
                  "convictedSpendLimitInPence": 15500,
                  "active": true,
                  "visitAllowanceActive": true
              }
            """.trimIndent(),
          ),
      ),
    )
  }

  fun stubNomisPrisonIncentiveLevelCreate(prisonId: String = "MDI", incentiveLevelCode: String = "STD") {
    stubFor(
      post("/incentives/prison/$prisonId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              {
                "prisonId": "$prisonId",
                "iepLevelCode": "$incentiveLevelCode",
                "visitOrderAllowance": 0,
                "privilegedVisitOrderAllowance": 0,
                "defaultOnAdmission": true,
                "remandTransferLimitInPence": 0,
                "remandSpendLimitInPence": 0,
                "convictedTransferLimitInPence": 0,
                "convictedSpendLimitInPence": 0,
                "active": true,
                "expiryDate": "2023-04-24",
                "visitAllowanceActive": true,
                "visitAllowanceExpiryDate": "2023-04-24"
              }
            """.trimIndent(),
          )
          .withStatus(201),
      ),
    )
  }

  fun stubNomisPrisonIncentiveLevelUpdate(prisonId: String = "MDI", incentiveLevelCode: String = "STD") {
    stubFor(
      put("/incentives/prison/$prisonId/code/$incentiveLevelCode").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              {
                "prisonId": "$prisonId",
                "iepLevelCode": "$incentiveLevelCode",
                "visitOrderAllowance": 0,
                "privilegedVisitOrderAllowance": 0,
                "defaultOnAdmission": true,
                "remandTransferLimitInPence": 0,
                "remandSpendLimitInPence": 0,
                "convictedTransferLimitInPence": 0,
                "convictedSpendLimitInPence": 0,
                "active": true,
                "expiryDate": "2023-04-24",
                "visitAllowanceActive": true,
                "visitAllowanceExpiryDate": "2023-04-24"
              }
            """.trimIndent(),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubNomisPrisonIncentiveLevelNotFound(prisonId: String = "MDI", incentiveLevelCode: String = "STD") {
    stubFor(
      get(WireMock.urlPathMatching("/incentives/prison/$prisonId/code/$incentiveLevelCode")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NOT_FOUND.value())
          .withBody("""{"message":"Not found"}"""),
      ),
    )
  }

  // *************************************************** Adjudications **********************************************

  fun stubAdjudicationCreate(offenderNo: String, adjudicationNumber: Long = 123456, chargeSeq: Int = 1) {
    stubFor(
      post("/prisoners/$offenderNo/adjudications").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
{
  "adjudicationSequence": 1,
  "offenderNo": "$offenderNo",
  "bookingId": 1234,
  "gender": {
     "code": "M",
     "description": "Male"
  },
  "adjudicationNumber": $adjudicationNumber,
  "partyAddedDate": "2023-07-25",
  "comment": "string",
  "incident": {
    "adjudicationIncidentId": 1234567,
    "reportingStaff": {
      "staffId": 7654,
      "firstName": "string",
      "lastName": "string",
      "username": "user1",
      "createdByUsername": "user2"
    },
    "incidentDate": "2023-07-25",
    "incidentTime": "10:00",
    "reportedDate": "2023-07-25",
    "reportedTime": "10:00",
    "internalLocation": {
      "locationId": 7654,
      "code": "string",
      "description": "string"
    },
    "incidentType": {
      "code": "string",
      "description": "string"
    },
    "details": "string",
    "prison": {
      "code": "MDI",
      "description": "string"
    },
    "prisonerWitnesses": [],
    "prisonerVictims": [],
    "otherPrisonersInvolved": [],
    "reportingOfficers": [],
    "staffWitnesses": [],
    "staffVictims": [],
    "otherStaffInvolved": [],
    "createdByUsername": "user2",
    "createdDateTime": "2023-02-24T13:14:23",
    "repairs": []
  },
  "charges": [
    {
      "offence": {
        "code": "string",
        "description": "string",
        "type": {
          "code": "string",
          "description": "string"
        },
        "category": {
          "code": "string",
          "description": "string"
        }
      },
      "evidence": "string",
      "reportDetail": "string",
      "offenceId": "string",
      "chargeSequence": $chargeSeq
    }
  ],
  "investigations": [],
  "hearings": []
}            
            """.trimIndent(),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubAdjudicationCreateWithError(offenderNo: String, status: Int) {
    stubFor(
      post("/prisoners/$offenderNo/adjudications").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status)
          .withBody("""{"message":"error"}"""),
      ),
    )
  }

  fun stubAdjudicationRepairsUpdate(adjudicationNumber: Long = 123456) {
    stubFor(
      put("/adjudications/adjudication-number/$adjudicationNumber/repairs").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
{
  "repairs": [
    {
      "type": {
        "code": "CLEA",
        "description": "Cleaning"
      },
      "comment": "Cleaning of cell required",
      "cost": 0,
      "createdByUsername": "PRISON_MANAGE_API"
    }
  ]}            
            """.trimIndent(),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubAdjudicationRepairsUpdateWithError(adjudicationNumber: Long = 123456, status: Int) {
    stubFor(
      put("/adjudications/adjudication-number/$adjudicationNumber/repairs").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status)
          .withBody("""{"message":"error"}"""),
      ),
    )
  }

  fun stubAdjudicationEvidenceUpdate(adjudicationNumber: Long = 123456) {
    stubFor(
      put("/adjudications/adjudication-number/$adjudicationNumber/evidence").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              {
                "evidence": [
                    {
                        "type": {
                            "code": "PHOTO",
                            "description": "Photographic Evidence"
                        },
                        "date": "2023-10-03",
                        "detail": "smashed light bulb",
                        "createdByUsername": "PRISONER_MANAGER_API"
                    }
                ]
              }
            """.trimIndent(),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubAdjudicationEvidenceUpdateWithError(adjudicationNumber: Long = 123456, status: Int) {
    stubFor(
      put("/adjudications/adjudication-number/$adjudicationNumber/evidence").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status)
          .withBody("""{"message":"error"}"""),
      ),
    )
  }

  fun stubHearingCreate(adjudicationNumber: Long = 123456, nomisHearingId: Long = 345) {
    stubFor(
      post("/adjudications/adjudication-number/$adjudicationNumber/hearings").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            {
              "hearingId": $nomisHearingId
            }
            """.trimIndent(),
          )
          .withStatus(200),
      ),
    )
  }
  fun stubHearingsCreate(adjudicationNumber: Long = 123456, nomisHearingId1: Long = 1, nomisHearingId2: Long = 2) {
    stubFor(
      post("/adjudications/adjudication-number/$adjudicationNumber/hearings")
        .inScenario("Two hearings")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
            {
              "hearingId": $nomisHearingId1
            }
              """.trimIndent(),
            )
            .withStatus(200),
        ).willSetStateTo("2nd Hearing"),
    )
    stubFor(
      post("/adjudications/adjudication-number/$adjudicationNumber/hearings")
        .inScenario("Two hearings")
        .whenScenarioStateIs("2nd Hearing")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
            {
              "hearingId": $nomisHearingId2
            }
              """.trimIndent(),
            )
            .withStatus(200),
        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubHearingUpdate(adjudicationNumber: Long = 123456, nomisHearingId: Long = 345) {
    stubFor(
      put("/adjudications/adjudication-number/$adjudicationNumber/hearings/$nomisHearingId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            {
            "hearingId": $nomisHearingId,
            "type": {
                "code": "GOV_ADULT",
                "description": "Governor's Hearing Adult"
            },
            "scheduleDate": "2023-09-06",
            "scheduleTime": "13:29:13",
            "hearingDate": "2023-09-09",
            "hearingTime": "10:00:00",
            "internalLocation": {
                "locationId": 775,
                "code": "ESTAB",
                "description": "MDI-ESTAB"
            },
            "hearingResults": [],
            "eventId": 514306417,
            "createdDateTime": "2023-09-06T13:29:13.465687577",
            "createdByUsername": "PRISONER_MANAGER_API",
            "notifications": []
        }
            """.trimIndent(),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubHearingDelete(adjudicationNumber: Long = 123456, nomisHearingId: Long = 345) {
    stubFor(
      delete("/adjudications/adjudication-number/$adjudicationNumber/hearings/$nomisHearingId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubHearingResultUpsert(
    adjudicationNumber: Long = 123456,
    nomisHearingId: Long = 345,
    chargeSequence: Int = 1,
    plea: String = "NOT_GUILTY",
    finding: String = "GUILTY",
  ) {
    stubFor(
      post("/adjudications/adjudication-number/$adjudicationNumber/hearings/$nomisHearingId/charge/$chargeSequence/result").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            {
              "pleaFindingCode": "$plea",
              "findingCode": "$finding",
              "adjudicatorUsername": "DBULL_GEN"
            }
            """.trimIndent(),
          )
          .withStatus(201),
      ),
    )
  }

  fun stubHearingResultDelete(adjudicationNumber: Long = 123456, nomisHearingId: Long = 345, chargeSequence: Int = 1, deletedAwardIds: List<Pair<Long, Int>> = emptyList()) {
    stubFor(
      delete("/adjudications/adjudication-number/$adjudicationNumber/hearings/$nomisHearingId/charge/$chargeSequence/result").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            {
                "awardsDeleted": [
                    ${deletedAwardIds.joinToString { """{"bookingId": ${it.first}, "sanctionSequence": ${it.second}}""" }}
                ]
            }            
            """.trimIndent(),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubReferralDelete(adjudicationNumber: Long = 123456, chargeSequence: Int = 1) {
    stubFor(
      delete("/adjudications/adjudication-number/$adjudicationNumber/charge/$chargeSequence/result").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubReferralUpsert(
    adjudicationNumber: Long = 123456,
    chargeSequence: Int = 1,
    plea: String = "NOT_ASKED",
    finding: String = "REF_POLICE",
  ) {
    stubFor(
      post("/adjudications/adjudication-number/$adjudicationNumber/charge/$chargeSequence/result").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            {
              "pleaFindingCode": "$plea",
              "findingCode": "$finding"
            }
            """.trimIndent(),
          )
          .withStatus(201),
      ),
    )
  }

  fun stubAdjudicationAwardsCreate(adjudicationNumber: Long = 123456, chargeSequence: Int = 1, awardIds: List<Pair<Long, Int>> = listOf(1201050L to 3)) {
    stubFor(
      post("/adjudications/adjudication-number/$adjudicationNumber/charge/$chargeSequence/awards").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            {
                "awardsCreated": [
                    ${awardIds.joinToString { """{"bookingId": ${it.first}, "sanctionSequence": ${it.second}}""" }}
                ]
            }            
            """.trimIndent(),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubAdjudicationAwardsCreateWithError(adjudicationNumber: Long = 123456, chargeSequence: Int = 1, status: Int) {
    stubFor(
      post("/adjudications/adjudication-number/$adjudicationNumber/charge/$chargeSequence/awards").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status)
          .withBody("""{"message":"error"}"""),
      ),
    )
  }

  fun stubAdjudicationAwardsUpdate(
    adjudicationNumber: Long = 123456,
    chargeSequence: Int = 1,
    createdAwardIds: List<Pair<Long, Int>> = listOf(1201050L to 3),
    deletedAwardIds: List<Pair<Long, Int>> = listOf(1201050L to 1),
  ) {
    stubFor(
      put("/adjudications/adjudication-number/$adjudicationNumber/charge/$chargeSequence/awards").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            {
                "awardsCreated": [
                    ${createdAwardIds.joinToString { """{"bookingId": ${it.first}, "sanctionSequence": ${it.second}}""" }}
                ],
                "awardsDeleted": [
                    ${deletedAwardIds.joinToString { """{"bookingId": ${it.first}, "sanctionSequence": ${it.second}}""" }}
                ]
            }            
            """.trimIndent(),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubAdjudicationAwardsDelete(
    adjudicationNumber: Long = 123456,
    chargeSequence: Int = 1,
    deletedAwardIds: List<Pair<Long, Int>> = listOf(1201050L to 1),
  ) {
    stubFor(
      delete("/adjudications/adjudication-number/$adjudicationNumber/charge/$chargeSequence/awards").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            {
                "awardsDeleted": [
                    ${deletedAwardIds.joinToString { """{"bookingId": ${it.first}, "sanctionSequence": ${it.second}}""" }}
                ]
            }            
            """.trimIndent(),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubAdjudicationAwardsUpdateWithError(adjudicationNumber: Long = 123456, chargeSequence: Int = 1, status: Int) {
    stubFor(
      put("/adjudications/adjudication-number/$adjudicationNumber/charge/$chargeSequence/awards").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status)
          .withBody("""{"message":"error"}"""),
      ),
    )
  }

  fun stubAdjudicationSquashAwards(
    adjudicationNumber: Long = 123456,
    chargeSequence: Int = 1,
  ) {
    stubFor(
      put("/adjudications/adjudication-number/$adjudicationNumber/charge/$chargeSequence/quash").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubAdjudicationUnquashAwards(
    adjudicationNumber: Long = 123456,
    chargeSequence: Int = 1,
    createdAwardIds: List<Pair<Long, Int>> = listOf(),
    deletedAwardIds: List<Pair<Long, Int>> = listOf(),
  ) {
    stubFor(
      put("/adjudications/adjudication-number/$adjudicationNumber/charge/$chargeSequence/unquash").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            {
                "awardsCreated": [
                    ${createdAwardIds.joinToString { """{"bookingId": ${it.first}, "sanctionSequence": ${it.second}}""" }}
                ],
                "awardsDeleted": [
                    ${deletedAwardIds.joinToString { """{"bookingId": ${it.first}, "sanctionSequence": ${it.second}}""" }}
                ]
            }            
            """.trimIndent(),
          )
          .withStatus(200),
      ),
    )
  }

  fun stubGetAdaAwardSummary(
    bookingId: Long,
    adjudicationADAAwardSummaryResponse: AdjudicationADAAwardSummaryResponse = AdjudicationADAAwardSummaryResponse(
      bookingId = bookingId,
      offenderNo = "A1234XT",
      adaSummaries = emptyList(),
    ),
  ) {
    stubFor(
      get(
        urlPathEqualTo("/prisoners/booking-id/$bookingId/awards/ada/summary"),
      )
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(adjudicationADAAwardSummaryResponse),
        ),
    )
  }

  fun stubGetAdaAwardSummaryWithError(bookingId: Int, status: Int) {
    stubFor(
      get(
        urlPathEqualTo("/prisoners/booking-id/$bookingId/awards/ada/summary"),
      )
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status)
            .withBody("""{"message":"Error"}"""),
        ),
    )
  }

  fun stubGetMergesFromDate(
    offenderNo: String,
    merges: List<MergeDetail> = emptyList(),
  ) {
    stubFor(
      get(
        urlPathEqualTo("/prisoners/$offenderNo/merges"),
      )
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(merges),
        ),
    )
  }

  // *************************************************** Non-Associations **********************************************

  fun stubNonAssociationCreate(response: String) {
    stubFor(
      post("/non-associations").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201)
          .withBody(response),
      ),
    )
  }

  fun stubNonAssociationAmend(offenderNo1: String, offenderNo2: String) {
    stubFor(
      put("/non-associations/offender/$offenderNo1/ns-offender/$offenderNo2/sequence/1").willReturn(ok()),
    )
  }

  fun stubNonAssociationAmendWithError(offenderNo1: String, offenderNo2: String, status: Int = 500) {
    stubFor(
      put("/non-associations/offender/$offenderNo1/ns-offender/$offenderNo2/sequence/1").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubNonAssociationClose(offenderNo1: String, offenderNo2: String) {
    stubFor(
      put("/non-associations/offender/$offenderNo1/ns-offender/$offenderNo2/sequence/1/close").willReturn(ok()),
    )
  }

  fun stubNonAssociationCloseWithError(offenderNo1: String, offenderNo2: String, status: Int = 500) {
    stubFor(
      put("/non-associations/offender/$offenderNo1/ns-offender/$offenderNo2/sequence/1/close").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubNonAssociationCloseWithErrorFollowedBySlowSuccess(offenderNo1: String, offenderNo2: String) {
    stubFor(
      put("/non-associations/offender/$offenderNo1/ns-offender/$offenderNo2/sequence/1/close")
        .inScenario("Retry NOMIS NonAssociations close Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause NOMIS NonAssociations close Success"),
    )

    stubFor(
      put("/non-associations/offender/$offenderNo1/ns-offender/$offenderNo2/sequence/1/close")
        .inScenario("Retry NOMIS NonAssociations close Scenario")
        .whenScenarioStateIs("Cause NOMIS NonAssociations close Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withFixedDelay(1500),
        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubNonAssociationDelete(offenderNo1: String, offenderNo2: String) {
    stubFor(
      delete("/non-associations/offender/$offenderNo1/ns-offender/$offenderNo2/sequence/1").willReturn(ok()),
    )
  }

  fun stubGetNonAssociationsInitialCount(response: String) {
    stubFor(
      get(urlPathEqualTo("/non-associations/ids"))
        .withQueryParam("page", equalTo("0"))
        .withQueryParam("size", equalTo("1"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(response),
        ),
    )
  }

  fun stubGetNonAssociationsPage(pageNumber: Long, pageSize: Long = 10, response: String) {
    stubFor(
      get(urlPathEqualTo("/non-associations/ids"))
        .withQueryParam("page", equalTo(pageNumber.toString()))
        .withQueryParam("size", equalTo(pageSize.toString()))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withFixedDelay(500)
            .withBody(response),
        ),
    )
  }

  fun stubGetNonAssociationsPageWithError(pageNumber: Long, responseCode: Int) {
    stubFor(
      get(urlPathEqualTo("/non-associations/ids"))
        .withQueryParam("page", equalTo(pageNumber.toString())).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(responseCode)
            .withBody("""{"message":"Error"}"""),
        ),
    )
  }

  fun stubGetNonAssociationsAll(offenderNo1: String, offenderNo2: String, response: String) {
    stubFor(
      get(
        urlPathEqualTo("/non-associations/offender/$offenderNo1/ns-offender/$offenderNo2/all"),
      )
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(response),
        ),
    )
  }

  fun stubGetNonAssociationsAllWithError(offenderNo1: String, offenderNo2: String, responseCode: Int) {
    stubFor(
      get(
        urlPathEqualTo("/non-associations/offender/$offenderNo1/ns-offender/$offenderNo2/all"),
      )
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(responseCode)
            .withBody("""{"message":"Error"}"""),
        ),
    )
  }

  fun stubGetNonAssociationsByBooking(bookingId: Long, response: String) {
    stubFor(
      get(urlPathEqualTo("/non-associations/booking/$bookingId")).willReturn(okJson(response)),
    )
  }

  // *************************************************** Locations **********************************************

  fun stubLocationCreate(response: String) {
    stubFor(
      post("/locations").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(201),
      ),
    )
  }

  fun stubLocationCreateWithError(status: Int = 500) {
    stubFor(
      post("/locations").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubLocationCreateWithErrorFollowedBySlowSuccess(response: String) {
    stubFor(
      post("/locations")
        .inScenario("Retry NOMIS Locations Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause NOMIS Locations Success"),
    )

    stubFor(
      post("/locations")
        .inScenario("Retry NOMIS Locations Scenario")
        .whenScenarioStateIs("Cause NOMIS Locations Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(response)
            .withStatus(200)
            .withFixedDelay(1500),
        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubLocationUpdate(url: String) {
    stubFor(
      put(url).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubLocationUpdateWithError(url: String, status: Int = 500) {
    stubFor(
      put(url).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubLocationUpdateWithErrorFollowedBySlowSuccess(url: String) {
    stubFor(
      put(url)
        .inScenario("Retry NOMIS Locations Update Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause NOMIS Locations Update Success"),
    )

    stubFor(
      put(url)
        .inScenario("Retry NOMIS Locations Update Scenario")
        .whenScenarioStateIs("Cause NOMIS Locations Update Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withFixedDelay(1500),
        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubLocationDelete(locationId: Long) {
    stubFor(
      delete("/locations/$locationId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubLocationDeleteWithError(locationId: Long, status: Int = 500) {
    stubFor(
      delete("/locations/$locationId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubLocationDeleteWithErrorFollowedBySlowSuccess(locationId: Long) {
    stubFor(
      delete("/locations/$locationId")
        .inScenario("Retry NOMIS Appointments Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause NOMIS Appointments Success"),
    )

    stubFor(
      delete("/locations/$locationId")
        .inScenario("Retry NOMIS Appointments Scenario")
        .whenScenarioStateIs("Cause NOMIS Appointments Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withFixedDelay(1500),
        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetLocationsInitialCount(response: String) {
    stubFor(
      get(urlPathEqualTo("/locations/ids"))
        .withQueryParam("page", equalTo("0"))
        .withQueryParam("size", equalTo("1"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(response),
        ),
    )
  }

  fun stubGetLocationsPage(pageNumber: Long, pageSize: Long = 10, response: String) {
    stubFor(
      get(urlPathEqualTo("/locations/ids"))
        .withQueryParam("page", equalTo(pageNumber.toString()))
        .withQueryParam("size", equalTo(pageSize.toString()))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withFixedDelay(500)
            .withBody(response),
        ),
    )
  }

  fun stubGetLocationsPageWithError(pageNumber: Long, responseCode: Int) {
    stubFor(
      get(urlPathEqualTo("/locations/ids"))
        .withQueryParam("page", equalTo(pageNumber.toString())).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(responseCode)
            .withBody("""{"message":"Error"}"""),
        ),
    )
  }

  fun stubGetLocation(id: Long, response: String) {
    stubFor(
      get(
        urlPathEqualTo("/locations/$id"),
      )
        .willReturn(okJson(response)),
    )
  }

  fun stubGetLocationWithError(id: Long, responseCode: Int) {
    stubFor(
      get(
        urlPathEqualTo("/locations/$id"),
      )
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(responseCode)
            .withBody("""{"message":"Error"}"""),
        ),
    )
  }

  fun stuGetAllLatestBookings(
    response: BookingIdsWithLast = BookingIdsWithLast(
      lastBookingId = 0,
      prisonerIds = emptyList(),
    ),
    activeOnly: Boolean = false,
  ) {
    val mappingBuilder = if (activeOnly) {
      get(urlPathEqualTo("/bookings/ids/latest-from-id"))
        .withQueryParam("activeOnly", equalTo("true"))
    } else {
      get(urlPathEqualTo("/bookings/ids/latest-from-id"))
    }
    stubFor(
      mappingBuilder
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(response),
        ),
    )
  }

  fun stuGetAllLatestBookings(
    bookingId: Long,
    response: BookingIdsWithLast = BookingIdsWithLast(
      lastBookingId = 0,
      prisonerIds = emptyList(),
    ),
  ) {
    stubFor(
      get(
        urlPathEqualTo("/bookings/ids/latest-from-id"),
      ).withQueryParam("bookingId", equalTo("$bookingId"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(response),
        ),
    )
  }
  fun stuGetAllLatestBookings(
    bookingId: Long,
    errorStatus: HttpStatus,
  ) {
    stubFor(
      get(
        urlPathEqualTo("/bookings/ids/latest-from-id"),
      ).withQueryParam("bookingId", equalTo("$bookingId"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(errorStatus.value()),
        ),
    )
  }

  fun postCountFor(url: String) = this.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo(url))).count()
  fun putCountFor(url: String) = this.findAll(WireMock.putRequestedFor(WireMock.urlEqualTo(url))).count()

  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder {
    this.withBody(jsonMapper.writeValueAsString(body))
    return this
  }
}

private const val CREATE_VISIT_RESPONSE = """
    {
      "visitId": "12345"
    }
    """
private const val CREATE_INCENTIVE_RESPONSE = """
    {
      "bookingId": 456,
      "sequence": 1
    }
    """

fun generateOffenderNo(prefix: String = "A", sequence: Long = 1, suffix: String = "TZ") = "$prefix${sequence.toString().padStart(4, '0')}$suffix"

fun activePrisons() = listOf(Prison("ASI", "Ashfield"), Prison("LEI", "Leeds"), Prison("MDI", "Moorland"))
