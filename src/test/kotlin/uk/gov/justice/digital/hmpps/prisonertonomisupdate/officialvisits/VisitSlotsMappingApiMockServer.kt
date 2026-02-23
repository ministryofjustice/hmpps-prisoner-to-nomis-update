package uk.gov.justice.digital.hmpps.prisonertonomisupdate.officialvisits

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import com.github.tomakehurst.wiremock.stubbing.Scenario
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.DuplicateMappingErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.LocationMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.VisitSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.VisitTimeSlotMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer

@Component
class VisitSlotsMappingApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubGetTimeSlotByDpsIdOrNull(
    dpsId: String = "123456",
    mapping: VisitTimeSlotMappingDto? = VisitTimeSlotMappingDto(
      dpsId = "123456",
      nomisPrisonId = "MDI",
      nomisDayOfWeek = "MON",
      nomisSlotSequence = 1,
      mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/visit-slots/time-slots/dps-id/$dpsId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/visit-slots/time-slots/dps-id/$dpsId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubGetTimeSlotByDpsId(
    dpsId: String = "123456",
    mapping: VisitTimeSlotMappingDto = VisitTimeSlotMappingDto(
      dpsId = "123456",
      nomisPrisonId = "MDI",
      nomisDayOfWeek = "MON",
      nomisSlotSequence = 1,
      mappingType = VisitTimeSlotMappingDto.MappingType.MIGRATED,
    ),
  ) = stubGetTimeSlotByDpsIdOrNull(dpsId, mapping)

  fun stubCreateTimeSlotMapping() {
    mappingServer.stubFor(
      post("/mapping/visit-slots/time-slots").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateTimeSlotMapping(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/visit-slots/time-slots").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubCreateTimeSlotMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/visit-slots/time-slots")
        .inScenario("Retry CreateTimeSlot Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause CreateTimeSlot Success"),
    )

    mappingServer.stubFor(
      post("/mapping/visit-slots/time-slots")
        .inScenario("Retry CreateTimeSlot Scenario")
        .whenScenarioStateIs("Cause CreateTimeSlot Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubDeleteTimeSlotByNomisIds(
    nomisPrisonId: String = "WWI",
    nomisDayOfWeek: String = "MON",
    nomisSlotSequence: Int = 2,
  ) {
    mappingServer.stubFor(
      delete(urlEqualTo("/mapping/visit-slots/time-slots/nomis-prison-id/$nomisPrisonId/nomis-day-of-week/$nomisDayOfWeek/nomis-slot-sequence/$nomisSlotSequence")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value())
          .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
      ),
    )
  }

  fun stubGetVisitSlotByDpsIdOrNull(
    dpsId: String = "123456",
    mapping: VisitSlotMappingDto? = VisitSlotMappingDto(
      dpsId = "123456",
      nomisId = 6543231,
      mappingType = VisitSlotMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/visit-slots/visit-slot/dps-id/$dpsId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/visit-slots/visit-slot/dps-id/$dpsId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun stubCreateVisitSlotMapping() {
    mappingServer.stubFor(
      post("/mapping/visit-slots/visit-slot").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(201),
      ),
    )
  }

  fun stubCreateVisitSlotMapping(error: DuplicateMappingErrorResponse) {
    mappingServer.stubFor(
      post("/mapping/visit-slots/visit-slot").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(409)
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  @Suppress("unused")
  fun stubCreateVisitSlotMappingFollowedBySuccess(status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/visit-slots/visit-slot")
        .inScenario("Retry CreateVisitSlot Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause CreateVisitSlot Success"),
    )

    mappingServer.stubFor(
      post("/mapping/visit-slots/visit-slot")
        .inScenario("Retry CreateVisitSlot Scenario")
        .whenScenarioStateIs("Cause CreateVisitSlot Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubDeleteVisitSlotByNomisId(
    nomisId: Long = 123456,
  ) {
    mappingServer.stubFor(
      delete(urlEqualTo("/mapping/visit-slots/visit-slot/nomis-id/$nomisId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.NO_CONTENT.value()),
      ),
    )
  }
  fun stubDeleteVisitSlotByNomisIdFailureFollowedBySuccess(
    nomisId: Long = 123456,
    status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    mappingServer.stubFor(
      delete(urlEqualTo("/mapping/visit-slots/visit-slot/nomis-id/$nomisId"))
        .inScenario("Retry stubDeleteVisitSlotByNomisId Scenario")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(error)),
        ).willSetStateTo("Cause stubDeleteVisitSlotByNomisId Success"),
    )

    mappingServer.stubFor(
      delete(urlEqualTo("/mapping/visit-slots/visit-slot/nomis-id/$nomisId"))
        .inScenario("Retry stubDeleteVisitSlotByNomisId Scenario")
        .whenScenarioStateIs("Cause stubDeleteVisitSlotByNomisId Success")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(201),

        ).willSetStateTo(Scenario.STARTED),
    )
  }

  fun stubGetInternalLocationByDpsId(
    dpsLocationId: String,
    mapping: LocationMappingDto = LocationMappingDto(
      dpsLocationId = dpsLocationId,
      nomisLocationId = 82828,
      mappingType = LocationMappingDto.MappingType.LOCATION_CREATED,
    ),
  ) {
    mappingServer.stubFor(
      get(urlEqualTo("/mapping/locations/dps/$dpsLocationId")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(mapping)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingServer.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
}
