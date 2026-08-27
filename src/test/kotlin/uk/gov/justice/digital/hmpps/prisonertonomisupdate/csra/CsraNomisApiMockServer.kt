package uk.gov.justice.digital.hmpps.prisonertonomisupdate.csra

import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerCsrasResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

@Component
class CsraNomisApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubGetCsrasForPrisoner(offenderNo: String, response: PrisonerCsrasResponse) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/prisoners/$offenderNo/csras"))
        .willReturn(okJson(jsonMapper.writeValueAsString(response))),
    )
  }
  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
