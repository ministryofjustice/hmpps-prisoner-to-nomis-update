package uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.created
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.UrlPattern
import com.github.tomakehurst.wiremock.stubbing.Scenario
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper

class MappingExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val mappingServer = MappingMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
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
          .withStatus(status),
      ),
    )
  }

  fun stubCreate() {
    stubFor(
      post("/mapping/visits").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateWithError(status: Int = 500) {
    stubFor(
      post("/mapping/visits").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id already exists" }""")
          .withStatus(status),
      ),
    )
  }

  fun stubCreateWithDuplicateError(visitId: String, nomisId: Long, duplicateNomisId: Long) {
    stubFor(
      post("/mapping/visits").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            { 
              "status": 409,
              "errorCode": 1409,
              "userMessage": "Conflict: Visit mapping already exist",
              "moreInfo": {
                "existing": {
                  "vsipId": "$visitId",
                  "nomisId": $nomisId,
                  "mappingType": "ONLINE"
                },
                "duplicate": {
                  "vsipId": "$visitId",
                  "nomisId": $duplicateNomisId,
                  "mappingType": "ONLINE"
                }
              }
            }
            """.trimMargin(),
          )
          .withStatus(409),
      ),
    )
  }

  fun stubGetNomis(nomisId: String, response: String) {
    stubFor(
      get("/mapping/visits/nomisId/$nomisId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200),
      ),
    )
  }

  fun stubGetNomisWithError(nomisId: String, status: Int = 500) {
    stubFor(
      get("/mapping/visits/nomisId/$nomisId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id does not exist" }""")
          .withStatus(status),
      ),
    )
  }

  fun stubGetVsip(vsipId: String, response: String) {
    stubFor(
      get("/mapping/visits/vsipId/$vsipId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200),
      ),
    )
  }

  fun stubGetVsipWithError(vsipId: String, status: Int = 500) {
    stubFor(
      get("/mapping/visits/vsipId/$vsipId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id does not exist" }""")
          .withStatus(status),
      ),
    )
  }

  fun stubGetVsipWithErrorFollowedBySuccess(vsipId: String, response: String, status: Int = 404) {
    stubFor(
      get("/mapping/visits/vsipId/$vsipId")
        .inScenario("Check Mapping Visit Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status),
        ).willSetStateTo("Cause Mapping Visit Success"),
    )

    stubFor(
      get("/mapping/visits/vsipId/$vsipId")
        .inScenario("Check Mapping Visit Scenario")
        .whenScenarioStateIs("Cause Mapping Visit Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
            .withBody(response),
        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubCreateIncentive() {
    stubFor(
      post("/mapping/incentives").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateIncentiveWithError(status: Int = 500) {
    stubFor(
      post("/mapping/incentives").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id already exists" }""")
          .withStatus(status),
      ),
    )
  }

  fun stubCreateIncentiveWithDuplicateError(
    incentiveId: Long,
    nomisBookingId: Long,
    nomisIncentiveSequence: Long,
    duplicateNomisIncentiveSequence: Long,
  ) {
    stubFor(
      post("/mapping/incentives").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            { 
              "status": 409,
              "errorCode": 1409,
              "userMessage": "Conflict: Incentive mapping already exist",
              "moreInfo": {
                "existing": {
                  "incentiveId": "$incentiveId",
                  "nomisBookingId": $nomisBookingId,
                  "nomisIncentiveSequence": $nomisIncentiveSequence,
                  "mappingType": "INCENTIVE_CREATED"
                },
                "duplicate": {
                  "incentiveId": "$incentiveId",
                  "nomisBookingId": $nomisBookingId,
                  "nomisIncentiveSequence": $duplicateNomisIncentiveSequence,
                  "mappingType": "INCENTIVE_CREATED"
                }
              }
            }
            """.trimMargin(),
          )
          .withStatus(409),
      ),
    )
  }

  fun stubGetIncentiveId(incentiveId: Long, response: String) {
    stubFor(
      get("/mapping/incentives/incentive-id/$incentiveId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200),
      ),
    )
  }

  fun stubGetIncentiveIdWithError(incentiveId: Long, status: Int = 500) {
    stubFor(
      get("/mapping/incentives/incentive-id/$incentiveId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id does not exist" }""")
          .withStatus(status),
      ),
    )
  }

  fun stubCreateActivity() {
    stubFor(
      post("/mapping/activities").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubUpdateActivity() {
    stubFor(
      put("/mapping/activities").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateActivityWithError(status: Int = 500) {
    stubFor(
      post("/mapping/activities").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id already exists" }""")
          .withStatus(status),
      ),
    )
  }

  fun stubCreateActivityWithDuplicateError(
    activityScheduleId: Long,
    nomisCourseActivityId: Long,
    duplicateNomisCourseActivityId: Long,
  ) {
    stubFor(
      post("/mapping/activities")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
            { 
              "status": 409,
              "errorCode": 1409,
              "userMessage": "Conflict: Activity mapping already exists",
              "moreInfo": {
                "existing": {
                  "activityScheduleId": $activityScheduleId,
                  "nomisCourseActivityId": $nomisCourseActivityId,
                  "mappingType": "ACTIVITY_CREATED"
                },
                "duplicate": {
                  "activityScheduleId": $activityScheduleId,
                  "nomisCourseActivityId": $duplicateNomisCourseActivityId,
                  "mappingType": "ACTIVITY_CREATED"
                }
              }
            }
              """.trimMargin(),
            )
            .withStatus(409),
        ),
    )
  }

  fun stubCreateActivityWithErrorFollowedBySuccess(status: Int = 500) {
    stubFor(
      post("/mapping/activities")
        .inScenario("Retry Mapping Activities Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("""{ "status": $status, "userMessage": "id already exists" }""")
            .withStatus(status),
        ).willSetStateTo("Cause Mapping Activities Success"),
    )

    stubFor(
      post("/mapping/activities")
        .inScenario("Retry Mapping Activities Scenario")
        .whenScenarioStateIs("Cause Mapping Activities Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetMappings(activityScheduleId: Long, response: String) {
    stubFor(
      get("/mapping/activities/activity-schedule-id/$activityScheduleId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200),
      ),
    )
  }

  fun stubGetMappingsWithError(activityScheduleId: Long, status: Int = 500) {
    stubFor(
      get("/mapping/activities/activity-schedule-id/$activityScheduleId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id does not exist" }""")
          .withStatus(status),
      ),
    )
  }

  fun stubGetScheduleInstanceMapping(scheduledInstanceId: Long, response: String) {
    stubFor(
      get("/mapping/activities/schedules/scheduled-instance-id/$scheduledInstanceId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200),
      ),
    )
  }

  fun stubGetScheduledInstanceMappingWithError(scheduledInstanceId: Long, status: Int = 500) {
    stubFor(
      get("/mapping/activities/schedules/scheduled-instance-id/$scheduledInstanceId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id does not exist" }""")
          .withStatus(status),
      ),
    )
  }

  fun stubDeleteMappingsGreaterThan(maxCourseScheduleId: Long) {
    stubFor(
      delete("/mapping/schedules/max-nomis-schedule-id/$maxCourseScheduleId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubDeleteMappingsGreaterThanError(maxCourseScheduleId: Long, status: Int = 503) {
    stubFor(
      delete("/mapping/schedules/max-nomis-schedule-id/$maxCourseScheduleId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id does not exist" }""")
          .withStatus(status),
      ),
    )
  }

  fun stubCreateAppointment() {
    stubFor(
      post("/mapping/appointments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateAppointmentWithError(status: Int = 500) {
    stubFor(
      post("/mapping/appointments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id already exists" }""")
          .withStatus(status),
      ),
    )
  }

  fun stubCreateAppointmentWithDuplicateError(
    appointmentInstanceId: Long,
    nomisEventId: Long?,
    duplicateNomisEventId: Long,
  ) {
    stubFor(
      post("/mapping/appointments")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
            { 
              "status": 409,
              "errorCode": 1409,
              "userMessage": "Conflict: Appointment mapping already exists",
              "moreInfo": {
                ${
                if (nomisEventId != null) {
                  """ "existing": {
                                  "appointmentInstanceId": $appointmentInstanceId,
                                  "nomisEventId": $nomisEventId
                                },"""
                } else {
                  ""
                }
              }
                "duplicate": {
                  "appointmentInstanceId": $appointmentInstanceId,
                  "nomisEventId": $duplicateNomisEventId
                }
              }
            }
              """.trimMargin(),
            )
            .withStatus(409),
        ),
    )
  }

  fun stubGetMappingGivenAppointmentInstanceId(id: Long, response: String) {
    stubFor(
      get("/mapping/appointments/appointment-instance-id/$id").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200),
      ),
    )
  }

  fun stubGetMappingGivenAppointmentInstanceIdWithError(id: Long, status: Int = 500) {
    stubFor(
      get("/mapping/appointments/appointment-instance-id/$id").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id does not exist" }""")
          .withStatus(status),
      ),
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
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause Mapping Appointment Success"),
    )

    stubFor(
      post("/mapping/appointments")
        .inScenario("Retry Mapping Appointment Scenario")
        .whenScenarioStateIs("Cause Mapping Appointment Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withFixedDelay(1500),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubDeleteAppointmentMapping(id: Long) {
    stubFor(
      delete("/mapping/appointments/appointment-instance-id/$id").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun postCountFor(url: String) = this.findAll(WireMock.postRequestedFor(WireMock.urlEqualTo(url))).count()
  fun stubCreateSentencingAdjustment() {
    stubFor(
      post("/mapping/sentencing/adjustments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateSentencingAdjustmentWithError(status: Int) {
    stubFor(
      post("/mapping/sentencing/adjustments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id already exists" }""")
          .withStatus(status),
      ),
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
              }""",
          )
          .withStatus(200),
      ),
    )
  }

  fun stubDeleteByAdjustmentId(
    adjustmentId: String,
  ) {
    stubFor(
      delete("/mapping/sentencing/adjustments/adjustment-id/$adjustmentId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubDeleteByAdjustmentIdWithError(adjustmentId: String, status: Int) {
    stubFor(
      delete("/mapping/sentencing/adjustments/adjustment-id/$adjustmentId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "not here" }""")
          .withStatus(status),
      ),
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
              }""",
          )
          .withStatus(status),
      ),
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
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause Mapping Adjustments Success"),
    )

    stubFor(
      post("/mapping/sentencing/adjustments")
        .inScenario("Retry Mapping Adjustments Scenario")
        .whenScenarioStateIs("Cause Mapping Adjustments Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withFixedDelay(1500),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubCreateSentencingAdjustmentWithDuplicateError(
    adjustmentId: String,
    nomisAdjustmentId: Long,
    duplicateNomisAdjustmentId: Long,
  ) {
    stubFor(
      post("/mapping/sentencing/adjustments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            { 
              "status": 409,
              "errorCode": 1409,
              "userMessage": "Conflict: Sentence adjustment mapping already exist",
              "moreInfo": {
                "existing": {
                  "adjustmentId": "$adjustmentId",
                  "nomisAdjustmentId": $nomisAdjustmentId,
                  "nomisAdjustmentCategory": "SENTENCE",
                  "mappingType": "SENTENCING_CREATED"
                },
                "duplicate": {
                  "adjustmentId": "$adjustmentId",
                  "nomisAdjustmentId": $duplicateNomisAdjustmentId,
                  "nomisAdjustmentCategory": "SENTENCE",
                  "mappingType": "SENTENCING_CREATED"
                }
              }
            }
            """.trimMargin(),
          )
          .withStatus(409),
      ),
    )
  }

  fun stubCreateAdjudication() {
    stubFor(
      post("/mapping/adjudications").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubGetByChargeNumberWithError(chargeNumber: String, status: Int) {
    stubFor(
      get("/mapping/adjudications/charge-number/$chargeNumber").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              { 
                "userMessage": "some error"
              }""",
          )
          .withStatus(status),
      ),
    )
  }

  fun stubGetByChargeNumber(
    chargeNumber: String,
    adjudicationNumber: Long,
    chargeSequence: Int = 1,
  ) {
    stubFor(
      get("/mapping/adjudications/charge-number/$chargeNumber").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            { 
            "chargeSequence": $chargeSequence,  
            "chargeNumber": "$chargeNumber",  
            "adjudicationNumber": $adjudicationNumber,  
            "mappingType": "ADJUDICATION_CREATED",  
            "whenCreated": "2020-01-01T00:00:00"
              }""",
          )
          .withStatus(200),
      ),
    )
  }
  fun stubGetByChargeNumberNotFoundFollowedByFound(
    chargeNumber: String,
    adjudicationNumber: Long,
    chargeSequence: Int = 1,
  ) {
    stubFor(
      get("/mapping/adjudications/charge-number/$chargeNumber")
        .inScenario("Charge mapping not found then found")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
            { 
              }""",
            )
            .withStatus(404),
        ).willSetStateTo("Found mapping"),
    )
    stubFor(
      get("/mapping/adjudications/charge-number/$chargeNumber")
        .inScenario("Charge mapping not found then found")
        .whenScenarioStateIs("Found mapping")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
            { 
            "chargeSequence": $chargeSequence,  
            "chargeNumber": "$chargeNumber",  
            "adjudicationNumber": $adjudicationNumber,  
            "mappingType": "ADJUDICATION_CREATED",  
            "whenCreated": "2020-01-01T00:00:00"
              }""",
            )
            .withStatus(200),
        ),
    )
  }

  fun stubCreateAdjudicationWithErrorFollowedBySlowSuccess() {
    stubFor(
      post("/mapping/adjudications")
        .inScenario("Retry Mapping Adjudication Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause Mapping Adjudication Success"),
    )

    stubFor(
      post("/mapping/adjudications")
        .inScenario("Retry Mapping Adjudication Scenario")
        .whenScenarioStateIs("Cause Mapping Adjudication Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withFixedDelay(1500),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubCreateHearing() {
    stubFor(
      post("/mapping/hearings").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubGetByDpsHearingIdWithError(hearingId: String, status: Int) {
    stubFor(
      get("/mapping/hearings/dps/$hearingId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
              { 
                "userMessage": "some error"
              }""",
          )
          .withStatus(status),
      ),
    )
  }

  fun stubGetByDpsHearingId(dpsHearingId: String, nomisHearingId: Long) {
    stubFor(
      get("/mapping/hearings/dps/$dpsHearingId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(
            """
            { 
            "dpsHearingId": $dpsHearingId,  
            "nomisHearingId": $nomisHearingId,  
            "mappingType": "ADJUDICATION_CREATED",  
            "whenCreated": "2020-01-01T00:00:00"
              }""",
          )
          .withStatus(200),
      ),
    )
  }
  fun stubGetByDpsHearingIdNotFoundFollowedByFound(dpsHearingId: String, nomisHearingId: Long) {
    stubFor(
      get("/mapping/hearings/dps/$dpsHearingId")
        .inScenario("Hearing mapping not found then found for $dpsHearingId")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
            { 
              }""",
            )
            .withStatus(404),
        ).willSetStateTo("Mapping found"),
    )
    stubFor(
      get("/mapping/hearings/dps/$dpsHearingId")
        .inScenario("Hearing mapping not found then found for $dpsHearingId")
        .whenScenarioStateIs("Mapping found")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
            { 
            "dpsHearingId": $dpsHearingId,  
            "nomisHearingId": $nomisHearingId,  
            "mappingType": "ADJUDICATION_CREATED",  
            "whenCreated": "2020-01-01T00:00:00"
              }""",
            )
            .withStatus(200),
        ),
    )
  }

  fun stubCreateHearingWithErrorFollowedBySlowSuccess() {
    stubFor(
      post("/mapping/hearings")
        .inScenario("Retry Mapping Hearing Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause Mapping Hearing Success"),
    )

    stubFor(
      post("/mapping/hearings")
        .inScenario("Retry Mapping Hearing Scenario")
        .whenScenarioStateIs("Cause Mapping Hearing Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withFixedDelay(1500),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubHearingDeleteByDpsHearingId(dpsHearingId: String) {
    stubFor(
      delete("/mapping/hearings/dps/$dpsHearingId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubCreatePunishments() {
    stubFor(
      post("/mapping/punishments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreatePunishmentsWithError(status: Int = 500) {
    stubFor(
      post("/mapping/punishments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "all gone wrong" }""")
          .withStatus(status),
      ),
    )
  }

  fun stubCreatePunishmentsWithDuplicateError(
    dpsPunishmentId: String,
    nomisBookingId: Long,
    nomisSanctionSequence: Int,
    duplicateDpsPunishmentId: String,
    duplicateNomisBookingId: Long,
    duplicateNomisSanctionSequence: Int,
  ) {
    stubFor(
      post("/mapping/punishments")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
            { 
              "status": 409,
              "errorCode": 1409,
              "userMessage": "Conflict: Punishment mapping already exists",
               "moreInfo": {
                "existing": {
                  "dpsPunishmentId": $dpsPunishmentId,
                  "nomisBookingId": "$nomisBookingId",
                  "nomisSanctionSequence": "$nomisSanctionSequence"
                  },
                "duplicate": {
                  "dpsPunishmentId": $duplicateDpsPunishmentId,
                  "nomisBookingId": "$duplicateNomisBookingId",
                  "nomisSanctionSequence": "$duplicateNomisSanctionSequence"
                }
              }
            }
              """.trimMargin(),
            )
            .withStatus(409),
        ),
    )
  }

  fun stubCreatePunishmentsWithErrorFollowedBySuccess() {
    stubFor(
      post("/mapping/punishments")
        .inScenario("Retry Mapping Adjudication Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause Mapping Adjudication Success"),
    )

    stubFor(
      post("/mapping/punishments")
        .inScenario("Retry Mapping Adjudication Scenario")
        .whenScenarioStateIs("Cause Mapping Adjudication Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withFixedDelay(1500),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubUpdatePunishments() {
    stubFor(
      put("/mapping/punishments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubUpdatePunishmentsWithError(status: Int = 500) {
    stubFor(
      put("/mapping/punishments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "all gone wrong" }""")
          .withStatus(status),
      ),
    )
  }

  fun stubUpdatePunishmentsWithDuplicateError(
    dpsPunishmentId: String,
    nomisBookingId: Long,
    nomisSanctionSequence: Int,
    duplicateDpsPunishmentId: String,
    duplicateNomisBookingId: Long,
    duplicateNomisSanctionSequence: Int,
  ) {
    stubFor(
      put("/mapping/punishments")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """
            { 
              "status": 409,
              "errorCode": 1409,
              "userMessage": "Conflict: Punishment mapping already exists",
               "moreInfo": {
                "existing": {
                  "dpsPunishmentId": $dpsPunishmentId,
                  "nomisBookingId": "$nomisBookingId",
                  "nomisSanctionSequence": "$nomisSanctionSequence"
                  },
                "duplicate": {
                  "dpsPunishmentId": $duplicateDpsPunishmentId,
                  "nomisBookingId": "$duplicateNomisBookingId",
                  "nomisSanctionSequence": "$duplicateNomisSanctionSequence"
                }
              }
            }
              """.trimMargin(),
            )
            .withStatus(409),
        ),
    )
  }

  fun stubUpdatePunishmentsWithErrorFollowedBySuccess() {
    stubFor(
      put("/mapping/punishments")
        .inScenario("Retry Mapping Adjudication Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause Mapping Adjudication Success"),
    )

    stubFor(
      put("/mapping/punishments")
        .inScenario("Retry Mapping Adjudication Scenario")
        .whenScenarioStateIs("Cause Mapping Adjudication Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withFixedDelay(1500),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetPunishments(dpsPunishmentId: String, nomisBookingId: Long = 1234, nomisSanctionSequence: Int = 2) {
    stubFor(
      get("/mapping/punishments/$dpsPunishmentId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200)
          .withBody(
            // language=json
            """ 
              {
                "nomisBookingId": $nomisBookingId,
                "nomisSanctionSequence": $nomisSanctionSequence,
                "dpsPunishmentId": "123456",
                "mappingType": "ADJUDICATION_CREATED",
                "whenCreated": "2021-07-05T10:35:17"
              }
            
            """.trimIndent(),
          ),
      ),
    )
  }

  fun stubGetPunishmentsWithError(dpsPunishmentId: String, status: Int = 500) {
    stubFor(
      get("/mapping/punishments/$dpsPunishmentId").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "all gone wrong" }""")
          .withStatus(status),
      ),
    )
  }

  fun stubDeleteMappingsForAdjudication() {
    stubFor(
      post("/mapping/adjudications/delete-mappings").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  // *************************************************** Non-Associations **********************************************

  fun stubCreateNonAssociation() {
    stubFor(
      post("/mapping/non-associations").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubGetMappingGivenNonAssociationId(id: Long, response: String) {
    stubFor(
      get("/mapping/non-associations/non-association-id/$id").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(response)
          .withStatus(200),
      ),
    )
  }

  fun stubGetMappingGivenNonAssociationIdWithError(id: Long, status: Int = 500) {
    stubFor(
      get("/mapping/non-associations/non-association-id/$id").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id does not exist" }""")
          .withStatus(status),
      ),
    )
  }

  fun stubGetThirdParties(oldOffenderNo: String, newOffenderNo: String, response: String) {
    stubFor(
      get("/mapping/non-associations/find/common-between/$oldOffenderNo/and/$newOffenderNo")
        .willReturn(okJson(response)),
    )
  }

  fun stubSetSequence(id: Long, sequence: Int) {
    stubFor(
      put("/mapping/non-associations/non-association-id/$id/sequence/$sequence")
        .willReturn(ok()),
    )
  }

  fun stubCreateNonAssociationWithErrorFollowedBySlowSuccess() {
    stubFor(
      post("/mapping/non-associations")
        .inScenario("Retry Mapping NonAssociation Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause Mapping NonAssociation Success"),
    )

    stubFor(
      post("/mapping/non-associations")
        .inScenario("Retry Mapping NonAssociation Scenario")
        .whenScenarioStateIs("Cause Mapping NonAssociation Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withFixedDelay(1500),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubDeleteNonAssociationMapping(id: Long) {
    stubFor(
      delete("/mapping/non-associations/non-association-id/$id").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubPutMergeNonAssociation(from: String, to: String) {
    stubFor(
      put("/mapping/non-associations/merge/from/$from/to/$to").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200),
      ),
    )
  }

  fun stubUpdateList(from: String, to: String) {
    stubFor(
      put("/mapping/non-associations/update-list/from/$from/to/$to").willReturn(ok()),
    )
  }

  // *************************************************** Locations **********************************************

  fun stubCreateLocation() {
    stubFor(
      post("/mapping/locations").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateLocationWithErrorFollowedBySlowSuccess() {
    stubFor(
      post("/mapping/locations")
        .inScenario("Retry Mapping Location Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause Mapping Location Success"),
    )

    stubFor(
      post("/mapping/locations")
        .inScenario("Retry Mapping Location Scenario")
        .whenScenarioStateIs("Cause Mapping Location Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201)
            .withFixedDelay(1500),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetMappingGivenDpsLocationId(id: String, response: String) {
    stubFor(
      get("/mapping/locations/dps/$id").willReturn(okJson(response)),
    )
  }

  fun stubGetMappingGivenNomisLocationId(id: Long, response: String) {
    stubFor(
      get("/mapping/locations/nomis/$id").willReturn(okJson(response)),
    )
  }

  fun stubGetMappingGivenDpsLocationIdWithError(id: String, status: Int = 500) {
    stubFor(
      get("/mapping/locations/dps/$id").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody("""{ "status": $status, "userMessage": "id does not exist" }""")
          .withStatus(status),
      ),
    )
  }

  fun stubDeleteLocationMapping(id: String) {
    stubFor(
      delete("/mapping/locations/dps/$id").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(204),
      ),
    )
  }

  fun stubMappingCreateFailureFollowedBySuccess(url: String, method: (UrlPattern) -> MappingBuilder = WireMock::post) {
    stubFor(
      method(urlPathMatching(url))
        .inScenario("Retry create Scenario")
        .whenScenarioStateIs(STARTED)
        .willReturn(
          aResponse()
            .withStatus(500) // request unsuccessful with status code 500
            .withHeader("Content-Type", "application/json"),
        )
        .willSetStateTo("Cause create Success"),
    )

    stubFor(
      method(urlPathMatching(url))
        .inScenario("Retry create Scenario")
        .whenScenarioStateIs("Cause create Success")
        .willReturn(created())
        .willSetStateTo(STARTED),
    )
  }
}
