package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.VisitBalanceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi

@Component
class VisitBalanceNomisApiMockServer(private val objectMapper: ObjectMapper) {

  fun stubGetVisitBalance(
    prisonNumber: String = "A1234BC",
    response: VisitBalanceResponse = visitBalance(),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$prisonNumber/visit-orders/balance")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }
  fun stubGetVisitBalance(prisonNumber: String, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$prisonNumber/visit-orders/balance")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(objectMapper.writeValueAsString(error)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun visitBalance(): VisitBalanceResponse = VisitBalanceResponse(
  remainingVisitOrders = 24,
  remainingPrivilegedVisitOrders = 3,
)
