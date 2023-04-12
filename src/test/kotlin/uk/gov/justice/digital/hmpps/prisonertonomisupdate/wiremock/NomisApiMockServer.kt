package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus

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

  fun stubActivityUpdate(nomisActivityId: Long) {
    stubFor(
      put("/activities/$nomisActivityId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
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

  fun stubScheduleInstancesUpdateWithError(nomisActivityId: Long, status: Int = 500) {
    stubFor(
      put("/activities/$nomisActivityId/schedules").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubAllocationCreate(courseActivityId: Long) {
    stubFor(
      post("/activities/$courseActivityId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "offenderProgramReferenceId": 1234 }""")
          .withStatus(201),
      ),
    )
  }

  fun stubAllocationCreateWithError(courseActivityId: Long, status: Int = 500) {
    stubFor(
      post("/activities/$courseActivityId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubDeallocate(courseActivityId: Long, bookingId: Long) {
    stubFor(
      put("/activities/$courseActivityId/booking-id/$bookingId/end").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "offenderProgramReferenceId": 1234 }""")
          .withStatus(201),
      ),
    )
  }

  fun stubDeallocateWithError(courseActivityId: Long, bookingId: Long, status: Int = 500) {
    stubFor(
      put("/activities/$courseActivityId/booking-id/$bookingId/end").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubUpsertAttendance(courseActivityId: Long, bookingId: Long, response: String) {
    stubFor(
      post("/activities/$courseActivityId/booking/$bookingId/attendance").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200),
      ),
    )
  }

  fun stubUpsertAttendanceWithError(courseActivityId: Long, bookingId: Long, status: Int = 500) {
    stubFor(
      post("/activities/$courseActivityId/booking/$bookingId/attendance").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

  fun stubGetAttendanceStatus(courseActivityId: Long, bookingId: Long, response: String) {
    stubFor(
      post("/activities/$courseActivityId/booking/$bookingId/attendance-status").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200),
      ),
    )
  }

  fun stubGetAttendanceStatusWithError(courseActivityId: Long, bookingId: Long, status: Int = 500) {
    stubFor(
      post("/activities/$courseActivityId/booking/$bookingId/attendance-status").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(ERROR_RESPONSE)
          .withStatus(status),
      ),
    )
  }

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
                "active": true,
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

  private val CREATE_VISIT_RESPONSE = """
    {
      "visitId": "12345"
    }
    """

  private val CREATE_INCENTIVE_RESPONSE = """
    {
      "bookingId": 456,
      "sequence": 1
    }
    """

  fun postCountFor(url: String) = this.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo(url))).count()
  fun putCountFor(url: String) = this.findAll(WireMock.putRequestedFor(WireMock.urlEqualTo(url))).count()
}
