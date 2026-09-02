package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra

import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.ok
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CsraCreateResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CsraGetDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerCsrasResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

@Component
class CsraNomisApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubCreateCsra(offenderNo: String, response: CsraCreateResponse) {
    nomisApi.stubFor(
      post(urlPathEqualTo("/prisoners/$offenderNo/csra"))
        .willReturn(okJson(jsonMapper.writeValueAsString(response))),
    )
  }

  fun stubUpdateCsra(bookingId: Long, sequence: Int) {
    nomisApi.stubFor(
      put(urlPathEqualTo("/prisoners/booking-id/$bookingId/csra/$sequence"))
        .willReturn(ok()),
    )
  }

  fun stubGetCsrasForPrisoner(offenderNo: String, response: PrisonerCsrasResponse) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/csras"))
        .willReturn(okJson(jsonMapper.writeValueAsString(response))),
    )
  }

  fun stubGetCurrentCsraForPrisoner(offenderNo: String, response: CsraGetDto) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/csras/current"))
        .willReturn(okJson(jsonMapper.writeValueAsString(response))),
    )
  }

  fun stubGetCurrentCsraForPrisonerError(offenderNo: String, statusCode: Int) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/csras/current"))
        .willReturn(status(statusCode)),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
