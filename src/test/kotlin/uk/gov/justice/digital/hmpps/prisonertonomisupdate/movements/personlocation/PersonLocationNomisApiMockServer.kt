package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingPersonLocations
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.MovementLocation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderPersonLocationsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonLocationBooking
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PersonLocationMovement
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDateTime

@Component
class PersonLocationNomisApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubGetOffenderPersonLocations(
    offenderNo: String = "A1234BC",
    response: OffenderPersonLocationsResponse = offenderPersonLocationsResponse(),
    status: HttpStatus = HttpStatus.OK,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/movements/$offenderNo/person-locations")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(if (status == HttpStatus.OK) response else error)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)

  companion object {
    private val now = LocalDateTime.now()
    private val yesterday = now.minusDays(1)

    fun offenderPersonLocationsResponse(
      bookingId: Long = 12345L,
      bookingBeginTime: LocalDateTime = LocalDateTime.parse("2024-01-01T10:00:00"),
      movements: List<PersonLocationMovement> = listOf(
        transferMovementIn(bookingId = bookingId, movementTime = bookingBeginTime),
        courtMovementOut(bookingId = bookingId, movementTime = bookingBeginTime.plusDays(10)),
      ),
      bookings: List<BookingPersonLocations> = listOf(
        activeBooking(bookingId = bookingId, bookingBeginTime = bookingBeginTime, movements = movements),
      ),
    ) = OffenderPersonLocationsResponse(bookings = bookings)

    fun activeBooking(
      bookingId: Long = 12345L,
      bookingBeginTime: LocalDateTime = LocalDateTime.parse("2024-01-01T10:00:00"),
      movements: List<PersonLocationMovement> = listOf(),
    ) = BookingPersonLocations(
      booking = PersonLocationBooking(
        bookingId = bookingId,
        bookingNumber = "B12345",
        activeBooking = true,
        latestBooking = true,
        bookingStatus = PersonLocationBooking.BookingStatus.OPEN,
        bookingBeginTime = bookingBeginTime,
        audit = NomisAudit(createDatetime = bookingBeginTime, createUsername = "PRISONER_MANAGER_API"),
      ),
      movements = movements,
    )

    fun transferMovementIn(
      bookingId: Long = 12345L,
      movementTime: LocalDateTime = LocalDateTime.parse("2024-01-01T10:00:00"),
    ) = PersonLocationMovement(
      bookingId = bookingId,
      sequence = 1,
      movementType = PersonLocationMovement.MovementType.ADM,
      directionCode = PersonLocationMovement.DirectionCode.IN,
      movementTime = movementTime,
      movementReason = CodeDescription(code = "INT", description = "Transfer In from Other Establishment"),
      audit = NomisAudit(
        createDatetime = movementTime,
        createUsername = "SOME_USER",
        modifyDatetime = movementTime.plusHours(1),
        modifyUserId = "PRISONER_MANAGER_API",
      ),
      from = MovementLocation(type = MovementLocation.Type.COURT, description = "Sheffield Crown Court", code = "SHEFCC"),
      to = MovementLocation(type = MovementLocation.Type.PRISON, description = "Brixton", code = "BXI"),
      commentText = "movement ADM notes",
    )

    fun courtMovementOut(
      bookingId: Long = 12345L,
      movementTime: LocalDateTime = LocalDateTime.parse("2024-01-02T10:00:00"),
    ) = PersonLocationMovement(
      bookingId = bookingId,
      sequence = 2,
      movementType = PersonLocationMovement.MovementType.CRT,
      directionCode = PersonLocationMovement.DirectionCode.OUT,
      movementTime = movementTime,
      movementReason = CodeDescription(code = "CRT", description = "Court Appearance"),
      audit = NomisAudit(createDatetime = movementTime, createUsername = "SOME_USER"),
      from = MovementLocation(type = MovementLocation.Type.PRISON, description = "Brixton", code = "NXI"),
      to = MovementLocation(type = MovementLocation.Type.COURT, description = "Leeds Crown Court", code = "LEEDCC"),
      scheduleId = 54321L,
      commentText = "movement CRT notes",
    )
  }
}
