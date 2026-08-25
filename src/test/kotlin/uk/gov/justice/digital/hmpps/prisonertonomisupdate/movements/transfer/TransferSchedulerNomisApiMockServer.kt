package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.transfer

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatus.OK
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingTransferMovements
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BookingTransferSchedule
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTransferMovementsResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.TransferMovementOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.TransferScheduleOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.TransferScheduleWaitlist
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDateTime

@Component
class TransferSchedulerNomisApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubGetOffenderTransferMovements(
    offenderNo: String = "A1234BC",
    response: OffenderTransferMovementsResponse = offenderTransferMovementsResponse(offenderNo = offenderNo),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/movements/$offenderNo/transfer")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetOffenderTransferMovements(
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlPathMatching("/movements/.*/transfer")).willReturn(
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

    fun transferScheduleOutResponse(
      eventId: Long = 1,
      eventStatus: String = "SCH",
      startTime: LocalDateTime = now,
      createDateTime: LocalDateTime = now,
      createUsername: String = "PRISONER_MANAGER_API",
      modifyDateTime: LocalDateTime? = null,
      modifyUserId: String? = null,
      waitlist: TransferScheduleWaitlist? = null,
    ) = TransferScheduleOut(
      bookingId = 12345,
      eventId = eventId,
      startTime = startTime,
      eventSubType = "TRN",
      eventStatus = eventStatus,
      comment = "transfer schedule comment",
      hiddenComment = "hidden transfer schedule comment",
      fromPrison = "BXI",
      toPrison = "LEI",
      cancellationReasonCode = "ADMI",
      escortCode = "U",
      userActiveCaseloadId = "MDI",
      waitlist = waitlist,
      audit = NomisAudit(
        createDatetime = createDateTime,
        createUsername = createUsername,
        auditModuleName = "OCCCDCASE",
        modifyDatetime = modifyDateTime,
        modifyUserId = modifyUserId,
      ),
    )

    fun transferScheduleWaitlistResponse(
      status: String = "PEND",
      priority: String = "3",
      approved: Boolean = true,
      createDateTime: LocalDateTime = yesterday,
      createUsername: String = "PRISONER_MANAGER_API",
      modifyDateTime: LocalDateTime? = null,
      modifyUserId: String? = null,
    ) = TransferScheduleWaitlist(
      requestDate = yesterday.toLocalDate(),
      status = status,
      statusDate = yesterday.toLocalDate(),
      priority = priority,
      approved = approved,
      approvedUserName = "A_USER",
      cancellationReasonCode = "TRANS",
      comment = "some waitlist comment",
      audit = NomisAudit(
        createDatetime = createDateTime,
        createUsername = createUsername,
        modifyDatetime = modifyDateTime,
        modifyUserId = modifyUserId,
      ),
    )

    fun transferMovementOutResponse() = TransferMovementOut(
      eventId = 123L,
      bookingId = 12345L,
      sequence = 3,
      movementTime = LocalDateTime.now(),
      movementReason = "28",
      fromPrison = "BXI",
      toPrison = "LEI",
      active = true,
      audit = NomisAudit(
        createDatetime = yesterday,
        createUsername = "PRISONER_MANAGER_API",
      ),
      transferScheduleOutId = 123L,
      escort = "PECS",
      commentText = "some transfer movement comment",
      userActiveCaseloadId = "MDI",
    )

    fun offenderTransferMovementsResponse(
      offenderNo: String = "A1234BC",
      bookingId: Long = 12345L,
      activeBooking: Boolean = true,
      latestBooking: Boolean = true,
      schedules: List<BookingTransferSchedule> = listOf(
        BookingTransferSchedule(
          schedule = transferScheduleOutResponse(eventId = 1L, waitlist = transferScheduleWaitlistResponse(status = "APPROVED")),
          movement = transferMovementOutResponse().copy(eventId = 1L, sequence = 3),
        ),
      ),
      unscheduledMovements: List<TransferMovementOut> = listOf(transferMovementOutResponse().copy(transferScheduleOutId = null, sequence = 4)),
    ): OffenderTransferMovementsResponse = OffenderTransferMovementsResponse(
      offenderNo = offenderNo,
      rootOffenderId = 777L,
      bookings = listOf(
        BookingTransferMovements(
          bookingId = bookingId,
          activeBooking = activeBooking,
          latestBooking = latestBooking,
          transferSchedules = schedules,
          unscheduledTransferMovements = unscheduledMovements,
        ),
      ),
    )
  }
}
