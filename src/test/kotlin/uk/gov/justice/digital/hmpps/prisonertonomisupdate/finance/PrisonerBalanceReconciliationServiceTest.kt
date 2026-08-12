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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.SubAccountBalanceForReconciliation
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationErrorPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.ReconciliationSuccessPageResult
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.AggregatedAccountDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerAggregatedAccountsDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.collections.mapOf

const val OFFENDER_NO = "A5678BZ"
const val OFFENDER_ID = 123456789000L

@SpringAPIServiceTest
@Import(
  PrisonerBalanceReconciliationService::class,
  FinanceNomisApiService::class,
  FinanceDpsApiService::class,
  FinanceNomisApiMockServer::class,
  NomisApiService::class,
  FinanceDpsApiMockServer::class,
  RetryApiService::class,
  FinanceConfiguration::class,
)
class PrisonerBalanceReconciliationServiceTest {

  @MockitoBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  private lateinit var financeNomisApi: FinanceNomisApiMockServer

  private val dpsApi = FinanceDpsApiExtension.dpsFinanceServer

  @Autowired
  private lateinit var service: PrisonerBalanceReconciliationService

  @BeforeEach
  fun setUp() {
    reset(telemetryClient)
  }

  @Nested
  inner class CheckMatch {
    private fun stubBalanceReconciliation(nomisBalance: PrisonerAggregatedAccountsDto, dpsBalance: Map<String, SubAccountBalanceForReconciliation>) {
      financeNomisApi.stubGetPrisonerAccounts(OFFENDER_ID, nomisBalance)
      dpsApi.stubGetPrisonerAccounts(OFFENDER_NO, dpsBalance)
    }

    @Test
    fun `will not report a mismatch when no differences found`() = runTest {
      stubBalanceReconciliation(
        nomisPrisonerAccounts(),
        dpsAccount(),
      )
      assertThat(service.checkPrisonerBalance(OFFENDER_ID)).isNull()
    }

    @Test
    fun `will report an extra DPS account`() = runTest {
      stubBalanceReconciliation(
        nomisPrisonerAccounts().copy(accounts = listOf()),
        dpsAccount(),
      )
      assertThat(service.checkPrisonerBalance(OFFENDER_ID)?.differences).isEqualTo(
        listOf(
          Difference(property = "prisoner-balances.accounts", dps = 1, nomis = 0),
        ),
      )
    }

    @Test
    fun `will ignore DPS accounts with a zero balance to show no mismatch`() = runTest {
      val dpsAccounts = dpsAccount() + dpsZeroAccount(1002) + dpsZeroAccount(1003)
      stubBalanceReconciliation(
        nomisPrisonerAccounts(),
        dpsAccounts,
      )

      assertThat(service.checkPrisonerBalance(OFFENDER_ID)).isNull()
    }

    @Test
    fun `will ignore DPS accounts with a zero balance when mismatch`() = runTest {
      stubBalanceReconciliation(
        nomisPrisonerAccounts(),
        dpsAccount(totalBalance = BigDecimal.TEN) + dpsZeroAccount(1002) + dpsZeroAccount(1003),
      )

      assertThat(service.checkPrisonerBalance(OFFENDER_ID)?.differences).isEqualTo(
        listOf(
          Difference(property = "prisoner-balances.accounts[0].balance: account code 1001", dps = BigDecimal.TEN, nomis = BigDecimal.valueOf(1.5)),
        ),
      )
    }

    @Test
    fun `will ignore Nomis accounts with a zero balance to show no mismatch`() = runTest {
      stubBalanceReconciliation(
        nomisPrisonerAccounts().copy(accounts = listOf(nomisAccount(), nomisZeroAccount(1002), nomisZeroAccount(1003))),
        dpsAccount(),
      )

      assertThat(service.checkPrisonerBalance(OFFENDER_ID)).isNull()
    }

    @Test
    fun `will ignore Nomis accounts with a zero balance when mismatch`() = runTest {
      stubBalanceReconciliation(
        nomisPrisonerAccounts().copy(accounts = listOf(nomisAccount(balance = BigDecimal.TEN), nomisZeroAccount(1002), nomisZeroAccount(1003))),
        dpsAccount(),
      )

      assertThat(service.checkPrisonerBalance(OFFENDER_ID)?.differences).isEqualTo(
        listOf(
          Difference(property = "prisoner-balances.accounts[0].balance: account code 1001", dps = BigDecimal.valueOf(1.5), nomis = BigDecimal.TEN),
        ),
      )
    }

    @Test
    fun `will report an extra Nomis account`() = runTest {
      stubBalanceReconciliation(
        nomisPrisonerAccounts().copy(accounts = listOf(nomisAccount(1001), nomisAccount(1002))),
        dpsAccount(),
      )
      assertThat(service.checkPrisonerBalance(OFFENDER_ID)?.differences).isEqualTo(
        listOf(
          Difference(property = "prisoner-balances.accounts", dps = 1, nomis = 2),
        ),
      )
    }

    @Test
    fun `will not report a mismatch when accounts are in a different order`() = runTest {
      stubBalanceReconciliation(
        nomisPrisonerAccounts().copy(accounts = listOf(nomisAccount(1001), nomisAccount(1002))),
        dpsAccount(accountCode = 1002) + dpsAccount(1001),
      )
      assertThat(service.checkPrisonerBalance(OFFENDER_ID)).isNull()
    }

    @Test
    fun `will not report a mismatch when accounts if hold balances don't match`() = runTest {
      stubBalanceReconciliation(
        nomisPrisonerAccounts(),
        dpsAccount(holdBalance = BigDecimal("0.5")),
      )
      assertThat(service.checkPrisonerBalance(OFFENDER_ID)).isNull()
    }

    @Test
    fun `will report a balance mismatch`() = runTest {
      stubBalanceReconciliation(
        nomisPrisonerAccounts(),
        dpsAccount(totalBalance = BigDecimal("100")),
      )
      assertThat(service.checkPrisonerBalance(OFFENDER_ID)?.differences).isEqualTo(
        listOf(
          Difference(property = "prisoner-balances.accounts[0].balance: account code 1001", dps = BigDecimal("100"), nomis = BigDecimal("1.5")),
        ),
      )
    }

    @Test
    fun `will report an account code mismatch`() = runTest {
      stubBalanceReconciliation(
        nomisPrisonerAccounts(),
        dpsAccount(accountCode = 1234),
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
      financeNomisApi.stubGetPrisonerBalanceIdentifiersInRange(
        fromRootOffenderId = 4,
        toRootOffenderId = 8,
      )
      val actual = service.getOffenderIdsInRange(4, 8)

      assertThat(actual).isInstanceOf(ReconciliationSuccessPageResult::class.java)
      actual as ReconciliationSuccessPageResult
      assertThat(actual.ids).isEqualTo(listOf(5L, 6L, 7L, 8L))
    }

    @Test
    fun `will return id list when filtering by prison`() = runTest {
      financeNomisApi.stubGetPrisonerBalanceIdentifiersInRange(
        fromRootOffenderId = 4,
        toRootOffenderId = 8,
      )

      val actual = service.getOffenderIdsInRange(4, 8, filterPrisonIds = listOf("MDI"))

      assertThat(actual).isInstanceOf(ReconciliationSuccessPageResult::class.java)
      actual as ReconciliationSuccessPageResult
      assertThat(actual.ids).isEqualTo(listOf(5L, 6L, 7L, 8L))
    }

    @Test
    fun `will report telemetry on error`() = runTest {
      financeNomisApi.stubGetPrisonerBalanceIdentifiersInRange(4, 8, status = 500)

      val actual = service.getOffenderIdsInRange(4, 8)

      assertThat(actual).isInstanceOf(ReconciliationErrorPageResult::class.java)
      actual as ReconciliationErrorPageResult
      assertThat(actual.error).isInstanceOf(WebClientResponseException.InternalServerError::class.java)

      verify(telemetryClient).trackEvent(
        eq("prisoner-balance-reports-reconciliation-mismatch-page-error"),
        check {
          assertThat(it).containsEntry("fromRootOffenderId", "4")
          assertThat(it).containsEntry("toRootOffenderId", "8")
          assertThat(it).containsKey("error")
          assertThat(it["error"]).startsWith("500 Internal Server Error from GET")
        },
        isNull(),
      )
    }
  }
}

fun dpsAccount(accountCode: Int = 1001, totalBalance: BigDecimal = BigDecimal.valueOf(1.5), holdBalance: BigDecimal = BigDecimal.valueOf(0.3)) = mapOf(
  accountCode.toString() to SubAccountBalanceForReconciliation(
    totalBalance = totalBalance,
    holdBalance = holdBalance,
    balanceDateTime = LocalDateTime.now(),
  ),
)

private fun dpsZeroAccount(accountCode: Int = 1001) = dpsAccount(
  accountCode = accountCode,
  totalBalance = BigDecimal.valueOf(0.00),
  holdBalance = BigDecimal.ZERO,
)

fun nomisPrisonerAccounts(accountCode: Long = 1001, balance: BigDecimal = BigDecimal.valueOf(1.5)) = PrisonerAggregatedAccountsDto(
  rootOffenderId = OFFENDER_ID,
  prisonNumber = OFFENDER_NO,
  accounts = listOf(nomisAccount(accountCode, balance)),
)

private fun nomisZeroAccount(accountCode: Long = 1001) = AggregatedAccountDto(
  accountCode = accountCode,
  balance = BigDecimal.valueOf(0),
)

private fun nomisAccount(accountCode: Long = 1001, balance: BigDecimal = BigDecimal.valueOf(1.5)) = AggregatedAccountDto(
  accountCode = accountCode,
  balance = balance,
)
