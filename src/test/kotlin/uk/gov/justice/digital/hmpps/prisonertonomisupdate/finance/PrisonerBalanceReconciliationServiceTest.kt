package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.PrisonerSubAccountDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.PrisonerSubAccountDetailsList
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerAccountDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerBalanceDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.RootOffenderIdsWithLast
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.math.BigDecimal
import java.time.LocalDateTime

private const val OFFENDER_NO = "A5678BZ"
private const val OFFENDER_ID = 123456789000L

@SpringAPIServiceTest
@Import(
  PrisonerBalanceReconciliationService::class,
  FinanceNomisApiService::class,
  FinanceDpsApiService::class,
  FinanceNomisApiMockServer::class,
  FinanceDpsApiMockServer::class,
  RetryApiService::class,
  FinanceConfiguration::class,
)
class PrisonerBalanceReconciliationServiceTest {

  @MockitoBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  private lateinit var nomisApi: FinanceNomisApiMockServer

  private val dpsApi = FinanceDpsApiExtension.dpsFinanceServer

  @Autowired
  private lateinit var service: PrisonerBalanceReconciliationService

  @BeforeEach
  fun setUp() {
    reset(telemetryClient)
  }

  @Nested
  inner class CheckMatch {
    private fun stubBalance(nomisBalance: PrisonerBalanceDto, dpsBalance: PrisonerSubAccountDetailsList) {
      nomisApi.stubGetPrisonerAccountDetails(OFFENDER_ID, nomisBalance)
      dpsApi.stubListPrisonerAccounts(OFFENDER_NO, dpsBalance)
    }

    @Test
    fun `will not report a mismatch when no differences found`() = runTest {
      stubBalance(
        nomisPrisonerBalanceResponse(),
        dpsPrisonerAccountResponse(),
      )
      assertThat(service.checkPrisonerBalance(OFFENDER_ID)).isNull()
    }

    @Test
    fun `will report an extra DPS account`() = runTest {
      stubBalance(
        nomisPrisonerBalanceResponse().copy(accounts = listOf()),
        dpsPrisonerAccountResponse(),
      )
      assertThat(service.checkPrisonerBalance(OFFENDER_ID)?.differences).isEqualTo(
        listOf(
          Difference(property = "prisoner-balances.accounts", dps = 1, nomis = 0),
        ),
      )
    }

    @Test
    fun `will report an extra Nomis account`() = runTest {
      stubBalance(
        nomisPrisonerBalanceResponse().copy(accounts = listOf(nomisAccount(1001), nomisAccount(1002))),
        dpsPrisonerAccountResponse(),
      )
      assertThat(service.checkPrisonerBalance(OFFENDER_ID)?.differences).isEqualTo(
        listOf(
          Difference(property = "prisoner-balances.accounts", dps = 1, nomis = 2),
        ),
      )
    }

    @Test
    fun `will not report a mismatch when accounts are in a different order`() = runTest {
      stubBalance(
        nomisPrisonerBalanceResponse().copy(accounts = listOf(nomisAccount(1001), nomisAccount(1002))),
        dpsPrisonerAccountResponse().copy(items = listOf(dpsAccount(1002), dpsAccount(1001))),
      )
      assertThat(service.checkPrisonerBalance(OFFENDER_ID)).isNull()
    }

    @Test
    fun `will report a balance mismatch`() = runTest {
      val dps = dpsPrisonerAccountResponse()
      stubBalance(
        nomisPrisonerBalanceResponse(),
        dps.copy(items = listOf(dps.items.first().copy(balance = BigDecimal("100")))),
      )
      assertThat(service.checkPrisonerBalance(OFFENDER_ID)?.differences).isEqualTo(
        listOf(
          Difference(property = "prisoner-balances.accounts[0].balance", dps = BigDecimal("100"), nomis = BigDecimal("1.5")),
        ),
      )
    }

    @Test
    fun `will report a hold balance mismatch`() = runTest {
      val dps = dpsPrisonerAccountResponse()
      stubBalance(
        nomisPrisonerBalanceResponse(),
        dps.copy(items = listOf(dps.items.first().copy(holdBalance = BigDecimal("100")))),
      )
      assertThat(service.checkPrisonerBalance(OFFENDER_ID)?.differences).isEqualTo(
        listOf(
          Difference(property = "prisoner-balances.accounts[0].holdBalance", dps = BigDecimal("100"), nomis = BigDecimal("0.3")),
        ),
      )
    }

    @Test
    fun `will handle a null hold balance`() = runTest {
      val nomis = nomisPrisonerBalanceResponse()
      stubBalance(
        nomis.copy(accounts = listOf(nomis.accounts.first().copy(holdBalance = null))),
        dpsPrisonerAccountResponse(),
      )
      assertThat(service.checkPrisonerBalance(OFFENDER_ID)?.differences).isEqualTo(
        listOf(
          Difference(property = "prisoner-balances.accounts[0].holdBalance", dps = BigDecimal("0.3"), nomis = null),
        ),
      )
    }

    @Test
    fun `will handle an equal hold balance with different scale`() = runTest {
      val nomis = nomisPrisonerBalanceResponse()
      stubBalance(
        nomis.copy(accounts = listOf(nomis.accounts.first().copy(holdBalance = BigDecimal("0.30")))),
        dpsPrisonerAccountResponse(),
      )
      assertThat(service.checkPrisonerBalance(OFFENDER_ID)?.differences).isNull()
    }

    @Test
    fun `will report an account code mismatch`() = runTest {
      val dps = dpsPrisonerAccountResponse()
      stubBalance(
        nomisPrisonerBalanceResponse(),
        dps.copy(items = listOf(dps.items.first().copy(code = 1234))),
      )
      assertThat(service.checkPrisonerBalance(OFFENDER_ID)?.differences).isEqualTo(
        listOf(
          Difference(property = "prisoner-balances.accounts[0].accountCode", dps = 1234, nomis = 1001),
        ),
      )
    }
  }

  @Nested
  inner class GetPrisonerIdsForPage {
    @Test
    fun `will return id list`() = runTest {
      nomisApi.stubGetPrisonerBalanceIdentifiersFromId(
        RootOffenderIdsWithLast(
          rootOffenderIds = listOf(5, 6, 7, 8),
          lastOffenderId = 8L,
        ),
      )
      val actual = service.getPrisonerIdsForPage(OFFENDER_ID)

      assertThat(actual).isInstanceOf(ReconciliationSuccessPageResult::class.java)
      actual as ReconciliationSuccessPageResult
      assertThat(actual.ids).isEqualTo(listOf(5L, 6L, 7L, 8L))
      assertThat(actual.last).isEqualTo(8L)
    }

    @Test
    fun `will report telemetry on error`() = runTest {
      nomisApi.stubGetPrisonerBalanceIdentifiersFromIdError()

      val actual = service.getPrisonerIdsForPage(OFFENDER_ID)

      assertThat(actual).isInstanceOf(ReconciliationErrorPageResult::class.java)
      actual as ReconciliationErrorPageResult
      assertThat(actual.error).isInstanceOf(WebClientResponseException.InternalServerError::class.java)

      verify(telemetryClient).trackEvent(
        eq("prisoner-balance-mismatch-page-error"),
        check {
          assertThat(it).containsEntry("lastOffenderId", OFFENDER_ID.toString())
          assertThat(it).containsKey("error")
          assertThat(it["error"]).startsWith("500 Internal Server Error from GET")
        },
        isNull(),
      )
    }
  }
}

fun nomisPrisonerBalanceResponse() = PrisonerBalanceDto(
  rootOffenderId = OFFENDER_ID,
  prisonNumber = OFFENDER_NO,
  accounts = listOf(nomisAccount(1001)),
)

fun dpsPrisonerAccountResponse() = PrisonerSubAccountDetailsList(
  items = listOf(dpsAccount(1001)),
)

private fun nomisAccount(accountCode: Long = 1001): PrisonerAccountDto = PrisonerAccountDto(
  prisonId = "MDI",
  lastTransactionId = 999999L,
  transactionDate = LocalDateTime.now(),
  accountCode = accountCode,
  balance = BigDecimal.valueOf(1.5),
  holdBalance = BigDecimal.valueOf(0.3),
)

private fun dpsAccount(accountCode: Int = 1001): PrisonerSubAccountDetails = PrisonerSubAccountDetails(
  code = accountCode,
  name = "SPEND",
  prisonNumber = OFFENDER_NO,
  balance = BigDecimal.valueOf(1.5),
  holdBalance = BigDecimal.valueOf(0.3),
)
