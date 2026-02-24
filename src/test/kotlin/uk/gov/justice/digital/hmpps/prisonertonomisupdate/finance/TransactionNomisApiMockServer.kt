package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.GeneralLedgerTransactionDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTransactionDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerTransactionIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerTransactionIdsPage
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class TransactionNomisApiMockServer(private val jsonMapper: JsonMapper) {
  companion object {
    fun nomisPrisonTransaction(transactionId: Long = 1234) = GeneralLedgerTransactionDto(
      transactionId = transactionId,
      transactionEntrySequence = 1,
      generalLedgerEntrySequence = 1,
      caseloadId = "MDI",
      amount = BigDecimal(5.4),
      type = "SPEN",
      postingType = GeneralLedgerTransactionDto.PostingType.CR,
      accountCode = 1501,
      description = "General Ledger Account Transfer",
      transactionTimestamp = LocalDateTime.parse("2021-02-03T04:05:09"),
      reference = "ref 123",
      createdAt = LocalDateTime.parse("2021-02-03T04:05:07"),
      createdBy = "J_BROWN",
      createdByDisplayName = "Jim Brown",
      lastModifiedAt = LocalDateTime.parse("2021-02-03T04:05:59"),
      lastModifiedBy = "T_SMITH",
      lastModifiedByDisplayName = "Tim Smith",
    )

    fun nomisPrisonerTransaction(transactionId: Long = 1001, bookingId: Long = 2345) = OffenderTransactionDto(
      transactionId = transactionId,
      transactionEntrySequence = 1,
      offenderId = 1234,
      offenderNo = "A1234AA",
      bookingId = bookingId,
      caseloadId = "MDI",
      subAccountType = OffenderTransactionDto.SubAccountType.REG,
      type = "type",
      reference = "FG1/12",
      clientReference = "clientUniqueRef",
      entryDate = LocalDate.parse("2025-06-01"),
      description = "entryDescription",
      amount = BigDecimal.valueOf(162.00),
      createdAt = LocalDateTime.parse("2024-06-18T14:30"),
      postingType = OffenderTransactionDto.PostingType.CR,
      createdBy = "me",
      createdByDisplayName = "Me",
      lastModifiedAt = LocalDateTime.now(),
      lastModifiedBy = "you",
      lastModifiedByDisplayName = "You",
      generalLedgerTransactions = listOf(nomisPrisonTransaction()),
    )
  }

  fun stubGetPrisonerTransactionIdsByLastId(
    lastTransactionId: Long = 0,
    entryDate: LocalDate = LocalDate.parse("2021-02-03"),
    size: Long = 20,
    content: List<PrisonerTransactionIdResponse> = listOf(PrisonerTransactionIdResponse(lastTransactionId)),
  ) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/transactions/from/$lastTransactionId"))
        .withQueryParam("entryDate", equalTo(entryDate.toString()))
        .withQueryParam("size", equalTo(size.toString()))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(PrisonerTransactionIdsPage(content))),
        ),
    )
  }

  // TODO - for testing, do we need to set up method to return multiple OffenderTransactionDto
  fun stubGetPrisonerTransactions(lastTransactionId: Long = 0, transactionEntrySequence: Int = 1, response: List<OffenderTransactionDto> = listOf(nomisPrisonerTransaction())) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/transactions/from/$lastTransactionId/$transactionEntrySequence"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetTransactionFromDate(fromDate: String = "2025-08-10", response: Long = 45) {
    nomisApi.stubFor(
      get(urlPathEqualTo("/transactions/$fromDate/first"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetPrisonTransaction(
    transactionId: Long = 1234,
    response: List<GeneralLedgerTransactionDto> = listOf(nomisPrisonTransaction(transactionId)),
  ) {
    response.apply {
      nomisApi.stubFor(
        get(urlPathEqualTo("/transactions/$transactionId/general-ledger")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              jsonMapper.writeValueAsString(response),
            ),
        ),
      )
    }
  }

  fun stubGetPrisonTransactionsOn(
    prisonId: String = "MDI",
    date: LocalDate = LocalDate.now(),
    response: List<GeneralLedgerTransactionDto> = listOf(nomisPrisonTransaction()),
  ) {
    nomisApi.stubFor(
      get(urlEqualTo("/transactions/prison/$prisonId?entryDate=$date")).willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withStatus(HttpStatus.OK.value())
          .withBody(
            jsonMapper.writeValueAsString(response),
          ),
      ),
    )
  }

  fun stubGetPrisonerTransaction(
    transactionId: Long = 2345,
    response: List<OffenderTransactionDto>? = listOf(nomisPrisonerTransaction(transactionId)),
  ) {
    response?.apply {
      nomisApi.stubFor(
        get(urlPathEqualTo("/transactions/$transactionId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(
              jsonMapper.writeValueAsString(response),
            ),
        ),
      )
    }
  }

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
