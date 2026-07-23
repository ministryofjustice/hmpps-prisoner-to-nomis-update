package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingCourtMovementIn
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingCourtMovementOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingCourtMovements
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingCourtScheduleOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderCourtMovementsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertCourtScheduleOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertCourtScheduleOutResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDateTime

@Component
class CourtSchedulerNomisApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubGetOffenderCourtMovements(
    offenderNo: String = "A1234BC",
    response: OffenderCourtMovementsResponse = offenderCourtMovementsResponse(),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/movements/$offenderNo/court")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetOffenderCourtMovements(
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlPathMatching("/movements/.*/court")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubUpsertCourtScheduleOut(
    prisonerNumber: String = "A1234BC",
    eventId: Long = 123,
    response: UpsertCourtScheduleOutResponse = upsertCourtScheduleOutResponse(eventId = eventId),
  ) {
    nomisApi.stubFor(
      put(urlPathMatching("/movements/$prisonerNumber/court/schedule/out.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.CREATED.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubUpsertCourtScheduleOut(
    status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      put(urlPathMatching("/movements/.*/court/schedule/out.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubDeleteCourtScheduleOut(
    prisonerNumber: String = "A1234BC",
    eventId: Long = 123,
  ) {
    nomisApi.stubFor(
      delete(urlPathMatching("/movements/$prisonerNumber/court/schedule/out/$eventId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }

  fun stubDeleteCourtScheduleOut(
    prisonerNumber: String = "A1234BC",
    eventId: Long = 123,
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      delete(urlPathEqualTo("/movements/$prisonerNumber/court/schedule/out/$eventId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)

  companion object {
    private val now = LocalDateTime.now()
    private val yesterday = now.minusDays(1)

    fun offenderCourtMovementsResponse(
      bookingId: Long = 12345L,
      activeBooking: Boolean = true,
      latestBooking: Boolean = true,
      courtSchedules: List<BookingCourtScheduleOut> = listOf(bookingCourtSchedule()),
      unscheduledCourtMovementOuts: List<BookingCourtMovementOut> = listOf(bookingCourtMovementOut(seq = 1)),
      unscheduledCourtMovementIns: List<BookingCourtMovementIn> = listOf(bookingCourtMovementIn(seq = 2)),
    ): OffenderCourtMovementsResponse = OffenderCourtMovementsResponse(
      bookings = listOf(
        BookingCourtMovements(
          bookingId = bookingId,
          activeBooking = activeBooking,
          latestBooking = latestBooking,
          courtSchedules = courtSchedules,
          unscheduledCourtMovementOuts = unscheduledCourtMovementOuts,
          unscheduledCourtMovementIns = unscheduledCourtMovementIns,
        ),
      ),
    )

    fun bookingCourtSchedule(
      eventId: Long = 1,
      courtMovementOut: BookingCourtMovementOut? = bookingCourtMovementOut(seq = 3),
      courtMovementIn: BookingCourtMovementIn? = bookingCourtMovementIn(seq = 4),
      startTime: LocalDateTime = yesterday,
      court: String = "LEEDMC",
      courtCaseId: Long? = null,
    ) = BookingCourtScheduleOut(
      eventId = eventId,
      eventDate = startTime.toLocalDate(),
      startTime = startTime,
      eventType = "CRT",
      eventStatus = "COMP",
      court = court,
      audit = NomisAudit(
        createDatetime = yesterday,
        createUsername = "USER",
      ),
      courtMovementOut = courtMovementOut,
      courtMovementIn = courtMovementIn,
      comment = "Some schedule comment",
      courtCaseId = courtCaseId,
    )

    fun bookingCourtMovementOut(
      seq: Int,
      court: String? = "LEEDMC",
    ) = BookingCourtMovementOut(
      sequence = seq,
      movementDate = yesterday.toLocalDate(),
      movementTime = yesterday,
      movementReason = "CRT",
      fromPrison = "BXI",
      toCourt = court,
      audit = NomisAudit(
        createDatetime = yesterday,
        createUsername = "USER",
      ),
      commentText = "Some movement out comment",
    )

    fun bookingCourtMovementIn(seq: Int) = BookingCourtMovementIn(
      sequence = seq,
      movementDate = yesterday.toLocalDate(),
      movementTime = yesterday,
      movementReason = "CRT",
      fromCourt = "LEEDMC",
      toPrison = "BXI",
      audit = NomisAudit(
        createDatetime = yesterday,
        createUsername = "USER",
      ),
      commentText = "Some movement in comment",
    )

    fun upsertCourtScheduleOut(eventId: Long? = 123) = UpsertCourtScheduleOut(
      eventId = eventId,
      eventType = "CRT",
      eventStatus = "SCH",
      returnStatus = null,
      startTime = yesterday,
      court = "LEEDMC",
      comment = "Some schedule comment",
    )

    fun upsertCourtScheduleOutResponse(eventId: Long = 123) = UpsertCourtScheduleOutResponse(12435, eventId)
  }
}
