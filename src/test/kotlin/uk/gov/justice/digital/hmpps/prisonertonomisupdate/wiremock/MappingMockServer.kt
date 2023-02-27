package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

class MappingExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val mappingServer = MappingMockServer()
  }

  override fun beforeAll(context: ExtensionContext) {
    mappingServer.start()
  }

  override fun beforeEach(context: ExtensionContext) {
    mappingServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    mappingServer.stop()
  }
}

class MappingMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8084
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status)
      )
    )
  }

  fun stubCreate() {
    stubFor(
      post("/mapping/visits").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201)
      )
    )
  }

  fun stubCreateWithError(status: Int = 500) {
    stubFor(
      post("/mapping/visits").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id already exists" }""")
          .withStatus(status)
      )
    )
  }

  fun stubGetNomis(nomisId: String, response: String) {
    stubFor(
      get("/mapping/visits/nomisId/$nomisId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200)
      )
    )
  }

  fun stubGetNomisWithError(nomisId: String, status: Int = 500) {
    stubFor(
      get("/mapping/visits/nomisId/$nomisId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id does not exist" }""")
          .withStatus(status)
      )
    )
  }

  fun stubGetVsip(vsipId: String, response: String) {
    stubFor(
      get("/mapping/visits/vsipId/$vsipId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200)
      )
    )
  }

  fun stubGetVsipWithError(vsipId: String, status: Int = 500) {
    stubFor(
      get("/mapping/visits/vsipId/$vsipId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id does not exist" }""")
          .withStatus(status)
      )
    )
  }

  fun stubCreateIncentive() {
    stubFor(
      post("/mapping/incentives").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201)
      )
    )
  }

  fun stubCreateIncentiveWithError(status: Int = 500) {
    stubFor(
      post("/mapping/incentives").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id already exists" }""")
          .withStatus(status)
      )
    )
  }

  fun stubGetIncentiveId(incentiveId: Long, response: String) {
    stubFor(
      get("/mapping/incentives/incentive-id/$incentiveId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200)
      )
    )
  }

  fun stubGetIncentiveIdWithError(incentiveId: Long, status: Int = 500) {
    stubFor(
      get("/mapping/incentives/incentive-id/$incentiveId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id does not exist" }""")
          .withStatus(status)
      )
    )
  }

  fun stubCreateActivity() {
    stubFor(
      post("/mapping/activities").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201)
      )
    )
  }

  fun stubCreateActivityWithError(status: Int = 500) {
    stubFor(
      post("/mapping/activities").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id already exists" }""")
          .withStatus(status)
      )
    )
  }

  fun stubGetMappingGivenActivityScheduleId(id: Long, response: String) {
    stubFor(
      get("/mapping/activities/activity-schedule-id/$id").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200)
      )
    )
  }

  fun stubGetMappingGivenActivityScheduleIdWithError(id: Long, status: Int = 500) {
    stubFor(
      get("/mapping/activities/activity-schedule-id/$id").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id does not exist" }""")
          .withStatus(status)
      )
    )
  }

  fun stubCreateAppointment() {
    stubFor(
      post("/mapping/appointments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201)
      )
    )
  }

  fun stubCreateAppointmentWithError(status: Int = 500) {
    stubFor(
      post("/mapping/appointments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id already exists" }""")
          .withStatus(status)
      )
    )
  }

  fun stubGetMappingGivenAppointmentInstanceId(id: Long, response: String) {
    stubFor(
      get("/mapping/appointments/appointment-instance-id/$id").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200)
      )
    )
  }

  fun stubGetMappingGivenAppointmentInstanceIdWithError(id: Long, status: Int = 500) {
    stubFor(
      get("/mapping/appointments/appointment-instance-id/$id").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id does not exist" }""")
          .withStatus(status)
      )
    )
  }

  fun stubCreateAppointmentWithErrorFollowedBySlowSuccess() {
    stubFor(
      post("/mapping/appointments")
        .inScenario("Retry Mapping Appointment Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json")
        )
        .willSetStateTo("Cause Mapping Appointment Success")
    )

    stubFor(
      post("/mapping/appointments")
        .inScenario("Retry Mapping Appointment Scenario")
        .whenScenarioStateIs("Cause Mapping Appointment Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withFixedDelay(1500)

        ).willSetStateTo(Scenario.STARTED)
    )
  }

  fun postCountFor(url: String) = this.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo(url))).count()
  fun stubCreateSentencingAdjustment() {
    stubFor(
      post("/mapping/sentencing/adjustments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201)
      )
    )
  }

  fun stubCreateSentencingAdjustmentWithError(status: Int) {
    stubFor(
      post("/mapping/sentencing/adjustments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id already exists" }""")
          .withStatus(status)
      )
    )
  }

  fun stubGetByAdjustmentId(
    adjustmentId: String,
    nomisAdjustmentId: Long = 1234,
    nomisAdjustmentCategory: String = "SENTENCE",
  ) {
    stubFor(
      get("/mapping/sentencing/adjustments/adjustment-id/$adjustmentId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            { 
            "adjustmentId": "$adjustmentId",  
            "nomisAdjustmentId": $nomisAdjustmentId,  
            "nomisAdjustmentCategory": "$nomisAdjustmentCategory",  
            "mappingType": "MIGRATED",  
            "whenCreated": "2020-01-01T00:00:00"
              }"""
          )
          .withStatus(200)
      )
    )
  }

  fun stubDeleteByAdjustmentId(
    adjustmentId: String,
  ) {
    stubFor(
      delete("/mapping/sentencing/adjustments/adjustment-id/$adjustmentId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204)
      )
    )
  }

  fun stubDeleteByAdjustmentIdWithError(adjustmentId: String, status: Int) {
    stubFor(
      delete("/mapping/sentencing/adjustments/adjustment-id/$adjustmentId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "not here" }""")
          .withStatus(status)
      )
    )
  }

  fun stubGetByAdjustmentIdWithError(adjustmentId: String, status: Int) {
    stubFor(
      get("/mapping/sentencing/adjustments/adjustment-id/$adjustmentId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              { 
                "userMessage": "some error"
              }"""
          )
          .withStatus(status)
      )
    )
  }

  fun stubCreateSentencingAdjustmentWithErrorFollowedBySlowSuccess() {
    stubFor(
      post("/mapping/sentencing/adjustments")
        .inScenario("Retry Mapping Adjustments Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json")
        )
        .willSetStateTo("Cause Mapping Adjustments Success")
    )

    stubFor(
      post("/mapping/sentencing/adjustments")
        .inScenario("Retry Mapping Adjustments Scenario")
        .whenScenarioStateIs("Cause Mapping Adjustments Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withFixedDelay(1500)

        ).willSetStateTo(Scenario.STARTED)
    )
  }
}
