package uk.gov.justice.digital.hmpps.prisonertonomisupdate.calculatereleasedates

import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderSentenceCalculationResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate
import java.time.LocalDateTime

fun nomisSentenceCalculationResponse(
  id: Long = 12345,
  offenderNo: String = "A1234BC",
  bookingId: Long = 54321,
) = OffenderSentenceCalculationResponse(
  id = id,
  bookingId = bookingId,
  offenderNo = offenderNo,
  calculationDate = LocalDateTime.parse("2026-01-02T10:15:30"),
  calculationReasonCode = "NEW",
  recordDatetime = LocalDateTime.parse("2026-01-02T10:15:30"),
  createDatetime = LocalDateTime.parse("2026-01-02T10:15:30"),
  createUsername = "JANE.SMITH",
  commentText = "Calculation comment",
  crdCalculatedDate = LocalDate.parse("2030-03-04"),
  sedCalculatedDate = LocalDate.parse("2031-05-06"),
)

@Component
class CalculateReleaseDatesNomisApiMockServer(private val jsonMapper: JsonMapper) {
  fun stubGetSentenceCalculations(
    offenderNo: String = "A1234BC",
    response: List<OffenderSentenceCalculationResponse> = listOf(nomisSentenceCalculationResponse(offenderNo = offenderNo)),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/sentence-calculations"))
        .willReturn(okJson(jsonMapper.writeValueAsString(response))),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
}
