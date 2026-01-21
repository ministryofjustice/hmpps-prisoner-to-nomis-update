package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.OffenderTransactionDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension.Companion.nomisApi
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Component
class TransactionNomisApiMockServer(private val jsonMapper: JsonMapper) {
  companion object {
    fun offenderTransactionDto(transactionId: Long = 1001, bookingId: Long = 123456): OffenderTransactionDto = OffenderTransactionDto(
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
      amount = BigDecimal.valueOf(2.34),
      createdAt = LocalDateTime.now(),
      postingType = OffenderTransactionDto.PostingType.CR,
      createdBy = "me",
      createdByDisplayName = "Me",
      lastModifiedAt = LocalDateTime.now(),
      lastModifiedBy = "you",
      lastModifiedByDisplayName = "You",
      generalLedgerTransactions = listOf(),

    )
  }

  // TODO - for testing, do we need to set up method to return multiple OffenderTransactionDto
  fun stubGetTransactions(lastTransactionId: Long = 0, transactionEntrySequence: Int = 1, response: List<OffenderTransactionDto> = listOf(offenderTransactionDto())) {
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

  fun verify(pattern: RequestPatternBuilder) = nomisApi.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = nomisApi.verify(count, pattern)
}
