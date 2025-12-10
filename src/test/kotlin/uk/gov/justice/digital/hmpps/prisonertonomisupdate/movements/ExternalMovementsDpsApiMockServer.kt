package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsDpsApiExtension.Companion.dpsExternalMovementsServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsDpsApiExtension.Companion.objectMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.Location
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.SyncAtAndBy
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.SyncAtAndByWithPrison
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.SyncReadTapAuthorisation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.SyncReadTapMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.SyncReadTapMovement.Direction.OUT
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.SyncReadTapOccurrence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.SyncReadTapOccurrenceAuthorisation
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDateTime
import java.util.*

class ExternalMovementsDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {
    @JvmField
    val dpsExternalMovementsServer = ExternalMovementsDpsApiMockServer()
    lateinit var objectMapper: ObjectMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsExternalMovementsServer.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    dpsExternalMovementsServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsExternalMovementsServer.stop()
  }
}

class ExternalMovementsDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8102
  }

  private val now = LocalDateTime.now()
  private val today = now.toLocalDate()
  private val tomorrow = now.plusDays(1)

  fun tapAuthorisation(id: UUID = UUID.randomUUID()) = SyncReadTapAuthorisation(
    id = id,
    repeat = true,
    fromDate = today,
    toDate = today,
    start = today,
    end = today,
    occurrences = listOf(),
    personIdentifier = "USER1",
    statusCode = "PENDING",
    prisonCode = "LEI",
    absenceReasonCode = "R2",
    created = SyncAtAndBy(now, "USER1"),
    absenceTypeCode = "SR",
    absenceSubTypeCode = "RDR",
    notes = "Some notes",
    comments = "Some notes",
    accompaniedByCode = "U",
  )

  fun tapOccurrence(id: UUID = UUID.randomUUID(), authorisationId: UUID) = SyncReadTapOccurrence(
    id = id,
    authorisation = SyncReadTapOccurrenceAuthorisation(
      id = authorisationId,
      personIdentifier = "A1234AA",
      prisonCode = "LEI",
    ),
    statusCode = "SCHEDULED",
    start = now,
    releaseAt = now,
    end = tomorrow,
    returnBy = tomorrow,
    location = Location(
      description = "Agency name",
      address = "agency address",
      postcode = "agency postcode",
      uprn = 1,
    ),
    accompaniedByCode = "U",
    transportCode = "TAX",
    absenceReasonCode = "R2",
    created = SyncAtAndBy(at = now, by = "USER1"),
    notes = "Tap occurrence comment",
    comments = "Tap occurrence comment",
  )

  fun tapMovement(id: UUID = UUID.randomUUID(), occurrenceId: UUID = UUID.randomUUID()) = SyncReadTapMovement(
    id = id,
    occurrenceId = occurrenceId,
    occurredAt = now,
    direction = OUT,
    absenceReasonCode = "R2",
    location = Location(
      description = "Agency name",
      address = "agency address",
      postcode = "agency postcode",
      uprn = 1,
    ),
    accompaniedByCode = "U",
    accompaniedByComments = "Unaccompanied movement notes",
    comments = "movement notes",
    personIdentifier = "A1234AA",
    created = SyncAtAndByWithPrison(at = now, by = "USER1", prisonCode = "LEI"),
  )

  fun stubGetTapAuthorisation(id: UUID, response: SyncReadTapAuthorisation = tapAuthorisation(id)) {
    dpsExternalMovementsServer.stubFor(
      get("/sync/temporary-absence-authorisations/$id")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetTapAuthorisationError(id: UUID, status: Int = 500, error: ErrorResponse = ErrorResponse(status = status)) {
    dpsExternalMovementsServer.stubFor(
      get("/sync/temporary-absence-authorisations/$id")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubGetTapOccurrence(id: UUID, authorisationId: UUID, response: SyncReadTapOccurrence = tapOccurrence(id, authorisationId)) {
    dpsExternalMovementsServer.stubFor(
      get("/sync/temporary-absence-occurrences/$id")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetTapOccurrenceError(id: UUID, status: Int = 500, error: ErrorResponse = ErrorResponse(status = status)) {
    dpsExternalMovementsServer.stubFor(
      get("/sync/temporary-absence-occurrences/$id")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubGetTapMovement(id: UUID, occurrenceId: UUID, response: SyncReadTapMovement = tapMovement(id, occurrenceId)) {
    dpsExternalMovementsServer.stubFor(
      get("/sync/temporary-absence-movements/$id")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetTapMovementError(id: UUID, status: Int = 500, error: ErrorResponse = ErrorResponse(status = status)) {
    dpsExternalMovementsServer.stubFor(
      get("/sync/temporary-absence-movements/$id")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }
}
