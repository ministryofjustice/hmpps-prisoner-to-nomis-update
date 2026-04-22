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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ExternalMovementMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ExternalMovementSyncMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ScheduledMovementMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TapApplicationMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TapScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsenceApplicationMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TemporaryAbsencesPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDateTime
import java.util.*

@Component
class ExternalMovementsMappingApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubCreateTapApplicationMapping() {
    mappingServer.stubFor(
      post("/mapping/taps/application")
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

  fun stubCreateTapApplicationMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/taps/application").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTapApplicationMappingConflict(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/taps/application").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTapApplicationMappingFailureFollowedBySuccess() = mappingServer.stubMappingCreateFailureFollowedBySuccess("/mapping/taps/application")

  fun stubGetTapApplicationMapping(prisonerNumber: String = "A1234BC", dpsId: UUID = UUID.randomUUID(), nomisApplicationId: Long = 1L) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/taps/application/dps-id/$dpsId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(200)
          .withBody(jsonMapper.writeValueAsString(tapApplicationMapping(nomisApplicationId, dpsId, prisonerNumber))),
      ),
    )
  }

  fun stubGetTapApplicationMapping(dpsId: UUID = UUID.randomUUID(), status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      get(urlEqualTo("/mapping/taps/application/dps-id/$dpsId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTapScheduleMapping() {
    mappingServer.stubFor(
      post("/mapping/taps/schedule")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),
        ),
    )
  }

  fun stubCreateTapScheduleMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/taps/schedule").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTapScheduleMappingConflict(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/taps/schedule").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTapScheduleMappingFailureFollowedBySuccess() = mappingServer.stubMappingCreateFailureFollowedBySuccess("/mapping/taps/schedule")

  fun stubUpdateTapScheduleMapping() {
    mappingServer.stubFor(
      put("/mapping/taps/schedule")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200),
        ),
    )
  }

  fun stubUpdateTapScheduleMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      put("/mapping/taps/schedule").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubUpdateTapScheduleMappingFailureFollowedBySuccess() = mappingServer.stubMappingCreateFailureFollowedBySuccess("/mapping/taps/schedule", ::put)

  fun stubGetTapScheduleMapping(
    prisonerNumber: String = "A1234BC",
    dpsId: UUID = UUID.randomUUID(),
    nomisEventId: Long = 1,
    addressId: Long = 54321,
    mapping: TapScheduleMappingDto = tapScheduleMapping(prisonerNumber = prisonerNumber, dpsId = dpsId, nomisEventId = nomisEventId, addressId = addressId),
  ) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/taps/schedule/dps-id/$dpsId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(mapping)),
      ),
    )
  }

  fun stubGetTapScheduleMapping(dpsId: UUID = UUID.randomUUID(), status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/taps/schedule/dps-id/$dpsId")).willReturn(
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

  fun stubGetTemporaryAbsenceMappingIds(
    prisonerNumber: String = "A1234BC",
    response: TemporaryAbsencesPrisonerMappingIdsDto = prisonerMappingIdsDto(),
  ) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/$prisonerNumber/ids")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }

  fun stubGetTemporaryAbsenceMappingIds(
    prisonerNumber: String = "A1234BC",
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/temporary-absence/$prisonerNumber/ids")).willReturn(
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

fun tapApplicationMapping(
  nomisApplicationId: Long = 1L,
  dpsAuthorisationId: UUID = UUID.randomUUID(),
  prisonerNumber: String = "A1234BC",
) = TapApplicationMappingDto(
  prisonerNumber = prisonerNumber,
  bookingId = 12345,
  nomisApplicationId = nomisApplicationId,
  dpsAuthorisationId = dpsAuthorisationId,
  mappingType = TapApplicationMappingDto.MappingType.MIGRATED,
)

fun tapScheduleMapping(nomisEventId: Long = 1L, prisonerNumber: String = "A1234BC", dpsId: UUID = UUID.randomUUID(), addressId: Long = 54321) = TapScheduleMappingDto(
  prisonerNumber = prisonerNumber,
  bookingId = 12345,
  nomisEventId = nomisEventId,
  dpsOccurrenceId = dpsId,
  mappingType = TapScheduleMappingDto.MappingType.MIGRATED,
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

fun prisonerMappingIdsDto(offenderNo: String = "A1234BC") = TemporaryAbsencesPrisonerMappingIdsDto(
  prisonerNumber = offenderNo,
  applications = listOf(TemporaryAbsenceApplicationMappingIdsDto(1111, UUID.randomUUID())),
  schedules = listOf(ScheduledMovementMappingIdsDto(2222, UUID.randomUUID())),
  movements = listOf(
    ExternalMovementMappingIdsDto(12345, 3, UUID.randomUUID()),
    ExternalMovementMappingIdsDto(12345, 4, UUID.randomUUID()),
    ExternalMovementMappingIdsDto(12345, 5, UUID.randomUUID()),
    ExternalMovementMappingIdsDto(12345, 6, UUID.randomUUID()),
  ),
)
