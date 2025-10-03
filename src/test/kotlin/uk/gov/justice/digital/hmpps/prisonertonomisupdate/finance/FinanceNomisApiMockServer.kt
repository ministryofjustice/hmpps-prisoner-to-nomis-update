package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PagedModelLong
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

@Component
class FinanceNomisApiMockServer(private val objectMapper: ObjectMapper) {

  fun stubGetPrisonersIds(lastTransactionId: Long? = null, response: PagedModelLong) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/finance/prisoners/ids"))
        .willReturn(okJson(objectMapper.writeValueAsString(response))),
    )
  }

  fun stubGetPrisonerAccountDetails(rootOffenderId: Long, response: PrisonerBalanceDto) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/finance/prisoners/$rootOffenderId/balance"))
        .willReturn(okJson(objectMapper.writeValueAsString(response))),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
