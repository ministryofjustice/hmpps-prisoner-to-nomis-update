package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension.Companion.dpsOrganisationsServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(
  OrganisationsDpsApiService::class,
  OrganisationsConfiguration::class,
  OrganisationsDpsApiMockServer::class,
  RetryApiService::class,
)
class OrganisationsDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: OrganisationsDpsApiService

  @Nested
  inner class GetOrganisation {
    @Test
    internal fun `will pass oath2 token to organisation endpoint`() = runTest {
      dpsOrganisationsServer.stubGetOrganisation(12345)

      apiService.getOrganisation(12345)

      dpsOrganisationsServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the GET endpoint`() = runTest {
      dpsOrganisationsServer.stubGetOrganisation(12345)

      apiService.getOrganisation(12345)

      dpsOrganisationsServer.verify(
        getRequestedFor(urlPathEqualTo("/organisation/12345")),
      )
    }
  }
}
