package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ExternalMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceOutsideMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.util.*

@Component
class ExternalMovementsMappingApiMockServer(private val objectMapper: ObjectMapper) {

  fun stubCreateTemporaryAbsenceApplicationMapping() {
    mappingServer.stubFor(
      post("/mapping/temporary-absence/application")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),
        ),
    )
  }

  fun stubCreateTemporaryAbsenceMappingFailureFollowedBySuccess() {
    mappingServer.stubMappingCreateFailureFollowedBySuccess(url = "/mapping/temporary-absence/migrate", WireMock::put)
  }

  fun stubCreateTemporaryAbsenceApplicationMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/temporary-absence/application").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTemporaryAbsenceApplicationMappingConflict(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/temporary-absence/application").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTemporaryAbsenceApplicationMappingFailureFollowedBySuccess() = mappingServer.stubMappingCreateFailureFollowedBySuccess("/mapping/temporary-absence/application")

  fun stubGetTemporaryAbsenceApplicationMapping(prisonerNumber: String = "A1234BC", dpsId: UUID = UUID.randomUUID()) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/application/dps-id/$dpsId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200)
          .withBody(objectMapper.writeValueAsString(temporaryAbsenceApplicationMapping(prisonerNumber = prisonerNumber, dpsMovementApplicationId = dpsId))),
      ),
    )
  }

  fun stubGetTemporaryAbsenceApplicationMapping(dpsId: UUID = UUID.randomUUID(), status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      get(urlEqualTo("/mapping/temporary-absence/application/dps-id/$dpsId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateOutsideMovementMapping() {
    mappingServer.stubFor(
      post("/mapping/temporary-absence/outside-movement")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),
        ),
    )
  }

  fun stubCreateOutsideMovementMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/temporary-absence/outside-movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateOutsideMovementMappingConflict(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/temporary-absence/outside-movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateOutsideMovementMappingFailureFollowedBySuccess() = mappingServer.stubMappingCreateFailureFollowedBySuccess("/mapping/temporary-absence/outside-movement")

  fun stubGetTemporaryAbsenceOutsideMovementMapping(prisonerNumber: String = "A1234BC", dpsId: UUID = UUID.randomUUID()) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/outside-movement/dps-id/$dpsId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(objectMapper.writeValueAsString(temporaryAbsenceOutsideMovementMapping(prisonerNumber = prisonerNumber, dpsId = dpsId))),
      ),
    )
  }

  fun stubGetTemporaryAbsenceOutsideMovementMapping(dpsId: UUID = UUID.randomUUID(), status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/outside-movement/dps-id/$dpsId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateScheduledMovementMapping() {
    mappingServer.stubFor(
      post("/mapping/temporary-absence/scheduled-movement")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),
        ),
    )
  }

  fun stubCreateScheduledMovementMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/temporary-absence/scheduled-movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateScheduledMovementMappingConflict(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/temporary-absence/scheduled-movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateScheduledMovementMappingFailureFollowedBySuccess() = mappingServer.stubMappingCreateFailureFollowedBySuccess("/mapping/temporary-absence/scheduled-movement")

  fun stubGetTemporaryAbsenceScheduledMovementMapping(prisonerNumber: String = "A1234BC", dpsId: UUID = UUID.randomUUID()) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/scheduled-movement/dps-id/$dpsId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(objectMapper.writeValueAsString(temporaryAbsenceScheduledMovementMapping(prisonerNumber = prisonerNumber, dpsId = dpsId))),
      ),
    )
  }

  fun stubGetTemporaryAbsenceScheduledMovementMapping(dpsId: UUID = UUID.randomUUID(), status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/scheduled-movement/dps-id/$dpsId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateExternalMovementMapping() {
    mappingServer.stubFor(
      post("/mapping/temporary-absence/external-movement")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),
        ),
    )
  }

  fun stubCreateExternalMovementMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/temporary-absence/external-movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateExternalMovementMappingConflict(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/temporary-absence/external-movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateExternalMovementMappingFailureFollowedBySuccess() = mappingServer.stubMappingCreateFailureFollowedBySuccess("/mapping/temporary-absence/external-movement")

  fun stubGetTemporaryAbsenceExternalMovementMapping(prisonerNumber: String = "A1234BC", dpsId: UUID = UUID.randomUUID()) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/external-movement/dps-id/$dpsId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(objectMapper.writeValueAsString(temporaryAbsenceExternalMovementMapping(prisonerNumber = prisonerNumber, dpsId = dpsId))),
      ),
    )
  }

  fun stubGetTemporaryAbsenceExternalMovementMapping(dpsId: UUID = UUID.randomUUID(), status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/external-movement/dps-id/$dpsId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingServer.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
}

fun temporaryAbsenceApplicationMapping(
  nomisMovementApplicationId: Long = 1L,
  dpsMovementApplicationId: UUID = UUID.randomUUID(),
  prisonerNumber: String = "A1234BC",
) = TemporaryAbsenceApplicationSyncMappingDto(
  prisonerNumber = prisonerNumber,
  bookingId = 12345,
  nomisMovementApplicationId = nomisMovementApplicationId,
  dpsMovementApplicationId = dpsMovementApplicationId,
  mappingType = TemporaryAbsenceApplicationSyncMappingDto.MappingType.MIGRATED,
)

fun temporaryAbsenceOutsideMovementMapping(nomisMovementApplicationMultiId: Long = 1L, prisonerNumber: String = "A1234BC", dpsId: UUID = UUID.randomUUID()) = TemporaryAbsenceOutsideMovementSyncMappingDto(
  prisonerNumber = prisonerNumber,
  bookingId = 12345,
  nomisMovementApplicationMultiId = nomisMovementApplicationMultiId,
  dpsOutsideMovementId = dpsId,
  mappingType = TemporaryAbsenceOutsideMovementSyncMappingDto.MappingType.MIGRATED,
)

fun temporaryAbsenceScheduledMovementMapping(nomisEventId: Long = 1L, prisonerNumber: String = "A1234BC", dpsId: UUID = UUID.randomUUID()) = ScheduledMovementSyncMappingDto(
  prisonerNumber = prisonerNumber,
  bookingId = 12345,
  nomisEventId = nomisEventId,
  dpsScheduledMovementId = dpsId,
  mappingType = ScheduledMovementSyncMappingDto.MappingType.MIGRATED,
)

fun temporaryAbsenceExternalMovementMapping(bookingId: Long = 12345L, movementSeq: Int = 1, prisonerNumber: String = "A1234BC", dpsId: UUID = UUID.randomUUID()) = ExternalMovementSyncMappingDto(
  prisonerNumber = prisonerNumber,
  bookingId = bookingId,
  nomisMovementSeq = movementSeq,
  dpsExternalMovementId = dpsId,
  mappingType = ExternalMovementSyncMappingDto.MappingType.MIGRATED,
)
