package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ExternalMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ScheduledMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceApplicationSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDateTime
import java.util.*

@Component
class ExternalMovementsMappingApiMockServer(private val jsonMapper: JsonMapper) {

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
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTemporaryAbsenceApplicationMappingConflict(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/temporary-absence/application").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTemporaryAbsenceApplicationMappingFailureFollowedBySuccess() = mappingServer.stubMappingCreateFailureFollowedBySuccess("/mapping/temporary-absence/application")

  fun stubGetTemporaryAbsenceApplicationMapping(prisonerNumber: String = "A1234BC", dpsId: UUID = UUID.randomUUID(), nomisMovementApplicationId: Long = 1L) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/application/dps-id/$dpsId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200)
          .withBody(jsonMapper.writeValueAsString(temporaryAbsenceApplicationMapping(nomisMovementApplicationId, dpsId, prisonerNumber))),
      ),
    )
  }

  fun stubGetTemporaryAbsenceApplicationMapping(dpsId: UUID = UUID.randomUUID(), status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      get(urlEqualTo("/mapping/temporary-absence/application/dps-id/$dpsId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
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
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateScheduledMovementMappingConflict(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/temporary-absence/scheduled-movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateScheduledMovementMappingFailureFollowedBySuccess() = mappingServer.stubMappingCreateFailureFollowedBySuccess("/mapping/temporary-absence/scheduled-movement")

  fun stubUpdateScheduledMovementMapping() {
    mappingServer.stubFor(
      put("/mapping/temporary-absence/scheduled-movement")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200),
        ),
    )
  }

  fun stubUpdateScheduledMovementMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      put("/mapping/temporary-absence/scheduled-movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubUpdateScheduledMovementMappingFailureFollowedBySuccess() = mappingServer.stubMappingCreateFailureFollowedBySuccess("/mapping/temporary-absence/scheduled-movement", ::put)

  fun stubGetTemporaryAbsenceScheduledMovementMapping(prisonerNumber: String = "A1234BC", dpsId: UUID = UUID.randomUUID(), nomisEventId: Long = 1, addressId: Long = 54321) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/scheduled-movement/dps-id/$dpsId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(temporaryAbsenceScheduledMovementMapping(prisonerNumber = prisonerNumber, dpsId = dpsId, nomisEventId = nomisEventId, addressId = addressId))),
      ),
    )
  }

  fun stubGetTemporaryAbsenceScheduledMovementMapping(dpsId: UUID = UUID.randomUUID(), status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/scheduled-movement/dps-id/$dpsId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
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
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateExternalMovementMappingConflict(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/temporary-absence/external-movement").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateExternalMovementMappingFailureFollowedBySuccess() = mappingServer.stubMappingCreateFailureFollowedBySuccess("/mapping/temporary-absence/external-movement")

  fun stubGetTemporaryAbsenceExternalMovementMapping(prisonerNumber: String = "A1234BC", dpsId: UUID = UUID.randomUUID()) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/external-movement/dps-id/$dpsId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(temporaryAbsenceExternalMovementMapping(prisonerNumber = prisonerNumber, dpsId = dpsId))),
      ),
    )
  }

  fun stubGetTemporaryAbsenceExternalMovementMapping(dpsId: UUID = UUID.randomUUID(), status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/external-movement/dps-id/$dpsId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
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

fun temporaryAbsenceScheduledMovementMapping(nomisEventId: Long = 1L, prisonerNumber: String = "A1234BC", dpsId: UUID = UUID.randomUUID(), addressId: Long = 54321) = ScheduledMovementSyncMappingDto(
  prisonerNumber = prisonerNumber,
  bookingId = 12345,
  nomisEventId = nomisEventId,
  dpsOccurrenceId = dpsId,
  mappingType = ScheduledMovementSyncMappingDto.MappingType.MIGRATED,
  dpsAddressText = "some address",
  eventTime = "${LocalDateTime.now()}",
  nomisAddressId = addressId,
  nomisAddressOwnerClass = "OFF",
  dpsUprn = 654,
  dpsDescription = null,
  dpsPostcode = "SW1A 1AA",
)

fun temporaryAbsenceExternalMovementMapping(bookingId: Long = 12345L, movementSeq: Int = 1, prisonerNumber: String = "A1234BC", dpsId: UUID = UUID.randomUUID()) = ExternalMovementSyncMappingDto(
  prisonerNumber = prisonerNumber,
  bookingId = bookingId,
  nomisMovementSeq = movementSeq,
  dpsMovementId = dpsId,
  mappingType = ExternalMovementSyncMappingDto.MappingType.MIGRATED,
  // TODO add address mapping details
  nomisAddressId = 0,
  nomisAddressOwnerClass = "",
  dpsAddressText = "",
)
