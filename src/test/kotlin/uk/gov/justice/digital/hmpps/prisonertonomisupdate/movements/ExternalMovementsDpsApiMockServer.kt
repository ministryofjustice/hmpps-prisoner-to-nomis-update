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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.UkgovjusticedigitalhmppsexternalmovementsapisyncTapMovement.Direction
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDateTime
import java.util.*
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.UkgovjusticedigitalhmppsexternalmovementsapimodellocationLocation as Location
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.UkgovjusticedigitalhmppsexternalmovementsapisyncTapAuthorisation as TapAuthorisation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.UkgovjusticedigitalhmppsexternalmovementsapisyncTapMovement as TapMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.UkgovjusticedigitalhmppsexternalmovementsapisyncTapOccurrence as TapOccurrence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.UkgovjusticedigitalhmppsexternalmovementsapisyncTapOccurrenceAuthorisation as TapOccurrenceAuthorisation

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

  fun tapAuthorisation(id: UUID = UUID.randomUUID()) = TapAuthorisation(
    id = id,
    repeat = true,
    fromDate = today,
    toDate = today,
    occurrences = listOf(),
    personIdentifier = "USER1",
    statusCode = "PENDING",
    prisonCode = "LEI",
    absenceReasonCode = "R2",
    submittedAt = now,
    absenceTypeCode = "SR",
    absenceSubTypeCode = "RDR",
    notes = "Some notes",
  )

  fun tapOccurrence(id: UUID = UUID.randomUUID(), authorisationId: UUID) = TapOccurrence(
    id = id,
    authorisation = TapOccurrenceAuthorisation(
      id = authorisationId,
      statusCode = "APPROVED",
      absenceReasonCode = "R2",
      repeat = true,
      submittedAt = now,
      absenceTypeCode = "SR",
      absenceSubTypeCode = "RDR",
    ),
    statusCode = "APPROVED",
    releaseAt = now,
    returnBy = tomorrow,
    location = Location(
      description = "Agency name",
      address = "agency address",
      postcode = "agency postcode",
      uprn = "uprn",
    ),
    accompaniedByCode = "U",
    transportCode = "TAX",
  )

  fun tapMovement(id: UUID = UUID.randomUUID(), occurrenceId: UUID = UUID.randomUUID()) = TapMovement(
    id = id,
    occurrenceId = occurrenceId,
    occurredAt = now,
    direction = Direction.OUT,
    absenceReasonCode = "R2",
    location = Location(
      description = "Agency name",
      address = "agency address",
      postcode = "agency postcode",
      uprn = "uprn",
    ),
    accompaniedByCode = "U",
    accompaniedByNotes = "Unaccompanied movement notes",
    notes = "movement notes",
    recordedByPrisonCode = "LEI",
  )

  fun stubGetTapAuthorisation(id: UUID, response: TapAuthorisation = tapAuthorisation(id)) {
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

  fun stubGetTapOccurrence(id: UUID, authorisationId: UUID, response: TapOccurrence = tapOccurrence(id, authorisationId)) {
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

  fun stubGetTapMovement(id: UUID, occurrenceId: UUID, response: TapMovement = tapMovement(id, occurrenceId)) {
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
