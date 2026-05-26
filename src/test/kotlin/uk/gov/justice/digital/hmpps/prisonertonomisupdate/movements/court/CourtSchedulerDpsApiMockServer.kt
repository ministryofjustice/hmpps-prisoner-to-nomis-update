package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtscheduler.model.CourtEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtscheduler.model.CourtEventMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtscheduler.model.ReconciliationCourtEvent
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.courtscheduler.model.ReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerDpsApiExtension.Companion.courtSchedulerDpsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court.CourtSchedulerDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDateTime
import java.util.*

class CourtSchedulerDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val courtSchedulerDpsApiServer = CourtSchedulerDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    courtSchedulerDpsApiServer.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    courtSchedulerDpsApiServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    courtSchedulerDpsApiServer.stop()
  }
}

class CourtSchedulerDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8105

    private val now = LocalDateTime.now()
    private val yesterday = now.minusDays(1)
    private val tomorrow = now.plusDays(1)

    fun reconciliation(
      courtEvents: List<ReconciliationCourtEvent> = listOf(
        ReconciliationCourtEvent(
          courtEvent = courtEvent(),
          movements = listOf(
            courtEventMovement(),
            courtEventMovement().copy(fromAgencyId = "LEEDMC", toAgencyId = "BXI", directionCode = "IN"),
          ),
        ),
      ),
      unscheduledMovements: List<CourtEventMovement> = listOf(
        courtEventMovement(),
        courtEventMovement().copy(fromAgencyId = "LEEDMC", toAgencyId = "BXI", directionCode = "IN"),
      ),
    ) = ReconciliationResponse(
      courtEvents = courtEvents,
      unscheduledMovements = unscheduledMovements,
    )

    fun courtEvent(
      id: UUID = UUID.randomUUID(),
    ) = CourtEvent(
      dpsId = id,
      prisonCodeAtTimeOfScheduling = "BXI",
      agyLocId = "LEEDMC",
      eventDate = yesterday.toLocalDate(),
      startTime = "$yesterday",
      courtEventType = "CRT",
      eventStatus = "COMP",
      commentText = "court event comment",
      externalReferenceUrn = "some-ext-ref-urn",
    )

    fun courtEventMovement(
      id: UUID = UUID.randomUUID(),
      fromAgency: String = "BXI",
      directionCode: String = "OUT",
      movementTime: LocalDateTime = yesterday,
      movementReasonCode: String = "CRT",
      toAgency: String = "LEEDMC",
    ) = CourtEventMovement(
      dpsId = id,
      movementDate = movementTime.toLocalDate(),
      movementTime = "$movementTime",
      directionCode = directionCode,
      movementReasonCode = movementReasonCode,
      fromAgencyId = fromAgency,
      toAgencyId = toAgency,
      commentText = "court movement comment",
    )
  }

  fun stubGetCourtSchedulerReconciliation(personIdentifier: String, response: ReconciliationResponse = reconciliation()) {
    courtSchedulerDpsApiServer.stubFor(
      get("/reconciliation/court-appearances/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetCourtSchedulerReconciliation(personIdentifier: String, status: Int = 500, error: ErrorResponse = ErrorResponse(status = status)) {
    courtSchedulerDpsApiServer.stubFor(
      get("/reconciliation/court-appearances/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
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
}
