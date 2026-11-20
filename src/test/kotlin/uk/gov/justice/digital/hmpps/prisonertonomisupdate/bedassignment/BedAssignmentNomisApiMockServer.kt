package uk.gov.justice.digital.hmpps.prisonertonomisupdate.bedassignment

import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.BedAssignmentHistoryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.NomisAudit
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerBedAssignmentHistoryResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDateTime

@Component
class BedAssignmentNomisApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubGetBedAssignmentsForPrisoner(
    offenderNo: String,
    response: PrisonerBedAssignmentHistoryResponse = PrisonerBedAssignmentHistoryResponse(bedAssignments = listOf(nomisBedAssignment())),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/bed-assignments"))
        .willReturn(okJson(jsonMapper.writeValueAsString(response))),
    )
  }

  fun stubGetBedAssignment(bookingId: Long, sequence: Int, response: BedAssignmentHistoryResponse = nomisBedAssignment(bookingId, sequence)) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/booking-id/$bookingId/bed-assignments/$sequence"))
        .willReturn(okJson(jsonMapper.writeValueAsString(response))),
    )
  }

  fun stubGetBedAssignmentError(bookingId: Long, sequence: Int, statusCode: Int) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/booking-id/$bookingId/bed-assignments/$sequence"))
        .willReturn(status(statusCode)),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun nomisBedAssignment(
  bookingId: Long = 12345,
  sequence: Int = 1,
) = BedAssignmentHistoryResponse(
  bookingId = bookingId,
  sequence = sequence,
  livingUnitId = 4321,
  assignmentDateTime = LocalDateTime.parse("2026-01-02T10:00:00"),
  prisonId = "MDI",
  audit = NomisAudit(createDatetime = LocalDateTime.parse("2026-01-02T10:00:00"), createUsername = "ME"),
)
