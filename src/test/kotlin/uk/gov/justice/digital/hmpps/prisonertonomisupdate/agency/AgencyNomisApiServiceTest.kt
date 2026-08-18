package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyConfiguration
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension

@ExtendWith(NomisApiExtension::class)
@SpringAPIServiceTest
@Import(AgencyNomisApiService::class, AgencyConfiguration::class, AgencyNomisApiMockServer::class, RetryApiService::class)
class AgencyNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: AgencyNomisApiService

  @Autowired
  private lateinit var mockServer: AgencyNomisApiMockServer

  @Nested
  inner class GetAgency {
    @Test
    internal fun `will pass oauth2 token to endpoint`() = runTest {
      mockServer.stubGetAgency(
        agencyId = "SHEFCC",
      )

      apiService.getAgency(
        agencyId = "SHEFCC",
      )

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get agency endpoint`() = runTest {
      mockServer.stubGetAgency(
        agencyId = "SHEFCC",
      )

      apiService.getAgency(
        agencyId = "SHEFCC",
      )
      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/agency/SHEFCC")),
      )
    }

    @Test
    fun `will return agency from the endpoint`() = runTest {
      mockServer.stubGetAgency(
        agencyId = "SHEFCC",
      )

      assertThat(apiService.getAgency("SHEFCC")).isNotNull
    }

    @Test
    fun `will return null from the endpoint when not found`() = runTest {
      mockServer.stubGetAgency("SHEFCC", errorHttpStatus = HttpStatus.NOT_FOUND)

      assertThat(apiService.getAgency("SHEFCC")).isNull()
    }
  }

  @Nested
  inner class GetAgencyIds {
    @Test
    internal fun `will pass oauth2 token to endpoint`() = runTest {
      mockServer.stubGetAgencyIds()

      apiService.getAgencyIds()

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get agency ids endpoint but exclude prisons`() = runTest {
      mockServer.stubGetAgencyIds()

      apiService.getAgencyIds()

      mockServer.verify(
        getRequestedFor(urlEqualTo("/agency/ids/all?excludeType=INST")),
      )
    }
  }
}
