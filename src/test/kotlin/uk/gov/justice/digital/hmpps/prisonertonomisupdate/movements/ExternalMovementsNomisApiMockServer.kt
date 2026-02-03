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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.Applications
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceReturnRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceReturnResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.Movements
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.MovementsByDirection
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTemporaryAbsenceSummaryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.ScheduledOut
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertScheduledTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertScheduledTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTemporaryAbsenceAddress
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTemporaryAbsenceApplicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpsertTemporaryAbsenceApplicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDateTime

@Component
class ExternalMovementsNomisApiMockServer(private val jsonMapper: JsonMapper) {
  companion object {
    private val today = LocalDateTime.now()
    private val tomorrow = today.plusDays(1)

    fun upsertTemporaryAbsenceApplicationRequest() = UpsertTemporaryAbsenceApplicationRequest(
      eventSubType = "C5",
      applicationDate = today.toLocalDate(),
      fromDate = today.toLocalDate(),
      releaseTime = today,
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
      applicationDate = today,
      eventDate = today.toLocalDate(),
      startTime = today,
      comment = "Scheduled temporary absence comment",
      toAgency = "HAZLWD",
      transportType = "VAN",
      toAddress = UpsertTemporaryAbsenceAddress(id = 3456),
    )

    fun upsertScheduledTemporaryAbsenceResponse(eventId: Long = 131415, addressId: Long = 77, addressOwnerClass: String = "OFF") = UpsertScheduledTemporaryAbsenceResponse(12345, 56789, eventId, addressId, addressOwnerClass)

    fun createTemporaryAbsenceRequest(scheduledTemporaryAbsenceId: Long = 131415) = CreateTemporaryAbsenceRequest(
      movementDate = today.toLocalDate(),
      movementTime = today,
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

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
