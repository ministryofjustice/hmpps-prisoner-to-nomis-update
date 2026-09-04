package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer.TransferSchedulerDpsApiExtension.Companion.transferSchedulerDpsApiServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.transferscheduler.model.ReconciliationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.transferscheduler.model.ReconciliationTransfer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.transferscheduler.model.SyncMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.transferscheduler.model.SyncSchedule
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.transferscheduler.model.SyncTransfer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.transferscheduler.model.SyncWaitlist
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDateTime
import java.util.*

class TransferSchedulerDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val transferSchedulerDpsApiServer = TransferSchedulerDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    transferSchedulerDpsApiServer.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    transferSchedulerDpsApiServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    transferSchedulerDpsApiServer.stop()
  }
}

class TransferSchedulerDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8110

    private val now = LocalDateTime.now()
    private val yesterday = now.minusDays(1)

    fun reconciliation(
      transfers: List<ReconciliationTransfer> = listOf(
        ReconciliationTransfer(
          transfer = SyncTransfer(
            dpsId = UUID.randomUUID(),
            eventId = 123L,
            schedule = transferSchedule(),
            waitlist = transferWaitlist(),
          ),
          movement = transferMovement(),
        ),
      ),
      unscheduledMovements: List<SyncMovement> = listOf(transferMovement()),
    ) = ReconciliationResponse(transfers, unscheduledMovements)

    fun transferWaitlist() = SyncWaitlist(
      requestDate = yesterday.toLocalDate(),
      waitListStatus = "CANC",
      statusDate = now.toLocalDate(),
      transferPriority = "3",
      approved = true,
      approvedUsername = "APPROVE_USER",
      outcomeReasonCode = SyncWaitlist.OutcomeReasonCode.TRANS,
      commentText1 = "some waitlist comment",
    )

    fun transferSchedule(start: LocalDateTime = now) = SyncSchedule(
      start = start,
      eventSubType = "TRN",
      eventStatus = "SCH",
      commentText = "Some schedule comment",
      hiddenCommentText = "Some hidden comment",
      agyLocId = "BXI",
      toAgyLocId = "LEI",
      outcomeReasonCode = "ADMI",
      escortCode = "PECS",
    )

    fun transferMovement(
      dpsId: UUID = UUID.randomUUID(),
      dpsTransferId: UUID? = UUID.randomUUID(),
    ) = SyncMovement(
      dpsId = dpsId,
      dpsTransferId = dpsTransferId,
      offenderBookId = 12345L,
      movementSeq = 3,
      occurredAt = now,
      movementReasonCode = "28",
      escortCode = "PECS",
      fromAgyLocId = "BXI",
      toAgyLocId = "LEI",
      active = true,
      commentText = "some transfer movement comment",
    )

    fun syncTransfer(
      id: UUID = UUID.randomUUID(),
      eventId: Long = 123L,
      start: LocalDateTime = now,
    ) = SyncTransfer(
      dpsId = id,
      eventId = eventId,
      schedule = transferSchedule(start = start),
      waitlist = transferWaitlist(),
    )
  }

  fun stubGetTransferSchedulerReconciliation(personIdentifier: String, response: ReconciliationResponse = reconciliation()) {
    transferSchedulerDpsApiServer.stubFor(
      get("/reconciliation/transfers/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetTransferSchedulerReconciliation(personIdentifier: String, status: Int = 500, error: ErrorResponse = ErrorResponse(status = status)) {
    transferSchedulerDpsApiServer.stubFor(
      get("/reconciliation/transfers/$personIdentifier")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubGetTransferSchedule(
    id: UUID,
    start: LocalDateTime = now,
    response: SyncTransfer = syncTransfer(id = id, start = start),
  ) {
    transferSchedulerDpsApiServer.stubFor(
      get("/sync/transfers/$id")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetTransferSchedule(status: Int = 500, error: ErrorResponse = ErrorResponse(status = status)) {
    transferSchedulerDpsApiServer.stubFor(
      get(urlPathMatching("/sync/transfers/.*"))
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
