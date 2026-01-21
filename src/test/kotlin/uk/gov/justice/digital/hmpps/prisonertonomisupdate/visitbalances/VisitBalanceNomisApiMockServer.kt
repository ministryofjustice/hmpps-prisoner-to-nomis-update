package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visitbalances

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitBalanceAdjustmentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CreateVisitBalanceAdjustmentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.UpdateVisitBalanceRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.VisitBalanceResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.time.LocalDate

@Component
class VisitBalanceNomisApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubGetVisitBalance(
    prisonNumber: String = "A1234KT",
    response: VisitBalanceResponse = visitBalance(),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$prisonNumber/visit-balance")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(jsonMapper.writeValueAsString(response)),
      ),
    )
  }
  fun stubGetVisitBalance(prisonNumber: String, status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      get(urlEqualTo("/prisoners/$prisonNumber/visit-balance")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(jsonMapper.writeValueAsString(error)),
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
            .withBody(jsonMapper.writeValueAsString(createVisitBalanceAdjResponse())),
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
            .withBody(jsonMapper.writeValueAsString(error)),
        ),
    )
  }
  fun stubPutVisitBalance(prisonNumber: String = "A1234KT", status: HttpStatus = HttpStatus.NO_CONTENT, error: ErrorResponse = ErrorResponse(status = status.value())) {
    nomisApi.stubFor(
      put("/prisoners/$prisonNumber/visit-balance")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value()),
        ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun updateVisitBalanceRequest(): UpdateVisitBalanceRequest = UpdateVisitBalanceRequest(
  remainingVisitOrders = 24,
  remainingPrivilegedVisitOrders = 3,
)
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
  comment = "A comment",
)
