package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsDpsApiExtension.Companion.dpsExternalMovementsServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.ExternalMovementsDpsApiExtension.Companion.jsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.Location
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.MovementInOutCount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.PersonAuthorisationCount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.PersonMovementsCount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.PersonOccurrenceCount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.PersonTapCounts
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.SyncAtAndBy
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.SyncReadTapAuthorisation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.model.SyncReadTapAuthorisationOccurrence
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
    lateinit var jsonMapper: JsonMapper
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsExternalMovementsServer.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
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
  private val tomorrow = now.plusDays(1)

  fun tapAuthorisation(
    id: UUID = UUID.randomUUID(),
    occurrenceCount: Int = 0,
    startTime: LocalDateTime = now,
    endTime: LocalDateTime = tomorrow,
    repeat: Boolean = true,
    statusCode: String = "PENDING",
  ) = SyncReadTapAuthorisation(
    id = id,
    repeat = repeat,
    start = startTime.toLocalDate(),
    end = endTime.toLocalDate(),
    occurrences = when (occurrenceCount) {
      0 -> listOf()
      1 -> listOf(tapAuthorisationOccurrence(start = startTime, end = endTime))
      else -> listOf(
        tapAuthorisationOccurrence(start = startTime, end = startTime.plusHours(1), location = Location(address = "some address 1", description = "some description 1", postcode = "some postcode 1", uprn = 1)),
        tapAuthorisationOccurrence(start = endTime.minusHours(6), end = endTime.minusHours(5), location = Location(address = "some address 2", description = "some description 2", postcode = "some postcode 2", uprn = 2)),
        tapAuthorisationOccurrence(start = endTime.minusHours(1), end = endTime, location = Location(address = "some address 1", description = "some description 1", postcode = "some postcode 1", uprn = 1)),
      )
    },
    personIdentifier = "USER1",
    statusCode = statusCode,
    prisonCode = "LEI",
    absenceReasonCode = "R2",
    created = SyncAtAndBy(now, "USER1"),
    absenceTypeCode = "SR",
    absenceSubTypeCode = "RDR",
    comments = "Some notes",
    accompaniedByCode = "U",
    transportCode = "VAN",
  )

  fun tapOccurrence(
    id: UUID = UUID.randomUUID(),
    authorisationId: UUID,
    location: Location,
  ) = SyncReadTapOccurrence(
    id = id,
    authorisation = SyncReadTapOccurrenceAuthorisation(
      id = authorisationId,
      personIdentifier = "A1234AA",
      prisonCode = "LEI",
    ),
    statusCode = "SCHEDULED",
    start = now,
    end = tomorrow,
    location = location,
    accompaniedByCode = "U",
    transportCode = "TAX",
    absenceReasonCode = "R2",
    created = SyncAtAndBy(at = now, by = "USER1"),
    comments = "Tap occurrence comment",
  )

  fun tapAuthorisationOccurrence(
    id: UUID = UUID.randomUUID(),
    start: LocalDateTime = now,
    end: LocalDateTime = tomorrow,
    location: Location = Location(address = "some address", postcode = "some postcode", uprn = 1, description = "some description"),
  ) = SyncReadTapAuthorisationOccurrence(
    id = id,
    statusCode = "SCHEDULED",
    start = start,
    end = end,
    location = location,
    accompaniedByCode = "U",
    transportCode = "TAX",
    absenceReasonCode = "R2",
    created = SyncAtAndBy(at = now, by = "USER1"),
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
    created = SyncAtAndBy(at = now, by = "USER1"),
    prisonCode = "LEI",
  )

  fun personTapCounts() = PersonTapCounts(
    authorisations = PersonAuthorisationCount(count = 1),
    occurrences = PersonOccurrenceCount(count = 2),
    movements = PersonMovementsCount(
      scheduled = MovementInOutCount(outCount = 3, inCount = 4),
      unscheduled = MovementInOutCount(outCount = 5, inCount = 6),
    ),
  )

  fun stubGetTapAuthorisation(id: UUID, response: SyncReadTapAuthorisation = tapAuthorisation(id)) {
    dpsExternalMovementsServer.stubFor(
      get("/sync/temporary-absence-authorisations/$id")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
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
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubGetTapOccurrence(
    id: UUID,
    authorisationId: UUID,
    location: Location = Location(description = "Agency name", address = "agency address", postcode = "agency postcode", uprn = 1),
    response: SyncReadTapOccurrence = tapOccurrence(id, authorisationId, location),
  ) {
    dpsExternalMovementsServer.stubFor(
      get("/sync/temporary-absence-occurrences/$id")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
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
            .withBody(jsonMapper.writeValueAsString(error)),
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
            .withBody(jsonMapper.writeValueAsString(response)),
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
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubGetTapReconciliation(personIdentifier: String, response: PersonTapCounts = personTapCounts()) {
    dpsExternalMovementsServer.stubFor(
      get("/reconciliation/$personIdentifier/temporary-absences")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetTapReconciliation(personIdentifier: String, status: Int = 500, error: ErrorResponse = ErrorResponse(status = status)) {
    dpsExternalMovementsServer.stubFor(
      get("/reconciliation/$personIdentifier/temporary-absences")
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }
}
