package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerAggregatedAccountsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

@Component
class PrisonerBalanceNomisApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubGetPrisonerAccounts(rootOffenderId: Long, response: PrisonerAggregatedAccountsDto) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/finance/prisoners/rootOffenderId/$rootOffenderId/balance/reconcile"))
        .willReturn(okJson(jsonMapper.writeValueAsString(response))),
    )
  }

  fun stubGetPrisonerAccountsWithError(rootOffenderId: Long, status: HttpStatus) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/finance/prisoners/rootOffenderId/$rootOffenderId/balance/reconcile"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = status.value()))),
        ),
    )
  }
  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
