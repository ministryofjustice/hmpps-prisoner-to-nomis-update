package uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment.BedAssignmentDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment.model.BedAssignment
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment.model.BedAssignmentNomisMapping
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment.model.BedAssignmentSyncResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment.model.CellMovementReason
import java.time.LocalDateTime
import java.util.UUID

class BedAssignmentDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val bedAssignmentApi = BedAssignmentDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    bedAssignmentApi.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    bedAssignmentApi.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    bedAssignmentApi.stop()
  }
}

class BedAssignmentDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8112
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

  fun stubSyncBedAssignments(prisonerNumber: String, response: BedAssignmentSyncResponse = BedAssignmentSyncResponse(created = emptyList(), updated = emptyList(), deleted = emptyList())) {
    stubFor(
      put(urlPathEqualTo("/nomis-sync/bed-assignments/sync/$prisonerNumber"))
        .willReturn(okJson(jsonMapper.writeValueAsString(response))),
    )
  }

  fun stubMigrateBedAssignments(prisonerNumber: String, response: List<BedAssignmentNomisMapping> = emptyList()) {
    stubFor(
      post(urlPathEqualTo("/nomis-sync/bed-assignments/migrate/$prisonerNumber"))
        .willReturn(okJson(jsonMapper.writeValueAsString(response))),
    )
  }

  fun stubGetCellMovementReason(bookingId: Long, bedAssignmentSequence: Int, response: CellMovementReason = dpsCellMovementReason(bookingId, bedAssignmentSequence)) {
    stubFor(
      get(urlPathEqualTo("/cell-movements/$bookingId/bed-assignment/$bedAssignmentSequence"))
        .willReturn(okJson(jsonMapper.writeValueAsString(response))),
    )
  }

  fun stubGetCellMovementReasonError(bookingId: Long, bedAssignmentSequence: Int, status: Int) {
    stubFor(
      get(urlPathEqualTo("/cell-movements/$bookingId/bed-assignment/$bedAssignmentSequence"))
        .willReturn(status(status)),
    )
  }

  fun stubGetBedAssignments(prisonerNumber: String, response: List<BedAssignment> = emptyList()) {
    stubFor(
      get(urlPathEqualTo("/nomis-sync/bed-assignments/$prisonerNumber"))
        .willReturn(okJson(jsonMapper.writeValueAsString(response))),
    )
  }
}

fun dpsCellMovementReason(
  bookingId: Long = 12345,
  bedAssignmentSequence: Int = 1,
) = CellMovementReason(
  bookingId = bookingId,
  bedAssignmentSequence = bedAssignmentSequence,
  source = CellMovementReason.Source.CELL_MOVEMENTS,
  toLocationId = UUID.fromString("de91dfa7-821f-4552-a427-bf2f32eafeb0"),
  prisonerNumber = "A1234AA",
  reasonCode = "ADM",
  occurredAt = LocalDateTime.parse("2026-01-02T10:00:00"),
  recordedBy = "ME",
  movementType = CellMovementReason.MovementType.CELL_MOVE,
)
