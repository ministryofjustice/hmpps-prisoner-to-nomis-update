package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerVisitBalanceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate

@Component
class VisitBalanceNomisApiMockServer(private val objectMapper: ObjectMapper) {

  fun stubGetVisitBalance(
    response: PrisonerVisitBalanceResponse = visitBalance(),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/${response.prisonNumber}/visit-orders/balance")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(response)),
      ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun visitBalance(): PrisonerVisitBalanceResponse = PrisonerVisitBalanceResponse(
  prisonNumber = "A1234BC",
  remainingVisitOrders = 24,
  remainingPrivilegedVisitOrders = 3,
  lastIEPAllocationDate = LocalDate.parse("2025-01-15"),
)
