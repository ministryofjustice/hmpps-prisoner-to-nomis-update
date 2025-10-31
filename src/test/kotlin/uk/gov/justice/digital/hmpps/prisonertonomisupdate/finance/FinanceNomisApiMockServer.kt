package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import com.github.tomakehurst.wiremock.client.WireMock.status
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.config.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonAccountBalanceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonBalanceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.RootOffenderIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.pagedModelContent
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.collections.map
import kotlin.math.min

@Component
class FinanceNomisApiMockServer(private val objectMapper: ObjectMapper) {

  fun stubGetPrisonerBalanceIdentifiersFromId(response: RootOffenderIdsWithLast) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/finance/prisoners/ids/all-from-id"))
        .willReturn(okJson(objectMapper.writeValueAsString(response))),
    )
  }

  fun stubGetPrisonerBalanceIdentifiersFromIdError(status: Int = 500) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/finance/prisoners/ids/all-from-id"))
        .willReturn(status(status)),
    )
  }

  fun stubGetPrisonerAccountDetails(rootOffenderId: Long, response: PrisonerBalanceDto) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/finance/prisoners/$rootOffenderId/balance"))
        .willReturn(okJson(objectMapper.writeValueAsString(response))),
    )
  }

  fun stubGetPrisonBalanceIds(totalElements: Long = 20, prisonIdPrefix: String = "MDI") {
    val content: List<String> = (1..totalElements).map { "$prisonIdPrefix$it" }
    nomisApi.stubFor(
      get(urlPathEqualTo("/finance/prison/ids")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(objectMapper.writeValueAsString(content)),
      ),
    )
  }
  fun stubGetPrisonBalanceIdsWithError(responseCode: Int) {
    nomisApi.stubFor(
      get(
        urlPathEqualTo("/finance/prison/ids"),
      )
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(responseCode)
            .withBody("""{"message":"Error"}"""),
        ),
    )
  }

  fun stubGetPrisonBalance(prisonId: String = "MDI", response: String) {
    nomisApi.stubFor(
      get(urlEqualTo("/finance/prison/$prisonId/balance")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(response),
      ),
    )
  }

  fun stubGetPrisonBalance(
    prisonId: String = "MDI",
    prisonBalance: PrisonBalanceDto = PrisonBalanceDto(
      prisonId = prisonId,
      accountBalances = listOf(
        PrisonAccountBalanceDto(
          accountCode = 2101,
          balance = BigDecimal("23.45"),
          transactionDate = LocalDateTime.parse("2025-06-02T02:02:03"),
        ),
      ),
    ),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/finance/prison/${prisonBalance.prisonId}/balance")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            objectMapper.writeValueAsString(prisonBalance),
          ),
      ),
    )
  }

  fun stubGetPrisonBalance(
    prisonId: String = "MDI",
    status: HttpStatus = HttpStatus.NOT_FOUND,
    error: ErrorResponse = ErrorResponse(status = status.value()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/finance/prison/$prisonId/balance")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(status.value())
          .withBody(
            objectMapper.writeValueAsString(error),
          ),
      ),
    )
  }

  fun stubGetRootOffenderIds(totalElements: Long = 20, pageSize: Long = 20, firstRootOffenderId: Long = 10000, prisonId: String = "ASI") {
    val content: List<Long> = (1..min(pageSize, totalElements)).map { firstRootOffenderId + it - 1 }
    nomisApi.stubFor(
      get(urlPathEqualTo("/finance/prisoners/ids"))
        .withQueryParam("prisonId", equalTo(prisonId))
        .willReturn(
          okJson(
            pagedModelContent(
              objectMapper = NomisApiExtension.objectMapper,
              content = content,
              pageSize = pageSize,
              pageNumber = 0,
              totalElements = totalElements,
            ),
          ),
        ),
    )
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}

fun prisonBalanceDto(prisonId: String = "MDI"): PrisonBalanceDto = PrisonBalanceDto(
  prisonId = prisonId,
  accountBalances = listOf(prisonAccountBalanceDto()),
)

fun prisonAccountBalanceDto(accountCode: Long = 2101): PrisonAccountBalanceDto = PrisonAccountBalanceDto(
  accountCode = accountCode,
  balance = BigDecimal.valueOf(23.45),
  transactionDate = LocalDateTime.parse("2025-06-01T01:02:03"),
)
