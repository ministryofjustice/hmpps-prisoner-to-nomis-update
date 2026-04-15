package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.taps

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ApplicationSummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingTap
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingTapApplication
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingTapMovementIn
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingTapMovementOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingTapScheduleIn
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingTapScheduleOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingTaps
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTapMovementIn
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTapMovementInResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTapMovementOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTapMovementOutResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.MovementSummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.MovementsByDirection
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTapMovementId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTapsIdsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTapsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ScheduledOutSummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.TapSummary
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTapAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTapApplication
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTapApplicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTapScheduleOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTapScheduleOutResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class TapNomisApiMockServer(private val jsonMapper: JsonMapper) {
  companion object {
    private val now = LocalDateTime.now()
    private val today = now.toLocalDate()
    private val tomorrow = now.plusDays(1)
    private val yesterday = now.minusDays(1)

    fun upsertTapApplicationRequest() = UpsertTapApplication(
      eventSubType = "C5",
      applicationDate = now.toLocalDate(),
      fromDate = now.toLocalDate(),
      releaseTime = now,
      toDate = tomorrow.toLocalDate(),
      returnTime = tomorrow,
      applicationStatus = "APP-SCH",
      applicationType = "SINGLE",
      escortCode = "U",
      transportType = "VAN",
      comment = "Temporary absence application comment",
      prisonId = "LEI",
      contactPersonName = "Deek Sanderson",
      tapType = "RR",
      tapSubType = "RDR",
      toAddresses = listOf(),
    )

    fun upsertTapApplicationResponse() = UpsertTapApplicationResponse(12345, 56789)

    fun upsertTapScheduleOut() = UpsertTapScheduleOut(
      tapApplicationId = 56789,
      eventSubType = "C5",
      eventStatus = "SCH",
      escort = "U",
      fromPrison = "LEI",
      returnDate = tomorrow.toLocalDate(),
      returnTime = tomorrow,
      applicationDate = now,
      eventDate = now.toLocalDate(),
      startTime = now,
      comment = "Scheduled temporary absence comment",
      toAgency = "HAZLWD",
      transportType = "VAN",
      toAddress = UpsertTapAddress(id = 3456),
    )

    fun upsertTapScheduleOutResponse(eventId: Long = 131415, addressId: Long = 77, addressOwnerClass: String = "OFF") = UpsertTapScheduleOutResponse(12345, 56789, eventId, addressId, addressOwnerClass)

    fun createTapMovementOut(tapScheduleOutId: Long = 131415) = CreateTapMovementOut(
      movementDate = now.toLocalDate(),
      movementTime = now,
      movementReason = "C5",
      tapScheduleOutId = tapScheduleOutId,
      arrestAgency = "POL",
      escort = "U",
      escortText = "Temporary absence escort text",
      fromPrison = "LEI",
      toAgency = "HAZLWD",
      commentText = "Temporary absence comment",
      toCity = "765",
      toAddressId = 76543,
    )

    fun createTapMovementOutResponse(bookingId: Long = 12345, movementSequence: Int = 2) = CreateTapMovementOutResponse(bookingId, movementSequence)

    fun createTapMovementIn() = CreateTapMovementIn(
      movementDate = tomorrow.toLocalDate(),
      movementTime = tomorrow,
      movementReason = "C5",
      tapScheduleInId = 161718,
      arrestAgency = "POL",
      escort = "U",
      escortText = "Temporary absence return escort text",
      fromAgency = "HAZLWD",
      toPrison = "LEI",
      commentText = "Temporary absence return comment",
      fromAddressId = 76543,
    )

    fun createTapMovementInResponse(bookingId: Long = 12345, movementSequence: Int = 3) = CreateTapMovementInResponse(bookingId, movementSequence)

    fun createTapSummary() = TapSummary(
      applications = ApplicationSummary(count = 1),
      scheduledOuts = ScheduledOutSummary(count = 2),
      movements = MovementSummary(
        count = 18,
        scheduled = MovementsByDirection(outCount = 3, inCount = 4),
        unscheduled = MovementsByDirection(outCount = 5, inCount = 6),
      ),
    )

    fun tapIdsResponse() = OffenderTapsIdsResponse(
      applicationIds = listOf(1111),
      scheduleOutIds = listOf(2222),
      scheduleInIds = listOf(9999),
      scheduledMovementOutIds = listOf(OffenderTapMovementId(12345, 3)),
      scheduledMovementInIds = listOf(OffenderTapMovementId(12345, 4)),
      unscheduledMovementOutIds = listOf(OffenderTapMovementId(12345, 5)),
      unscheduledMovementInIds = listOf(OffenderTapMovementId(12345, 6)),
    )

    fun bookingTaps(
      movementPrison: String = "LEI",
      bookingId: Long = 12345L,
      activeBooking: Boolean = true,
      latestBooking: Boolean = true,
      taps: List<BookingTap> = listOf(tap(movementPrison = movementPrison)),
      applications: List<BookingTapApplication> = listOf(tapApplication(taps = taps)),
      unscheduledTapMovementOuts: List<BookingTapMovementOut> = listOf(
        tapMovementOut(seq = 1).copy(
          movementDate = yesterday.toLocalDate(),
          movementTime = yesterday,
        ),
      ),
      unscheduledTapMovementIns: List<BookingTapMovementIn> = listOf(
        tapMovementIn(seq = 2).copy(
          movementDate = yesterday.toLocalDate(),
          movementTime = yesterday,
        ),
      ),
    ): BookingTaps = BookingTaps(
      bookingId = bookingId,
      activeBooking = activeBooking,
      latestBooking = latestBooking,
      tapApplications = applications,
      unscheduledTapMovementOuts = unscheduledTapMovementOuts,
      unscheduledTapMovementIns = unscheduledTapMovementIns,
    )

    fun tapApplication(
      id: Long = 1,
      fromDate: LocalDate = today,
      toDate: LocalDate = tomorrow.toLocalDate(),
      status: String = "APP-SCH",
      taps: List<BookingTap> = listOf(tap(movementPrison = "LEI")),
    ) = BookingTapApplication(
      tapApplicationId = id,
      eventSubType = "C5",
      applicationDate = now.toLocalDate(),
      fromDate = fromDate,
      releaseTime = now,
      toDate = toDate,
      returnTime = tomorrow,
      applicationStatus = status,
      applicationType = "SINGLE",
      escortCode = "U",
      transportType = "VAN",
      comment = "application comment",
      prisonId = "LEI",
      toAgencyId = "COURT1",
      toAddressId = 321,
      toAddressOwnerClass = "OFF",
      toAddressDescription = "some address description",
      toFullAddress = "some full address",
      toAddressPostcode = "S1 1AA",
      contactPersonName = "Jeff",
      tapType = "RR",
      tapSubType = "SPL",
      taps = taps,
      audit = NomisAudit(
        createDatetime = now,
        createUsername = "USER",
      ),
    )

    fun tap(
      movementPrison: String = "LEI",
      tapScheduleOut: BookingTapScheduleOut = tapScheduleOut(),
      tapScheduleIn: BookingTapScheduleIn? = tapScheduleIn(),
      tapMovementOut: BookingTapMovementOut? = tapMovementOut(seq = 3).copy(
        movementDate = yesterday.toLocalDate(),
        movementTime = yesterday,
        fromPrison = movementPrison,
      ),
      tapMovementIn: BookingTapMovementIn? = tapMovementIn(seq = 4).copy(
        movementDate = now.toLocalDate(),
        movementTime = now,
        toPrison = movementPrison,
      ),
    ) = BookingTap(tapScheduleOut, tapScheduleIn, tapMovementOut, tapMovementIn)

    fun tapMovementIn(seq: Int = 2) = BookingTapMovementIn(
      sequence = seq,
      movementDate = now.toLocalDate(),
      movementTime = now,
      movementReason = "C5",
      escort = "PECS",
      escortText = "Return escort text",
      fromAgency = "COURT1",
      toPrison = "LEI",
      commentText = "Return comment text",
      fromAddressId = 321L,
      fromAddressOwnerClass = "CORP",
      fromAddressDescription = "Absence return address description",
      fromFullAddress = "Absence return full address",
      fromAddressPostcode = "S2 2AA",
      audit = NomisAudit(
        createDatetime = now,
        createUsername = "USER",
      ),
    )

    fun tapMovementOut(seq: Int = 1) = BookingTapMovementOut(
      sequence = seq,
      movementDate = now.toLocalDate(),
      movementTime = now,
      movementReason = "C6",
      arrestAgency = "POL",
      escort = "U",
      escortText = "Absence escort text",
      fromPrison = "LEI",
      toAgency = "COURT1",
      commentText = "Absence comment text",
      toAddressId = 432L,
      toAddressOwnerClass = "AGY",
      toAddressDescription = "Absence address description",
      toFullAddress = "Absence full address",
      toAddressPostcode = "S1 1AA",
      audit = NomisAudit(
        createDatetime = now,
        createUsername = "USER",
      ),
    )

    fun tapScheduleIn(id: Long = 2) = BookingTapScheduleIn(
      eventId = id,
      eventSubType = "C5",
      eventStatus = "SCH",
      escort = "PECS",
      eventDate = tomorrow.toLocalDate(),
      startTime = tomorrow,
      comment = "scheduled return comment",
      fromAgency = "COURT1",
      toPrison = "LEI",
      audit = NomisAudit(
        createDatetime = now,
        createUsername = "USER",
      ),
    )

    fun tapScheduleOut(id: Long = 1): BookingTapScheduleOut = BookingTapScheduleOut(
      eventId = id,
      eventSubType = "C5",
      eventStatus = "SCH",
      escort = "PECS",
      applicationTime = now,
      applicationDate = now,
      eventDate = yesterday.toLocalDate(),
      startTime = yesterday,
      returnDate = tomorrow.toLocalDate(),
      returnTime = tomorrow,
      comment = "scheduled absence comment",
      fromPrison = "LEI",
      toAgency = "COURT1",
      transportType = "VAN",
      toAddressId = 543L,
      toAddressOwnerClass = "CORP",
      toAddressDescription = "Schedule address description",
      toFullAddress = "Schedule full address",
      toAddressPostcode = "S1 1AA",
      contactPersonName = "Derek",
      audit = NomisAudit(
        createDatetime = now,
        createUsername = "USER",
      ),
    )

    fun offenderTapsResponse(
      movementPrison: String = "LEI",
      bookingId: Long = 12345L,
      activeBooking: Boolean = true,
      latestBooking: Boolean = true,
      taps: List<BookingTap> = listOf(tap(movementPrison = movementPrison)),
      applications: List<BookingTapApplication> = listOf(tapApplication(taps = taps)),
      unscheduledTapMovementOuts: List<BookingTapMovementOut> = listOf(tapMovementOut(seq = 1).copy(movementDate = yesterday.toLocalDate(), movementTime = yesterday)),
      unscheduledTapMovementIns: List<BookingTapMovementIn> = listOf(tapMovementIn(seq = 2).copy(movementDate = yesterday.toLocalDate(), movementTime = yesterday)),
    ): OffenderTapsResponse = OffenderTapsResponse(
      bookings = listOf(
        BookingTaps(
          bookingId = bookingId,
          activeBooking = activeBooking,
          latestBooking = latestBooking,
          tapApplications = applications,
          unscheduledTapMovementOuts = unscheduledTapMovementOuts,
          unscheduledTapMovementIns = unscheduledTapMovementIns,
        ),
      ),
    )
  }

  fun stubUpsertTapApplication(
    offenderNo: String = "A1234BC",
    response: UpsertTapApplicationResponse = upsertTapApplicationResponse(),
  ) {
    NomisApiExtension.nomisApi.stubFor(
      WireMock.put(WireMock.urlEqualTo("/movements/$offenderNo/taps/application"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubUpsertTapApplication(
    offenderNo: String = "A1234BC",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status),
  ) {
    NomisApiExtension.nomisApi.stubFor(
      WireMock.put(WireMock.urlEqualTo("/movements/$offenderNo/taps/application"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubUpsertTapScheduleOut(
    offenderNo: String = "A1234BC",
    response: UpsertTapScheduleOutResponse = upsertTapScheduleOutResponse(),
  ) {
    NomisApiExtension.nomisApi.stubFor(
      WireMock.put(WireMock.urlEqualTo("/movements/$offenderNo/taps/schedule/out"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubUpsertTapScheduleOut(
    offenderNo: String = "A1234BC",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status),
  ) {
    NomisApiExtension.nomisApi.stubFor(
      WireMock.put(WireMock.urlEqualTo("/movements/$offenderNo/taps/schedule/out"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubCreateTapMovementOut(
    offenderNo: String = "A1234BC",
    response: CreateTapMovementOutResponse = createTapMovementOutResponse(),
  ) {
    NomisApiExtension.nomisApi.stubFor(
      WireMock.post(WireMock.urlEqualTo("/movements/$offenderNo/taps/movement/out"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubCreateTapMovementOut(
    offenderNo: String = "A1234BC",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status),
  ) {
    NomisApiExtension.nomisApi.stubFor(
      WireMock.post(WireMock.urlEqualTo("/movements/$offenderNo/taps/movement/out"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubCreateTapMovementIn(
    offenderNo: String = "A1234BC",
    response: CreateTapMovementInResponse = createTapMovementInResponse(),
  ) {
    NomisApiExtension.nomisApi.stubFor(
      WireMock.post(WireMock.urlEqualTo("/movements/$offenderNo/taps/movement/in"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubCreateTapMovementIn(
    offenderNo: String = "A1234BC",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status),
  ) {
    NomisApiExtension.nomisApi.stubFor(
      WireMock.post(WireMock.urlEqualTo("/movements/$offenderNo/taps/movement/in"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubGetTapCounts(
    offenderNo: String = "A1234BC",
    response: TapSummary = createTapSummary(),
  ) {
    NomisApiExtension.nomisApi.stubFor(
      WireMock.get(WireMock.urlEqualTo("/movements/$offenderNo/taps/summary"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetTapCounts(
    offenderNo: String = "A1234BC",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status),
  ) {
    NomisApiExtension.nomisApi.stubFor(
      WireMock.get(WireMock.urlEqualTo("/movements/$offenderNo/taps/summary"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubGetTapIds(
    offenderNo: String = "A1234BC",
    response: OffenderTapsIdsResponse = tapIdsResponse(),
  ) {
    NomisApiExtension.nomisApi.stubFor(
      WireMock.get(WireMock.urlEqualTo("/movements/$offenderNo/taps/ids"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetTapIds(
    offenderNo: String = "A1234BC",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status),
  ) {
    NomisApiExtension.nomisApi.stubFor(
      WireMock.get(WireMock.urlEqualTo("/movements/$offenderNo/taps/ids"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubGetBookingTaps(
    bookingId: Long = 12345L,
    response: BookingTaps = bookingTaps(bookingId = bookingId),
  ) {
    NomisApiExtension.nomisApi.stubFor(
      WireMock.get(WireMock.urlEqualTo("/movements/booking/$bookingId/taps"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetBookingTaps(
    bookingId: Long = 12345L,
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status),
  ) {
    NomisApiExtension.nomisApi.stubFor(
      WireMock.get(WireMock.urlEqualTo("/movements/booking/$bookingId/taps"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubGetOffenderTaps(
    offenderNo: String = "A1234BC",
    response: OffenderTapsResponse = offenderTapsResponse(),
  ) {
    NomisApiExtension.nomisApi.stubFor(
      WireMock.get(WireMock.urlPathEqualTo("/movements/$offenderNo/taps")).willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetOffenderTaps(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    NomisApiExtension.nomisApi.stubFor(
      WireMock.get(WireMock.urlPathMatching("/movements/.*/taps")).willReturn(
        WireMock.aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubDeleteTapScheduleOut(
    offenderNo: String = "A1234BC",
    eventId: Long = 4321L,
  ) {
    NomisApiExtension.nomisApi.stubFor(
      WireMock.delete(WireMock.urlPathEqualTo("/movements/$offenderNo/taps/schedule/out/$eventId"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NO_CONTENT.value()),
        ),
    )
  }

  fun stubDeleteTapScheduleOut(
    offenderNo: String = "A1234BC",
    eventId: Long = 4321L,
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    NomisApiExtension.nomisApi.stubFor(
      WireMock.delete(WireMock.urlPathEqualTo("/movements/$offenderNo/taps/schedule/out/$eventId"))
        .willReturn(
          WireMock.aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = NomisApiExtension.nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = NomisApiExtension.nomisApi.verify(count, pattern)
}
