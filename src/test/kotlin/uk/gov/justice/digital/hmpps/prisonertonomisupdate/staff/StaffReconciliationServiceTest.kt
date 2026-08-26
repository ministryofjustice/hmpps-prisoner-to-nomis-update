package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

import com.microsoft.applicationinsights.TelemetryClient
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.reset
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.test.context.bean.override.mockito.MockitoBean
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CaseloadResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.RoleResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.PrisonUserAccount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.PrisonUserReconciliationResponse
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(
  StaffReconciliationService::class,
  StaffNomisApiService::class,
  StaffDpsApiService::class,
  StaffNomisApiMockServer::class,
  NomisApiService::class,
  StaffDpsApiMockServer::class,
  RetryApiService::class,
  StaffConfiguration::class,
)
class StaffReconciliationServiceTest {

  @MockitoBean
  lateinit var telemetryClient: TelemetryClient

  @Autowired
  private lateinit var nomisApi: StaffNomisApiMockServer

  private val dpsApi = StaffDpsApiExtension.dpsStaffServer

  @Autowired
  private lateinit var service: StaffReconciliationService

  @BeforeEach
  fun setUp() {
    reset(telemetryClient)
  }

  @Nested
  inner class CheckMatch {
    private fun stubStaffReconciliation(nomisStaff: StaffDetails, dpsStaff: PrisonUserReconciliationResponse) {
      nomisApi.stubGetStaffById(nomisStaff.id, response = nomisStaff)
      dpsApi.stubGetStaff(nomisStaff.id, response = dpsStaff)
    }

    @Nested
    inner class CheckStaffAccount {
      @Nested
      inner class Match {
        @Test
        fun `will not report a mismatch when no differences found`() = runTest {
          stubStaffReconciliation(nomisStaffDetails(), dpsStaffDetails())
          assertThat(service.checkStaffMatch(1234)).isNull()
        }

        @Test
        fun `will not report a mismatch when status values are both set to an inactive value`() = runTest {
          stubStaffReconciliation(
            nomisStaffDetails().copy(status = "SICK"),
            dpsStaffDetails().copy(status = PrisonUserReconciliationResponse.Status.INACTIVE),
          )
          assertThat(service.checkStaffMatch(1234)).isNull()
        }
      }

      @Nested
      inner class Mismatch {
        @Test
        fun `will report a mismatch when first names don't match`() = runTest {
          stubStaffReconciliation(nomisStaffDetails().copy(firstName = "FRED"), dpsStaffDetails())
          assertThat(service.checkStaffMatch(1234)?.differences).containsExactly(
            entry("firstName", "nomis=FRED, dps=JOHN"),
          )
        }

        @Test
        fun `will report a mismatch when last names don't match`() = runTest {
          stubStaffReconciliation(nomisStaffDetails().copy(lastName = "WHITE"), dpsStaffDetails())
          assertThat(service.checkStaffMatch(1234)?.differences).containsExactly(
            entry("lastName", "nomis=WHITE, dps=SMITH"),
          )
        }

        @Test
        fun `will report a mismatch when statuses don't match`() = runTest {
          stubStaffReconciliation(nomisStaffDetails().copy(status = "SICK"), dpsStaffDetails())
          assertThat(service.checkStaffMatch(1234)?.differences).containsExactly(
            entry("status", "nomis=INACT, dps=ACTIVE"),
          )
        }

        @Test
        fun `will report a mismatch when emailAddresses don't match`() = runTest {
          val nomisEmail = nomisStaffDetails().emailAddresses[0].copy(email = "fred@email.com")
          stubStaffReconciliation(nomisStaffDetails().copy(emailAddresses = listOf(nomisEmail)), dpsStaffDetails())
          assertThat(service.checkStaffMatch(1234)!!.differences).containsExactly(
            entry("emails", "nomis=[fred@email.com], dps=[john.smith@justice.gov.uk]"),
          )
        }

        @Test
        fun `will report a mismatch when no emailAddresses in Nomis`() = runTest {
          stubStaffReconciliation(nomisStaffDetails().copy(emailAddresses = listOf()), dpsStaffDetails())
          assertThat(service.checkStaffMatch(1234)!!.differences).containsExactly(
            entry("emails", "nomis=[], dps=[john.smith@justice.gov.uk]"),
          )
        }

        @Test
        fun `will report a mismatch when no emailAddresses in Dps`() = runTest {
          stubStaffReconciliation(nomisStaffDetails(), dpsStaffDetails().copy(emails = listOf()))
          assertThat(service.checkStaffMatch(1234)!!.differences).containsExactly(
            entry("emails", "nomis=[john.smith@justice.gov.uk], dps=[]"),
          )
        }

        @Test
        fun `will report a missing mismatch when emailAddresses missing from Nomis`() = runTest {
          val dpsEmail = dpsStaffDetails().emails[0].copy(email = "fred@email.com")
          stubStaffReconciliation(
            nomisStaffDetails(),
            dpsStaffDetails().copy(emails = listOf(dpsStaffDetails().emails[0], dpsEmail)),
          )
          assertThat(service.checkStaffMatch(1234)!!.differences).containsExactly(
            entry("emails", "nomis=[john.smith@justice.gov.uk], dps=[john.smith@justice.gov.uk, fred@email.com]"),
          )
        }

        @Test
        fun `will report a missing mismatch when emailAddresses missing from Dps`() = runTest {
          val nomisEmail = nomisStaffDetails().emailAddresses[0].copy(email = "fred@email.com")
          stubStaffReconciliation(
            nomisStaffDetails().copy(emailAddresses = listOf(nomisStaffDetails().emailAddresses[0], nomisEmail)),
            dpsStaffDetails(),
          )
          assertThat(service.checkStaffMatch(1234)!!.differences).containsExactly(
            entry("emails", "nomis=[john.smith@justice.gov.uk, fred@email.com], dps=[john.smith@justice.gov.uk]"),
          )
        }

        @Test
        fun `will report multiple mismatches for same staff`() = runTest {
          stubStaffReconciliation(nomisStaffDetails().copy(firstName = "FRED", lastName = "WHITE"), dpsStaffDetails())
          assertThat(service.checkStaffMatch(1234)?.differences).containsExactly(
            entry("firstName", "nomis=FRED, dps=JOHN"),
            entry("lastName", "nomis=WHITE, dps=SMITH"),
          )
        }
      }
    }

    @Nested
    inner class CheckStaffUserAccount {

      @Nested
      inner class Match {
        @Test
        fun `will not report a mismatch when user account status values match`() = runTest {
          stubStaffReconciliation(
            nomisStaffDetails().copy(accounts = listOf(staffAccount().copy(status = "EXPIRED & LOCKED"))),
            dpsStaffDetails().copy(accounts = listOf(prisonUserAccount().copy(accountStatus = PrisonUserAccount.AccountStatus.EXPIRED_LOCKED))),
          )
          assertThat(service.checkStaffMatch(1234)).isNull()
        }
      }

      @Nested
      inner class Mismatch {
        @Test
        fun `will report a different number of staff user accounts`() = runTest {
          val nomisStaffAccount = staffAccount().copy(typeCode = "GENERAL")
          stubStaffReconciliation(
            nomisStaffDetails().copy(accounts = listOf(staffAccount(), nomisStaffAccount)),
            dpsStaffDetails(),
          )
          assertThat(service.checkStaffMatch(1234)!!.differences).containsExactly(
            entry("accounts.size", "nomis=2, dps=1"),
          )
        }

        @Test
        fun `will report a mismatch when staff user account usernames don't match`() = runTest {
          val nomisStaffAccount = staffAccount().copy(username = "JSMITH_ADM")
          stubStaffReconciliation(nomisStaffDetails().copy(accounts = listOf(nomisStaffAccount)), dpsStaffDetails())
          assertThat(service.checkStaffMatch(1234)!!.differences).containsExactly(
            entry("accounts[0].username", "nomis=JSMITH_ADM, dps=JOHNSMITH_ADM"),
          )
        }

        @Test
        fun `will report a mismatch when staff user account STATUS don't match`() = runTest {
          val nomisStaffAccount = staffAccount().copy(status = "EXPIRED & LOCKED")
          stubStaffReconciliation(nomisStaffDetails().copy(accounts = listOf(nomisStaffAccount)), dpsStaffDetails())
          assertThat(service.checkStaffMatch(1234)!!.differences).containsExactly(
            entry("accounts[0].status", "nomis=EXPIRED & LOCKED, dps=OPEN"),
          )
        }

        @Test
        fun `will report a mismatch when staff user account types don't match`() = runTest {
          val nomisStaffAccount = staffAccount().copy(typeCode = "GENERAL")
          stubStaffReconciliation(nomisStaffDetails().copy(accounts = listOf(nomisStaffAccount)), dpsStaffDetails())
          assertThat(service.checkStaffMatch(1234)!!.differences).containsExactly(
            entry("accounts[0].typeCode", "nomis=GENERAL, dps=ADMIN"),
          )
        }

        @Test
        fun `will report a mismatch when staff user account caseloads don't match`() = runTest {
          val nomisStaffAccount = staffAccount().copy(
            caseloads = listOf(CaseloadResponse(caseloadId = "LEI", roles = emptyList(), audit = audit())),
          )
          stubStaffReconciliation(nomisStaffDetails().copy(accounts = listOf(nomisStaffAccount)), dpsStaffDetails())
          assertThat(service.checkStaffMatch(1234)!!.differences).containsExactly(
            entry("accounts[0].caseloads", "nomis=[LEI], dps=[LEI, MDI, NWEB]"),
          )
        }

        @Test
        fun `will report a mismatch when staff user account dpsRoles don't match`() = runTest {
          val nwebCaseload = staffAccount().caseloads[2].copy(
            roles = listOf(RoleResponse(code = "DPS_CODE_3", name = "Dps Role 3", audit = audit())),
          )
          val nomisStaffAccount = staffAccount().copy(
            caseloads = listOf(staffAccount().caseloads[0], staffAccount().caseloads[1], nwebCaseload),
          )
          stubStaffReconciliation(nomisStaffDetails().copy(accounts = listOf(nomisStaffAccount)), dpsStaffDetails())
          assertThat(service.checkStaffMatch(1234)!!.differences).containsExactly(
            entry("accounts[0].dpsRoles", "nomis=[DPS_CODE_3], dps=[DPS_CODE_1, DPS_CODE_2]"),
          )
        }

        @Test
        fun `will report a mismatch when staff user account lastloggedIn values don't match`() = runTest {
          val nomisStaffAccount = staffAccount().copy(
            lastLoggedIn = LocalDateTime.parse("2026-03-17T11:30:00"),
          )
          stubStaffReconciliation(nomisStaffDetails().copy(accounts = listOf(nomisStaffAccount)), dpsStaffDetails())
          assertThat(service.checkStaffMatch(1234)!!.differences).containsExactly(
            entry("accounts[0].lastLoggedIn", "nomis=2026-03-17T11:30, dps=2026-03-17T12:30"),
          )
        }

        @Test
        fun `will report a mismatch when staff user account activeCaseloads don't match`() = runTest {
          val nomisStaffAccount = staffAccount().copy(activeCaseloadId = "LEI")
          stubStaffReconciliation(nomisStaffDetails().copy(accounts = listOf(nomisStaffAccount)), dpsStaffDetails())
          assertThat(service.checkStaffMatch(1234)!!.differences).containsExactly(
            entry("accounts[0].activeCaseloadId", "nomis=LEI, dps=MDI"),
          )
        }
      }
    }
  }
}
