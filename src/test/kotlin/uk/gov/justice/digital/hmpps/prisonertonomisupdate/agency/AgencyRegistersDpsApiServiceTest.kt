package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyRegistersDpsApiExtension.Companion.agencyRegistersApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@ExtendWith(AgencyRegistersDpsApiExtension::class)
@SpringAPIServiceTest
@Import(AgencyRegistersDpsApiService::class, AgencyRegistersConfiguration::class, RetryApiService::class)
class AgencyRegistersDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: AgencyRegistersDpsApiService

  @Nested
  inner class GetAgency {
    @Test
    internal fun `will pass oauth2 token to endpoint`() = runTest {
      agencyRegistersApi.stubGetAgency("SHEFCC")

      apiService.getAgency("SHEFCC")

      agencyRegistersApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the endpoint`() = runTest {
      agencyRegistersApi.stubGetAgency("SHEFCC")

      apiService.getAgency("SHEFCC")

      agencyRegistersApi.verify(
        getRequestedFor(urlPathEqualTo("/legacy/SHEFCC")),
      )
    }

    @Test
    fun `will return agency from the endpoint`() = runTest {
      agencyRegistersApi.stubGetAgency("SHEFCC")

      assertThat(apiService.getAgency("SHEFCC")).isNotNull
    }

    @Test
    fun `will return null from the endpoint when not found`() = runTest {
      agencyRegistersApi.stubGetAgency("SHEFCC", errorHttpStatus = HttpStatus.NOT_FOUND)

      assertThat(apiService.getAgency("SHEFCC")).isNull()
    }
  }

  @Nested
  inner class GetAgencyForReconciliation {
    @Test
    fun `will call the endpoint`() = runTest {
      agencyRegistersApi.stubGetAgencyForReconciliation("SHEFCC")

      apiService.getAgencyForReconciliation("SHEFCC")

      agencyRegistersApi.verify(
        getRequestedFor(urlPathEqualTo("/legacy/reconciliation/SHEFCC")),
      )
    }

    @Test
    fun `will return agency from the endpoint`() = runTest {
      agencyRegistersApi.stubGetAgencyForReconciliation("SHEFCC")

      assertThat(apiService.getAgencyForReconciliation("SHEFCC")).isNotNull
    }

    @Test
    fun `will return null from the endpoint when not found`() = runTest {
      agencyRegistersApi.stubGetAgencyForReconciliation("SHEFCC", errorHttpStatus = HttpStatus.NOT_FOUND)

      assertThat(apiService.getAgencyForReconciliation("SHEFCC")).isNull()
    }
  }

  @Nested
  inner class GetAgencyIds {
    @Test
    internal fun `will pass oauth2 token to endpoint`() = runTest {
      agencyRegistersApi.stubGetAgencyIds()

      apiService.getAgencyIds()

      agencyRegistersApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the endpoint`() = runTest {
      agencyRegistersApi.stubGetAgencyIds()

      apiService.getAgencyIds()

      agencyRegistersApi.verify(
        getRequestedFor(urlPathEqualTo("/legacy/reconciliation/ids/all")),
      )
    }
  }

  @Nested
  inner class GetPrisonIds {
    @Test
    internal fun `will pass oauth2 token to endpoint`() = runTest {
      agencyRegistersApi.stubGetPrisonIds()

      apiService.getPrisonIds()

      agencyRegistersApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", matching(".+")),
      )
    }

    @Test
    fun `will call the get prison ids endpoint`() = runTest {
      agencyRegistersApi.stubGetPrisonIds()

      apiService.getPrisonIds()

      agencyRegistersApi.verify(
        getRequestedFor(urlPathEqualTo("/legacy/reconciliation/prison/ids/all")),
      )
    }

    @Test
    fun `will return prison ids from the endpoint`() = runTest {
      val expected = AgencyRegistersDpsApiExtension.agencyIdsResponse().copy(
        agencyIds = listOf(
          AgencyRegistersDpsApiExtension.agencyId().copy(agencyId = "MDI"),
          AgencyRegistersDpsApiExtension.agencyId().copy(agencyId = "LEI"),
        ),
      )
      agencyRegistersApi.stubGetPrisonIds(response = expected)

      assertThat(apiService.getPrisonIds()).isEqualTo(expected)
    }
  }
}
