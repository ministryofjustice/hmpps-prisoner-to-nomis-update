package uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.delete
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.BedAssignmentMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer

@Component
class BedAssignmentMappingApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetByDpsId(
    dpsBedAssignmentId: String,
    mapping: BedAssignmentMappingDto = bedAssignmentMapping(dpsBedAssignmentId = dpsBedAssignmentId),
  ) {
    mappingServer.stubFor(
      get(urlEqualTo("/mapping/bed-assignments/dps-bed-assignment-id/$dpsBedAssignmentId")).willReturn(
        okJson(jsonMapper.writeValueAsString(mapping)),
      ),
    )
  }

  fun stubGetByDpsId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/bed-assignments/dps-bed-assignment-id/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubGetByNomisId(
    bookingId: Long,
    sequence: Int,
    mapping: BedAssignmentMappingDto = bedAssignmentMapping(bookingId = bookingId, sequence = sequence),
  ) {
    mappingServer.stubFor(
      get(urlEqualTo("/mapping/bed-assignments/booking-id/$bookingId/sequence/$sequence")).willReturn(
        okJson(jsonMapper.writeValueAsString(mapping)),
      ),
    )
  }

  fun stubGetByNomisId(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      get(urlPathMatching("/mapping/bed-assignments/booking-id/.*/sequence/.*")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostMapping() {
    mappingServer.stubFor(
      post("/mapping/bed-assignments").willReturn(status(201)),
    )
  }

  fun stubPostMapping(status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    mappingServer.stubFor(
      post("/mapping/bed-assignments").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
      ),
    )
  }

  fun stubPostMappingsForPrisoner(offenderNo: String) {
    mappingServer.stubFor(
      post("/mapping/bed-assignments/$offenderNo/all").willReturn(status(201)),
    )
  }

  fun stubDeleteByDpsId(dpsBedAssignmentId: String) {
    mappingServer.stubFor(
      delete("/mapping/bed-assignments/dps-bed-assignment-id/$dpsBedAssignmentId").willReturn(status(204)),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = mappingServer.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
}

fun bedAssignmentMapping(
  dpsBedAssignmentId: String = "de91dfa7-821f-4552-a427-bf2f32eafeb0",
  bookingId: Long = 12345,
  sequence: Int = 1,
) = BedAssignmentMappingDto(
  dpsBedAssignmentId = dpsBedAssignmentId,
  nomisBookingId = bookingId,
  nomisSequence = sequence,
  mappingType = BedAssignmentMappingDto.MappingType.MIGRATED,
)
