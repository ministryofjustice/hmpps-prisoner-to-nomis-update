package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.Absence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.Applications
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingTemporaryAbsences
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceReturnRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceReturnResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.Movements
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.MovementsByDirection
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsenceId
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsenceIdsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsenceSummaryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ScheduledOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ScheduledTemporaryAbsence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ScheduledTemporaryAbsenceReturn
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.TemporaryAbsence
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.TemporaryAbsenceApplication
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.TemporaryAbsenceReturn
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertScheduledTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertScheduledTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTemporaryAbsenceAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTemporaryAbsenceApplicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTemporaryAbsenceApplicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class ExternalMovementsNomisApiMockServer(private val jsonMapper: JsonMapper) {
  companion object {
    private val now = LocalDateTime.now()
    private val today = now.toLocalDate()
    private val tomorrow = now.plusDays(1)
    private val yesterday = now.minusDays(1)

    fun upsertTemporaryAbsenceApplicationRequest() = UpsertTemporaryAbsenceApplicationRequest(
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
      temporaryAbsenceType = "RR",
      temporaryAbsenceSubType = "RDR",
      toAddresses = listOf(),
    )

    fun upsertTemporaryAbsenceApplicationResponse() = UpsertTemporaryAbsenceApplicationResponse(12345, 56789)

    fun upsertScheduledTemporaryAbsenceRequest() = UpsertScheduledTemporaryAbsenceRequest(
      movementApplicationId = 56789,
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
      toAddress = UpsertTemporaryAbsenceAddress(id = 3456),
    )

    fun upsertScheduledTemporaryAbsenceResponse(eventId: Long = 131415, addressId: Long = 77, addressOwnerClass: String = "OFF") = UpsertScheduledTemporaryAbsenceResponse(12345, 56789, eventId, addressId, addressOwnerClass)

    fun createTemporaryAbsenceRequest(scheduledTemporaryAbsenceId: Long = 131415) = CreateTemporaryAbsenceRequest(
      movementDate = now.toLocalDate(),
      movementTime = now,
      movementReason = "C5",
      scheduledTemporaryAbsenceId = scheduledTemporaryAbsenceId,
      arrestAgency = "POL",
      escort = "U",
      escortText = "Temporary absence escort text",
      fromPrison = "LEI",
      toAgency = "HAZLWD",
      commentText = "Temporary absence comment",
      toCity = "765",
      toAddressId = 76543,
    )

    fun createTemporaryAbsenceResponse(bookingId: Long = 12345, movementSequence: Int = 2) = CreateTemporaryAbsenceResponse(bookingId, movementSequence)

    fun createTemporaryAbsenceReturnRequest() = CreateTemporaryAbsenceReturnRequest(
      movementDate = tomorrow.toLocalDate(),
      movementTime = tomorrow,
      movementReason = "C5",
      scheduledTemporaryAbsenceReturnId = 161718,
      arrestAgency = "POL",
      escort = "U",
      escortText = "Temporary absence return escort text",
      fromAgency = "HAZLWD",
      toPrison = "LEI",
      commentText = "Temporary absence return comment",
      fromAddressId = 76543,
    )

    fun createTemporaryAbsenceReturnResponse(bookingId: Long = 12345, movementSequence: Int = 3) = CreateTemporaryAbsenceReturnResponse(bookingId, movementSequence)

    fun createTemporaryAbsenceSummaryResponse() = OffenderTemporaryAbsenceSummaryResponse(
      applications = Applications(count = 1),
      scheduledOutMovements = ScheduledOut(count = 2),
      movements = Movements(
        count = 18,
        scheduled = MovementsByDirection(outCount = 3, inCount = 4),
        unscheduled = MovementsByDirection(outCount = 5, inCount = 6),
      ),
    )

    fun temporaryAbsenceSummaryIdsResponse() = OffenderTemporaryAbsenceIdsResponse(
      applicationIds = listOf(1111),
      scheduleIds = listOf(2222),
      scheduledMovementOutIds = listOf(OffenderTemporaryAbsenceId(12345, 3)),
      scheduledMovementInIds = listOf(OffenderTemporaryAbsenceId(12345, 4)),
      unscheduledMovementOutIds = listOf(OffenderTemporaryAbsenceId(12345, 5)),
      unscheduledMovementInIds = listOf(OffenderTemporaryAbsenceId(12345, 6)),
    )

    fun bookingTemporaryAbsences(
      movementPrison: String = "LEI",
      bookingId: Long = 12345L,
      activeBooking: Boolean = true,
      latestBooking: Boolean = true,
      absences: List<Absence> = listOf(absence(movementPrison = movementPrison)),
      applications: List<TemporaryAbsenceApplication> = listOf(application(absences = absences)),
      unscheduledTemporaryAbsences: List<TemporaryAbsence> = listOf(
        temporaryAbsence(seq = 1).copy(
          movementDate = yesterday.toLocalDate(),
          movementTime = yesterday,
        ),
      ),
      unscheduledTemporaryAbsenceReturns: List<TemporaryAbsenceReturn> = listOf(
        temporaryAbsenceReturn(seq = 2).copy(
          movementDate = yesterday.toLocalDate(),
          movementTime = yesterday,
        ),
      ),
    ): BookingTemporaryAbsences = BookingTemporaryAbsences(
      bookingId = bookingId,
      activeBooking = activeBooking,
      latestBooking = latestBooking,
      temporaryAbsenceApplications = applications,
      unscheduledTemporaryAbsences = unscheduledTemporaryAbsences,
      unscheduledTemporaryAbsenceReturns = unscheduledTemporaryAbsenceReturns,
    )

    fun application(
      id: Long = 1,
      fromDate: LocalDate = today,
      toDate: LocalDate = tomorrow.toLocalDate(),
      status: String = "APP-SCH",
      absences: List<Absence> = listOf(absence(movementPrison = "LEI")),
    ) = TemporaryAbsenceApplication(
      movementApplicationId = id,
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
      temporaryAbsenceType = "RR",
      temporaryAbsenceSubType = "SPL",
      absences = absences,
      audit = NomisAudit(
        createDatetime = now,
        createUsername = "USER",
      ),
    )

    fun absence(
      movementPrison: String = "LEI",
      scheduledAbsence: ScheduledTemporaryAbsence = scheduledAbsence(),
      scheduledAbsenceReturn: ScheduledTemporaryAbsenceReturn = scheduledAbsenceReturn(),
      temporaryAbsence: TemporaryAbsence = temporaryAbsence(seq = 3).copy(
        movementDate = yesterday.toLocalDate(),
        movementTime = yesterday,
        fromPrison = movementPrison,
      ),
      temporaryAbsenceReturn: TemporaryAbsenceReturn = temporaryAbsenceReturn(seq = 4).copy(
        movementDate = now.toLocalDate(),
        movementTime = now,
        toPrison = movementPrison,
      ),
    ) = Absence(scheduledAbsence, scheduledAbsenceReturn, temporaryAbsence, temporaryAbsenceReturn)

    fun temporaryAbsenceReturn(seq: Int = 2) = TemporaryAbsenceReturn(
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

    fun temporaryAbsence(seq: Int = 1) = TemporaryAbsence(
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

    fun scheduledAbsenceReturn(id: Long = 2) = ScheduledTemporaryAbsenceReturn(
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

    fun scheduledAbsence(id: Long = 1): ScheduledTemporaryAbsence = ScheduledTemporaryAbsence(
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
  }

  fun stubUpsertTemporaryAbsenceApplication(
    offenderNo: String = "A1234BC",
    response: UpsertTemporaryAbsenceApplicationResponse = upsertTemporaryAbsenceApplicationResponse(),
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/movements/$offenderNo/temporary-absences/application"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubUpsertTemporaryAbsenceApplication(
    offenderNo: String = "A1234BC",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status),
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/movements/$offenderNo/temporary-absences/application"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubUpsertScheduledTemporaryAbsence(
    offenderNo: String = "A1234BC",
    response: UpsertScheduledTemporaryAbsenceResponse = upsertScheduledTemporaryAbsenceResponse(),
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/movements/$offenderNo/temporary-absences/scheduled-temporary-absence"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubUpsertScheduledTemporaryAbsence(
    offenderNo: String = "A1234BC",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status),
  ) {
    nomisApi.stubFor(
      put(urlEqualTo("/movements/$offenderNo/temporary-absences/scheduled-temporary-absence"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubCreateTemporaryAbsence(
    offenderNo: String = "A1234BC",
    response: CreateTemporaryAbsenceResponse = createTemporaryAbsenceResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/movements/$offenderNo/temporary-absences/temporary-absence"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubCreateTemporaryAbsence(
    offenderNo: String = "A1234BC",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/movements/$offenderNo/temporary-absences/temporary-absence"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubCreateTemporaryAbsenceReturn(
    offenderNo: String = "A1234BC",
    response: CreateTemporaryAbsenceReturnResponse = createTemporaryAbsenceReturnResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/movements/$offenderNo/temporary-absences/temporary-absence-return"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubCreateTemporaryAbsenceReturn(
    offenderNo: String = "A1234BC",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/movements/$offenderNo/temporary-absences/temporary-absence-return"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubGetTemporaryAbsencePrisonerSummary(
    offenderNo: String = "A1234BC",
    response: OffenderTemporaryAbsenceSummaryResponse = createTemporaryAbsenceSummaryResponse(),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/movements/$offenderNo/temporary-absences/summary"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetTemporaryAbsencePrisonerSummary(
    offenderNo: String = "A1234BC",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/movements/$offenderNo/temporary-absences/summary"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubGetTemporaryAbsencePrisonerSummaryIds(
    offenderNo: String = "A1234BC",
    response: OffenderTemporaryAbsenceIdsResponse = temporaryAbsenceSummaryIdsResponse(),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/movements/$offenderNo/temporary-absences/ids"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetTemporaryAbsencePrisonerSummaryIds(
    offenderNo: String = "A1234BC",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/movements/$offenderNo/temporary-absences/ids"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubGetBookingTemporaryAbsences(
    bookingId: Long = 12345L,
    response: BookingTemporaryAbsences = bookingTemporaryAbsences(bookingId = bookingId),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/movements/booking/$bookingId/temporary-absences"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetBookingTemporaryAbsences(
    bookingId: Long = 12345L,
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/movements/booking/$bookingId/temporary-absences"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
