package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.github.tomakehurst.wiremock.client.CountMatchingStrategy
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.TransactionMappingDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension.Companion.mappingServer
import java.util.UUID

@Component
class TransactionMappingApiMockServer(private val jsonMapper: JsonMapper) {

  fun stubGetByNomisTransactionIdOrNull(
    nomisTransactionId: Long = 1234,
    dpsTransactionId: String = UUID.randomUUID().toString(),
    mapping: TransactionMappingDto? = TransactionMappingDto(
      offenderNo = "A1234KT",
      nomisBookingId = 123456,
      nomisTransactionId = nomisTransactionId,
      dpsTransactionId = dpsTransactionId,
      mappingType = TransactionMappingDto.MappingType.MIGRATED,
    ),
  ) {
    mapping?.apply {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/transactions/nomis-transaction-id/$nomisTransactionId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.OK.value())
            .withBody(jsonMapper.writeValueAsString(mapping)),
        ),
      )
    } ?: run {
      mappingServer.stubFor(
        get(urlEqualTo("/mapping/transactions/nomis-transaction-id/$nomisTransactionId")).willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(HttpStatus.NOT_FOUND.value())
            .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = 404))),
        ),
      )
    }
  }

  fun verify(pattern: RequestPatternBuilder) = mappingServer.verify(pattern)
  fun verify(count: Int, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
  fun verify(count: CountMatchingStrategy, pattern: RequestPatternBuilder) = mappingServer.verify(count, pattern)
}
