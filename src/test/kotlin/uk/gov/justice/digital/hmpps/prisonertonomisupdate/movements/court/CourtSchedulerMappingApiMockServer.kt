package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.court

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtMovementMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtScheduleMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtScheduleMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.CourtSchedulerPrisonerMappingIdsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.util.*

@Component
class CourtSchedulerMappingApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubGetCourtSchedulerPrisonerMappingIds(
    prisonerNumber: String = "A1234BC",
    bookingId: Long = 12345,
    nomisEventId: Long = 1,
    dpsCourtAppearanceId: UUID = UUID.randomUUID(),
    nomisMovementOutSeq: Int = 3,
    dpsMovementOutId: UUID = UUID.randomUUID(),
    nomisMovementInSeq: Int = 4,
    dpsMovementInId: UUID = UUID.randomUUID(),
    nomisUnscheduledMovementOutSeq: Int = 1,
    dpsUnscheduledMovementOutId: UUID = UUID.randomUUID(),
    nomisUnscheduledMovementInSeq: Int = 2,
    dpsUnscheduledMovementInId: UUID = UUID.randomUUID(),
    idMappings: CourtSchedulerPrisonerMappingIdsDto = courtSchedulerPrisonerIdMappings(bookingId, nomisEventId, dpsCourtAppearanceId, nomisMovementOutSeq, dpsMovementOutId, nomisMovementInSeq, dpsMovementInId, nomisUnscheduledMovementOutSeq, dpsUnscheduledMovementOutId, nomisUnscheduledMovementInSeq, dpsUnscheduledMovementInId),
  ) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/court-scheduler/$prisonerNumber/ids")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(idMappings)),
      ),
    )
  }

  fun stubGetCourtSchedulerPrisonerMappingIds(prisonerNumber: String = "A1234BC", status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/court-scheduler/$prisonerNumber/ids")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetCourtScheduleMapping(
    prisonerNumber: String = "A1234BC",
    dpsId: UUID = UUID.randomUUID(),
    nomisEventId: Long = 123,
    mapping: CourtScheduleMappingDto = courtScheduleMapping(prisonerNumber, nomisEventId, dpsId),
  ) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/court-scheduler/schedule/dps-id/$dpsId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(jsonMapper.writeValueAsString(mapping)),
      ),
    )
  }

  fun stubGetCourtScheduleMapping(
    status: HttpStatus,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/court-scheduler/schedule/dps-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateCourtScheduleMapping() {
    mappingServer.stubFor(
      post("/mapping/court-scheduler/schedule")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),
        ),
    )
  }

  fun stubCreateCourtScheduleMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/court-scheduler/schedule").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateCourtScheduleMappingConflict(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/court-scheduler/schedule").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateCourtScheduleMappingFailureFollowedBySuccess() = mappingServer.stubMappingCreateFailureFollowedBySuccess("/mapping/court-scheduler/schedule")

  fun verify(pattern: RequestPatternBuilder) = mappingServer.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
}

fun courtSchedulerPrisonerIdMappings(
  bookingId: Long = 12345,
  nomisEventId: Long = 1,
  dpsCourtAppearanceId: UUID = UUID.randomUUID(),
  nomisMovementOutSeq: Int = 3,
  dpsMovementOutId: UUID = UUID.randomUUID(),
  nomisMovementInSeq: Int = 4,
  dpsMovementInId: UUID = UUID.randomUUID(),
  nomisUnscheduledMovementOutSeq: Int = 1,
  dpsUnscheduledMovementOutId: UUID = UUID.randomUUID(),
  nomisUnscheduledMovementInSeq: Int = 2,
  dpsUnscheduledMovementInId: UUID = UUID.randomUUID(),
) = CourtSchedulerPrisonerMappingIdsDto(
  prisonerNumber = "A1234BC",
  schedules = listOf(CourtScheduleMappingIdsDto(nomisEventId, dpsCourtAppearanceId)),
  movements = listOf(
    CourtMovementMappingIdsDto(bookingId, nomisMovementOutSeq, dpsMovementOutId),
    CourtMovementMappingIdsDto(bookingId, nomisMovementInSeq, dpsMovementInId),
    CourtMovementMappingIdsDto(bookingId, nomisUnscheduledMovementOutSeq, dpsUnscheduledMovementOutId),
    CourtMovementMappingIdsDto(bookingId, nomisUnscheduledMovementInSeq, dpsUnscheduledMovementInId),
  ),
)

fun courtScheduleMapping(
  prisonerNumber: String = "A1234BC",
  nomisEventId: Long = 123,
  dpsId: UUID = UUID.randomUUID(),
) = CourtScheduleMappingDto(
  prisonerNumber,
  12435L,
  nomisEventId,
  dpsId,
  CourtScheduleMappingDto.MappingType.MIGRATED,
)
