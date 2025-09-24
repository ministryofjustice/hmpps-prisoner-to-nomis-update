package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateScheduledTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateScheduledTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateScheduledTemporaryAbsenceReturnRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateScheduledTemporaryAbsenceReturnResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceApplicationRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceApplicationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceOutsideMovementRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceOutsideMovementResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceReturnRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateTemporaryAbsenceReturnResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDateTime

@Component
class ExternalMovementsNomisApiMockServer(private val objectMapper: ObjectMapper) {
  companion object {
    private val today = LocalDateTime.now()
    private val tomorrow = today.plusDays(1)

    fun createTemporaryAbsenceApplicationRequest() = CreateTemporaryAbsenceApplicationRequest(
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
      toAgencyId = "HAZLWD",
      toAddressId = 3456,
      contactPersonName = "Deek Sanderson",
      temporaryAbsenceType = "RR",
      temporaryAbsenceSubType = "RDR",
    )

    fun createTemporaryAbsenceApplicationResponse() = CreateTemporaryAbsenceApplicationResponse(12345, 56789)

    fun createTemporaryAbsenceOutsideMovementRequest() = CreateTemporaryAbsenceOutsideMovementRequest(
      movementApplicationId = 56789,
      eventSubType = "C5",
      fromDate = today.toLocalDate(),
      releaseTime = today,
      toDate = tomorrow.toLocalDate(),
      returnTime = tomorrow,
      comment = "Temporary Absence Outside Movement comment",
      toAgencyId = "HAZLWD",
      toAddressId = 3456,
      contactPersonName = "Deek Sanderson",
      temporaryAbsenceType = "RR",
      temporaryAbsenceSubType = "RDR",
    )

    fun createTemporaryAbsenceOutsideMovementResponse(applicationMultiId: Long = 101112) = CreateTemporaryAbsenceOutsideMovementResponse(12345, 56789, applicationMultiId)

    fun createScheduledTemporaryAbsenceRequest() = CreateScheduledTemporaryAbsenceRequest(
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
      toAddressId = 3456,
    )

    fun createScheduledTemporaryAbsenceResponse(eventId: Long = 131415) = CreateScheduledTemporaryAbsenceResponse(12345, 56789, eventId)

    fun createScheduledTemporaryAbsenceReturnRequest() = CreateScheduledTemporaryAbsenceReturnRequest(
      movementApplicationId = 56789,
      scheduledTemporaryAbsenceEventId = 131415,
      eventSubType = "C5",
      eventStatus = "SCH",
      eventDate = today.toLocalDate(),
      escort = "U",
      startTime = today,
      comment = "Scheduled temporary absence return comment",
      fromAgency = "HAZLWD",
      toPrison = "LEI",
    )

    fun createScheduledTemporaryAbsenceReturnResponse() = CreateScheduledTemporaryAbsenceReturnResponse(12345, 56789, 161718)

    fun createTemporaryAbsenceRequest() = CreateTemporaryAbsenceRequest(
      movementDate = today.toLocalDate(),
      movementTime = today,
      movementReason = "C5",
      scheduledTemporaryAbsenceId = 131415,
      arrestAgency = "POL",
      escort = "U",
      escortText = "Temporary absence escort text",
      fromPrison = "LEI",
      toAgency = "HAZLWD",
      commentText = "Temporary absence comment",
      toCity = "765",
      toAddressId = 76543,
    )

    fun createTemporaryAbsenceResponse() = CreateTemporaryAbsenceResponse(12345, 2)

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

    fun createTemporaryAbsenceReturnResponse() = CreateTemporaryAbsenceReturnResponse(12345, 3)
  }

  fun stubCreateTemporaryAbsenceApplication(
    offenderNo: String = "A1234BC",
    response: CreateTemporaryAbsenceApplicationResponse = createTemporaryAbsenceApplicationResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/movements/$offenderNo/temporary-absences/application"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubCreateTemporaryAbsenceApplication(
    offenderNo: String = "A1234BC",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/movements/$offenderNo/temporary-absences/application"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubCreateTemporaryAbsenceOutsideMovement(
    offenderNo: String = "A1234BC",
    response: CreateTemporaryAbsenceOutsideMovementResponse = createTemporaryAbsenceOutsideMovementResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/movements/$offenderNo/temporary-absences/outside-movement"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubCreateTemporaryAbsenceOutsideMovement(
    offenderNo: String = "A1234BC",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/movements/$offenderNo/temporary-absences/outside-movement"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubCreateScheduledTemporaryAbsence(
    offenderNo: String = "A1234BC",
    response: CreateScheduledTemporaryAbsenceResponse = createScheduledTemporaryAbsenceResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/movements/$offenderNo/temporary-absences/scheduled-temporary-absence"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubCreateScheduledTemporaryAbsence(
    offenderNo: String = "A1234BC",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/movements/$offenderNo/temporary-absences/scheduled-temporary-absence"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun stubCreateScheduledTemporaryAbsenceReturn(
    offenderNo: String = "A1234BC",
    response: CreateScheduledTemporaryAbsenceReturnResponse = createScheduledTemporaryAbsenceReturnResponse(),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/movements/$offenderNo/temporary-absences/scheduled-temporary-absence-return"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.CREATED.value())
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubCreateScheduledTemporaryAbsenceReturn(
    offenderNo: String = "A1234BC",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status),
  ) {
    nomisApi.stubFor(
      post(urlEqualTo("/movements/$offenderNo/temporary-absences/scheduled-temporary-absence-return"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(objectMapper.writeValueAsString(error)),
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
            .withBody(objectMapper.writeValueAsString(response)),
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
            .withBody(objectMapper.writeValueAsString(error)),
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
            .withBody(objectMapper.writeValueAsString(response)),
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
            .withBody(objectMapper.writeValueAsString(error)),
        ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
