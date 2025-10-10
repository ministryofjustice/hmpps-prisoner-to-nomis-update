package uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.reset
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.PrisonerSubAccountDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.finance.model.PrisonerSubAccountDetailsList
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerAccountDto
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.PrisonerBalanceDto
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

  @Nested
  inner class CheckMatch {
    @BeforeEach
    fun setUp() {
      reset(telemetryClient)
    }

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
      assertThat(
        service.checkPrisonerBalance(OFFENDER_ID),
      ).isNull()
    }

    @Test
    fun `will report an extra DPS account`() = runTest {
      stubBalance(
        nomisPrisonerBalanceResponse().copy(accounts = listOf()),
        dpsPrisonerAccountResponse(), //.copy(balance = BigDecimal("100")),
      )

      assertThat(
        service.checkPrisonerBalance(OFFENDER_ID)?.differences,
      ).isEqualTo(
        listOf(
          Difference(property = "prisoner-balances.accounts", dps = 1, nomis = 0),
        ),
      )
    }

    @Test
    fun `will report a balance mismatch`() = runTest {
      val dps = dpsPrisonerAccountResponse()
      stubBalance(
        nomisPrisonerBalanceResponse(),
        dps.copy(
          items = listOf(
            dps.items.first().copy(balance = BigDecimal("100")),
          ),
        ),
      )

      assertThat(
        service.checkPrisonerBalance(OFFENDER_ID)?.differences,
      ).isEqualTo(
        listOf(
          Difference(property = "prisoner-balances.accounts[0].balance", dps = BigDecimal("100"), nomis = BigDecimal("1.5")),
        ),
      )
    }
  }
}

fun nomisPrisonerBalanceResponse() = PrisonerBalanceDto(
  rootOffenderId = OFFENDER_ID,
  prisonNumber = OFFENDER_NO,
  accounts = listOf(
    PrisonerAccountDto(
      prisonId = "MDI",
      lastTransactionId = 999999L,
      transactionDate = LocalDateTime.now(),
      accountCode = 1001,
      balance = BigDecimal.valueOf(1.5),
      holdBalance = BigDecimal.valueOf(0.3),
    ),
  ),
)

fun dpsPrisonerAccountResponse() = PrisonerSubAccountDetailsList(
  items = listOf(
    PrisonerSubAccountDetails(
      code = 1001,
      name = "SPEND",
      prisonNumber = OFFENDER_NO,
      balance = BigDecimal.valueOf(1.5),
      holdBalance = BigDecimal.valueOf(0.3),
    ),
  ),
)
