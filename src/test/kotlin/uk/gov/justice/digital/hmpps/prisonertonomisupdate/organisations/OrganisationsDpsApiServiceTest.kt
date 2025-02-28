package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsDpsApiExtension.Companion.dpsOrganisationsServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.model.SyncOrganisationId
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

  @Nested
  inner class GetOrganisationIds {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      dpsOrganisationsServer.stubGetOrganisationIds()

      apiService.getOrganisationIds()

      dpsOrganisationsServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass mandatory parameters to service`() = runTest {
      dpsOrganisationsServer.stubGetOrganisationIds()

      apiService.getOrganisationIds(pageNumber = 12, pageSize = 20)

      dpsOrganisationsServer.verify(
        getRequestedFor(anyUrl())
          .withQueryParam("page", equalTo("12"))
          .withQueryParam("size", equalTo("20")),
      )
    }

    @Test
    fun `will return corporate ids`() = runTest {
      dpsOrganisationsServer.stubGetOrganisationIds(
        content = listOf(
          SyncOrganisationId(1234567),
          SyncOrganisationId(1234568),
        ),
      )

      val pages = apiService.getOrganisationIds(pageNumber = 12, pageSize = 20)

      val content = pages.content!!
      assertThat(content).hasSize(2)
      assertThat(content[0].organisationId).isEqualTo(1234567)
      assertThat(content[1].organisationId).isEqualTo(1234568)
    }
  }
}
