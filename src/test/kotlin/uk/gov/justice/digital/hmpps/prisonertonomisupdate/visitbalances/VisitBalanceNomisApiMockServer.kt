package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitBalanceAdjustmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitBalanceAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.VisitBalanceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate

@Component
class VisitBalanceNomisApiMockServer(private val objectMapper: ObjectMapper) {

  fun stubGetVisitBalance(
    prisonNumber: String = "A1234KT",
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

  fun stubPostVisitBalanceAdjustment(prisonNumber: String = "A1234KT") {
    nomisApi.stubFor(
      post("/prisoners/$prisonNumber/visit-balance-adjustments")
        .willReturn(
          aResponse()
            .withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(createVisitBalanceAdjResponse())),
        ),
    )
  }
  fun stubPostVisitBalanceAdjustment(prisonNumber: String = "A1234KT", status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      post("/prisoners/$prisonNumber/visit-balance-adjustments")
        .willReturn(
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

fun createVisitBalanceAdjResponse(): CreateVisitBalanceAdjustmentResponse = CreateVisitBalanceAdjustmentResponse(
  visitBalanceAdjustmentId = 1234,
)
fun createVisitBalanceAdjRequest(): CreateVisitBalanceAdjustmentRequest = CreateVisitBalanceAdjustmentRequest(
  previousVisitOrderCount = 12,
  visitOrderChange = 2,
  previousPrivilegedVisitOrderCount = 7,
  privilegedVisitOrderChange = -1,
  adjustmentDate = LocalDate.parse("2021-01-18"),
  adjustmentReasonCode = "GOV",
  comment = "A comment",
  expiryBalance = 6,
  expiryDate = LocalDate.parse("2021-02-19"),
  endorsedStaffId = 123,
  authorisedStaffId = 345,
)
