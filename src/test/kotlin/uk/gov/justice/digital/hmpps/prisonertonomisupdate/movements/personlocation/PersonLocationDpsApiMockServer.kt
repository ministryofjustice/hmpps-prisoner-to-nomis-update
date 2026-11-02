package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.test.context.junit.jupiter.SpringExtension.getApplicationContext
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.PersonLocationDpsApiExtension.Companion.dpsPersonLocationServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.PersonLocationDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personlocation.model.CodedReason
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personlocation.model.Court
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personlocation.model.Prison
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personlocation.model.ReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personlocation.model.ReconciliationSeries
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personlocation.model.SyncCustodialSeries
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personlocation.model.SyncExternalJourney
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personlocation.model.SyncExternalMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.getRequestBodies
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.getRequestBody
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDateTime
import java.util.*

class PersonLocationDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    private var enableResetBeforeEach = true

    @JvmField
    val dpsPersonLocationServer = PersonLocationDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper

    fun resetAndDisableResetBeforeEach() {
      enableResetBeforeEach = false
      dpsPersonLocationServer.resetAll()
    }
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsPersonLocationServer.start()
    jsonMapper = (getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    if (enableResetBeforeEach) dpsPersonLocationServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsPersonLocationServer.stop()
    enableResetBeforeEach = true
  }
}

@Component
class PersonLocationDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8111

    @Suppress("unused")
    inline fun <reified T> getRequestBody(pattern: RequestPatternBuilder): T = dpsPersonLocationServer.getRequestBody(pattern, jsonMapper)
    inline fun <reified T> getRequestBodies(pattern: RequestPatternBuilder): List<T> = dpsPersonLocationServer.getRequestBodies(pattern, jsonMapper)

    fun reconciliationResponse(
      bookingId: Long = 12345L,
      dpsCustodialSeriesId: UUID = UUID.randomUUID(),
      movementIds: List<Pair<Int, UUID>> = listOf(1 to UUID.randomUUID(), 2 to UUID.randomUUID()),
    ) = ReconciliationResponse(
      series = listOf(
        ReconciliationSeries(
          series = SyncCustodialSeries(
            status = SyncCustodialSeries.Status.OPEN,
            isActive = true,
            openedAt = LocalDateTime.now(),
            legacyBookingId = bookingId,
            legacyBookingReference = "B12435",
            dpsId = dpsCustodialSeriesId,
            closedAt = null,
            notes = "custodial series notes",
          ),
          movements = listOf(
            SyncExternalMovement(
              type = SyncExternalMovement.Type.ARRIVAL,
              reason = CodedReason(
                description = "Transfer In from Other Establishment",
                reasonType = "CodedReason",
                code = "INT",
              ),
              occurredAt = LocalDateTime.now(),
              from = Court(
                description = "Sheffield Crown Court",
                locationType = "Court",
                code = "SHEFCC",
              ),
              journey = SyncExternalJourney(
                type = SyncExternalJourney.Type.ADMISSION,
                scheduleReference = null,
              ),
              dpsId = movementIds[0].second,
              dpsCustodySeriesId = dpsCustodialSeriesId,
              legacyBookingId = bookingId,
              legacySequenceNumber = movementIds[0].first,
              to = Prison(
                description = "Brixton",
                locationType = "Prison",
                code = "BXI",
              ),
              notes = "movement ADM notes",
            ),
            SyncExternalMovement(
              type = SyncExternalMovement.Type.DEPARTURE,
              reason = CodedReason(
                description = "Court Appearance",
                reasonType = "CodedReason",
                code = "CRT",
              ),
              occurredAt = LocalDateTime.now(),
              from = Prison(
                description = "Brixton",
                locationType = "Prison",
                code = "BXI",
              ),
              journey = SyncExternalJourney(
                type = SyncExternalJourney.Type.COURT_APPEARANCE,
                scheduleReference = "urn:court-appearance-scheduler:court-appearance:019ef99d-9326-73f8-8b96-27671a52287b",
              ),
              dpsId = movementIds[1].second,
              dpsCustodySeriesId = dpsCustodialSeriesId,
              legacyBookingId = bookingId,
              legacySequenceNumber = movementIds[1].first,
              to = Court(
                description = "Leeds Crown Court",
                locationType = "Court",
                code = "LEEDCC",
              ),
              notes = "movement CRT notes",
            ),
          ),
        ),
      ),
    )
  }

  fun stubReconciliation(
    personIdentifier: String = "A1234BC",
    response: ReconciliationResponse = reconciliationResponse(),
    status: HttpStatus = HttpStatus.CREATED,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    dpsPersonLocationServer.stubFor(
      get("/reconciliation/external-movements/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(status.value())
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(if (status == HttpStatus.CREATED) response else error)),
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
