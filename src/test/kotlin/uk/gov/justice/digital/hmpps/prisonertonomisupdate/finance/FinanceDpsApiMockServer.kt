package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.FinanceDpsApiExtension.Companion.generalLedgerTransaction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.FinanceDpsApiExtension.Companion.offenderTransaction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.SyncGeneralLedgerTransactionResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.SyncOffenderTransactionResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension.Companion.objectMapper
import java.time.LocalDateTime
import java.util.UUID

class FinanceDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {

    @JvmField
    val dpsFinanceServer = FinanceDpsApiMockServer()
    lateinit var objectMapper: ObjectMapper

    fun offenderTransaction(uuid: UUID = UUID.randomUUID()) = SyncOffenderTransactionResponse(
      synchronizedTransactionId = uuid,
      caseloadId = "GMI",
      transactionTimestamp = LocalDateTime.parse("2024-06-18T14:30"),
      createdAt = LocalDateTime.parse("2024-06-18T14:50"),
      createdBy = "JD12345",
      createdByDisplayName = "J Doe",
      legacyTransactionId = 123456,
      lastModifiedAt = LocalDateTime.parse("2022-07-15T23:03:01"),
      lastModifiedBy = "AB11DZ",
      lastModifiedByDisplayName = "U Dated",
      transactions = listOf(
        OffenderTransaction(
          entrySequence = 1,
          offenderId = 1015388,
          offenderDisplayId = "AA001AA",
          offenderBookingId = 455987,
          subAccountType = "REG",
          postingType = OffenderTransaction.PostingType.DR,
          type = "OT",
          description = "Sub-Account Transfer",
          amount = 162.toDouble(),
          reference = "string",
          generalLedgerEntries = listOf(
            GeneralLedgerEntry(
              entrySequence = 1,
              code = 2101,
              postingType = GeneralLedgerEntry.PostingType.DR,
              amount = 162.toDouble(),
            ),
          ),
        ),
      ),
    )

    fun generalLedgerTransaction(uuid: UUID = UUID.randomUUID()) = SyncGeneralLedgerTransactionResponse(
      synchronizedTransactionId = uuid,
      description = "General Ledger Account Transfer",
      caseloadId = "GMI",
      transactionType = "GJ",
      transactionTimestamp = LocalDateTime.parse("2011-09-30T09:08"),
      createdAt = LocalDateTime.parse("2024-06-18T14:50"),
      createdBy = "JD12345",
      createdByDisplayName = "J Doe",
      legacyTransactionId = 123456,
      lastModifiedAt = LocalDateTime.parse("2022-07-15T23:03:01"),
      lastModifiedBy = "AB11DZ",
      lastModifiedByDisplayName = "U Dated",
      reference = "REF12345",
      generalLedgerEntries = listOf(
        GeneralLedgerEntry(
          entrySequence = 1,
          code = 2101,
          postingType = GeneralLedgerEntry.PostingType.DR,
          amount = 162.toDouble(),
        ),
      ),
    )
  }

  override fun beforeAll(context: ExtensionContext) {
    dpsFinanceServer.start()
    objectMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonObjectMapper") as ObjectMapper)
  }

  override fun beforeEach(context: ExtensionContext) {
    dpsFinanceServer.resetAll()
  }

  override fun afterAll(context: ExtensionContext) {
    dpsFinanceServer.stop()
  }
}

class FinanceDpsApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8097
  }

  fun stubHealthPing(status: Int) {
    stubFor(
      get("/health/ping").willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(if (status == 200) "pong" else "some error")
          .withStatus(status),
      ),
    )
  }

  fun stubGetOffenderTransaction(transactionId: String, response: SyncOffenderTransactionResponse = offenderTransaction()) {
    stubFor(
      get("/sync/offender-transactions/$transactionId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }

  fun stubGetGeneralLedgerTransaction(transactionId: String, response: SyncGeneralLedgerTransactionResponse = generalLedgerTransaction()) {
    stubFor(
      get("/sync/general-ledger-transactions/$transactionId")
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(response)),
        ),
    )
  }
}
