package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.ActivePrisonerId

class NomisApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val nomisApi = NomisApiMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
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
    private const val ERROR_RESPONSE = """{ "error": "some error" }"""
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
          .withBody("""{ "offenderProgramReferenceId": 1234, "created": true }""")
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

  fun stubGetServicePrisons(serviceCode: String, response: String) {
    stubFor(
      get("/service-prisons/$serviceCode")
        .willReturn(
          aResponse()
            .withHeader("Content-type", "application/json")
            .withBody(response)
            .withStatus(200),
        ),
    )
  }

  fun stubGetServicePrisonsWithError(serviceCode: String, status: Int = 500) {
    stubFor(
      get("/service-prisons/$serviceCode")
        .willReturn(
          aResponse()
            .withHeader("Content-type", "application/json")
            .withBody(ERROR_RESPONSE)
            .withStatus(status),
        ),
    )
  }

  fun stubGetPrisonerDetails(response: String) {
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
                "active": true
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
                "active": true
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
                "active": true
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
        urlPathEqualTo("/prisoners/ids"),
      )
        .withQueryParam("page", WireMock.equalTo("0"))
        .withQueryParam("size", WireMock.equalTo("1"))
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
  ) {
    stubFor(
      get(
        urlPathEqualTo("/prisoners/ids"),
      )
        .withQueryParam("page", WireMock.equalTo(pageNumber.toString()))
        .withQueryParam("size", WireMock.equalTo(pageSize.toString()))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withFixedDelay(500)
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

  fun stubGetActivePrisonersPageWithError(pageNumber: Long, responseCode: Int) {
    stubFor(
      get(
        urlPathEqualTo("/prisoners/ids"),
      )
        .withQueryParam("page", WireMock.equalTo(pageNumber.toString())).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(responseCode)
            .withBody("""{"message":"Error"}"""),
        ),
    )
  }

  fun stubCurrentIncentiveGet(bookingId: Long, iepCode: String) {
    stubFor(
      get("/incentives/booking-id/$bookingId/current").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{"iepLevel": { "code" : "$iepCode", "description" : "description for $iepCode" }}""")
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
      .map { ActivePrisonerId(bookingId = it, offenderNo = "A${it.toString().padStart(4, '0')}TZ") }
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

  fun stubHearingResultDelete(adjudicationNumber: Long = 123456, nomisHearingId: Long = 345, chargeSequence: Int = 1) {
    stubFor(
      delete("/adjudications/adjudication-number/$adjudicationNumber/hearings/$nomisHearingId/charge/$chargeSequence/result").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
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
                "awardResponses": [
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
                "awardResponsesCreated": [
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

  fun stubNonAssociationCreateWithError(status: Int = 500) {
    stubFor(
      post("/non-associations").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubNonAssociationCreateWithErrorFollowedBySlowSuccess(response: String) {
    stubFor(
      post("/non-associations")
        .inScenario("Retry NOMIS NonAssociations Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause NOMIS NonAssociations Success"),
    )

    stubFor(
      post("/non-associations")
        .inScenario("Retry NOMIS NonAssociations Scenario")
        .whenScenarioStateIs("Cause NOMIS NonAssociations Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withBody(response)
            .withFixedDelay(1500),
        ).willSetStateTo(Scenario.STARTED),
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

  fun stubNonAssociationAmendWithErrorFollowedBySlowSuccess(offenderNo1: String, offenderNo2: String) {
    stubFor(
      put("/non-associations/offender/$offenderNo1/ns-offender/$offenderNo2/sequence/1")
        .inScenario("Retry NOMIS NonAssociations amend Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause NOMIS NonAssociations amend Success"),
    )

    stubFor(
      put("/non-associations/offender/$offenderNo1/ns-offender/$offenderNo2/sequence/1")
        .inScenario("Retry NOMIS NonAssociations amend Scenario")
        .whenScenarioStateIs("Cause NOMIS NonAssociations amend Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withFixedDelay(1500),
        ).willSetStateTo(Scenario.STARTED),
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
        .withQueryParam("page", WireMock.equalTo("0"))
        .withQueryParam("size", WireMock.equalTo("1"))
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
        .withQueryParam("page", WireMock.equalTo(pageNumber.toString()))
        .withQueryParam("size", WireMock.equalTo(pageSize.toString()))
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
        .withQueryParam("page", WireMock.equalTo(pageNumber.toString())).willReturn(
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

  fun postCountFor(url: String) = this.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo(url))).count()
  fun putCountFor(url: String) = this.findAll(WireMock.putRequestedFor(WireMock.urlEqualTo(url))).count()
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
