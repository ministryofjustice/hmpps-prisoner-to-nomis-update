package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.okJson
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.http.HttpStatus
import org.springframework.test.context.junit.jupiter.SpringExtension
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.FinanceDpsApiExtension.Companion.offenderTransaction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.GeneralLedgerBalanceDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.GeneralLedgerBalanceDetailsList
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.GeneralLedgerEntry
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.OffenderTransaction
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.PrisonerEstablishmentBalanceDetailsList
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.PrisonerSubAccountDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.SyncGeneralLedgerTransactionResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.SyncOffenderTransactionResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomismappings.model.ErrorResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension.Companion.jsonMapper
import java.math.BigDecimal
import java.time.LocalDateTime
import java.util.UUID

class FinanceDpsApiExtension :
  BeforeAllCallback,
  AfterAllCallback,
  BeforeEachCallback {
  companion object {

    @JvmField
    val dpsFinanceServer = FinanceDpsApiMockServer()
    lateinit var jsonMapper: JsonMapper

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
  }
  override fun beforeAll(context: ExtensionContext) {
    dpsFinanceServer.start()
    jsonMapper = (SpringExtension.getApplicationContext(context).getBean("jacksonJsonMapper") as JsonMapper)
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
  fun ResponseDefinitionBuilder.withBody(body: Any): ResponseDefinitionBuilder {
    this.withBody(FinanceDpsApiExtension.jsonMapper.writeValueAsString(body))
    return this
  }

  fun stubGetOffenderTransaction(transactionId: String, response: SyncOffenderTransactionResponse = offenderTransaction()) {
    stubFor(
      get("/sync/offender-transactions/$transactionId")
        .willReturn(okJson(jsonMapper.writeValueAsString(response))),
    )
  }

  fun stubGetGeneralLedgerTransaction(dpsTransactionId: String, response: SyncGeneralLedgerTransactionResponse? = generalLedgerTransaction()) {
    response?.apply {
      stubFor(
        get("/sync/general-ledger-transactions/$dpsTransactionId")
          .willReturn(okJson(jsonMapper.writeValueAsString(response))),
      )
    }
      ?: run {
        stubFor(
          get("/sync/general-ledger-transactions/$dpsTransactionId")
            .willReturn(
              aResponse()
                .withHeader("Content-Type", "application/json")
                .withStatus(HttpStatus.NOT_FOUND.value())
                .withBody(jsonMapper.writeValueAsString(ErrorResponse(status = HttpStatus.NOT_FOUND.value()))),
            ),
        )
      }
  }

  fun stubListPrisonerAccounts(prisonNumber: String, response: PrisonerEstablishmentBalanceDetailsList) {
    stubFor(
      get("/reconcile/prisoner-balances/$prisonNumber")
        .willReturn(okJson(jsonMapper.writeValueAsString(response))),
    )
  }

  fun stubGetPrisonerSubAccountDetails(prisonerNo: String, accountCode: Int, response: PrisonerSubAccountDetails) {
    stubFor(
      get("/prisoners/$prisonerNo/accounts/$accountCode")
        .willReturn(okJson(jsonMapper.writeValueAsString(response))),
    )
  }

  fun stubGetPrisonBalance(prisonId: String = "MDI", response: String) {
    stubFor(
      get("/reconcile/general-ledger-balances/$prisonId")
        .willReturn(okJson(response)),
    )
  }
  fun stubGetPrisonBalance(prisonId: String = "MDI", response: GeneralLedgerBalanceDetailsList = prisonAccounts()) {
    stubFor(
      get("/reconcile/general-ledger-balances/$prisonId")
        .willReturn(okJson(jsonMapper.writeValueAsString(response))),
    )
  }
  fun stubGetPrisonBalance(prisonId: String = "MDI", status: HttpStatus, error: ErrorResponse = ErrorResponse(status = status.value())) {
    stubFor(
      get("/reconcile/general-ledger-balances/$prisonId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status.value())
            .withBody(error),
        ),
    )
  }
}

fun prisonAccounts(): GeneralLedgerBalanceDetailsList = GeneralLedgerBalanceDetailsList(items = listOf(prisonAccountDetails()))

fun prisonAccountDetails(accountCode: Int = 2101) = GeneralLedgerBalanceDetails(
  accountCode = accountCode,
  balance = BigDecimal.valueOf(23.45),
)
fun generalLedgerTransaction(uuid: UUID = UUID.randomUUID(), transactionId: Long = 1234) = SyncGeneralLedgerTransactionResponse(
  legacyTransactionId = transactionId,
  synchronizedTransactionId = uuid,
  description = "General Ledger Account Transfer",
  caseloadId = "MDI",
  transactionType = "SPEN",
  transactionTimestamp = LocalDateTime.parse("2021-02-03T04:05:09"),
  createdAt = LocalDateTime.parse("2021-02-03T04:05:07"),
  createdBy = "J_BROWN",
  createdByDisplayName = "Jim Brown",
  lastModifiedAt = LocalDateTime.parse("2021-02-03T04:05:59"),
  lastModifiedBy = "T_SMITH",
  lastModifiedByDisplayName = "Tim Smith",
  reference = "ref 123",
  generalLedgerEntries = listOf(
    GeneralLedgerEntry(
      entrySequence = 1,
      code = 1501,
      postingType = GeneralLedgerEntry.PostingType.CR,
      amount = 5.4,
    ),
  ),
)
