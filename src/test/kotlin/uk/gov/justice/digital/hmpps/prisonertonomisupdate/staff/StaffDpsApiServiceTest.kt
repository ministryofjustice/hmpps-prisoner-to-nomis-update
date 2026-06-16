package uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.staff.StaffDpsApiExtension.Companion.dpsStaffServer

@SpringAPIServiceTest
@Import(StaffDpsApiService::class, StaffConfiguration::class, RetryApiService::class)
class StaffDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: StaffDpsApiService

  @Nested
  inner class GetStaff {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsStaffServer.stubGetStaff()

      apiService.getStaffOrNull(staffId = 4321)

      dpsStaffServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get endpoint`() = runTest {
      dpsStaffServer.stubGetStaff()

      apiService.getStaffOrNull(staffId = 4321)

      dpsStaffServer.verify(
        getRequestedFor(urlPathEqualTo("/prison-users/4321")),
      )
    }

    @Test
    fun `will return null when not found`() = runTest {
      dpsStaffServer.stubGetStaff(response = null)

      assertThat(apiService.getStaffOrNull(staffId = 4321)).isNull()
    }

    @Test
    fun `will return staff when found`() = runTest {
      dpsStaffServer.stubGetStaff()

      assertThat(apiService.getStaffOrNull(staffId = 4321)).isNotNull()
    }
  }
}
