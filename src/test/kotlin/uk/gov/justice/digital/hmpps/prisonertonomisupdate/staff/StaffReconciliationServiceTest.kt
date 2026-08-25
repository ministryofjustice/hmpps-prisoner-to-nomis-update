package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.StaffDetails
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.NomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.PrisonUserAccount
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.model.PrisonUserReconciliationResponse

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

    @Test
    fun `will not report a mismatch when no differences found`() = runTest {
      stubStaffReconciliation(nomisStaffDetails(), dpsStaffDetails())
      assertThat(service.checkStaffMatch(1234)).isNull()
    }

    @Test
    fun `will not report a mismatch when status values are both set to an inactive value`() = runTest {
      stubStaffReconciliation(nomisStaffDetails().copy(status = "SICK"), dpsStaffDetails().copy(status = PrisonUserReconciliationResponse.Status.INACTIVE))
      assertThat(service.checkStaffMatch(1234)).isNull()
    }

    @Test
    fun `will not report a mismatch when user account status values match`() = runTest {
      stubStaffReconciliation(
        nomisStaffDetails().copy(accounts = listOf(staffAccount().copy(status = "EXPIRED & LOCKED"))),
        dpsStaffDetails().copy(accounts = listOf(prisonUserAccount().copy(accountStatus = PrisonUserAccount.AccountStatus.EXPIRED_LOCKED))),
      )
      assertThat(service.checkStaffMatch(1234)).isNull()
    }

    @Test
    fun `will report a mismatch when first names don't match`() = runTest {
      stubStaffReconciliation(nomisStaffDetails().copy(firstName = "FRED"), dpsStaffDetails())
      assertThat(service.checkStaffMatch(1234)?.reason).isEqualTo("different-staff-details")
    }
  }
}
