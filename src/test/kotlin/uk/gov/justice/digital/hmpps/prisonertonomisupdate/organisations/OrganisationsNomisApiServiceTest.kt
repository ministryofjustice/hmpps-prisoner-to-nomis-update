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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CorporateOrganisationIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(
  OrganisationsNomisApiService::class,
  OrganisationsNomisApiMockServer::class,
  RetryApiService::class,
)
class OrganisationsNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: OrganisationsNomisApiService

  @Autowired
  private lateinit var mockServer: OrganisationsNomisApiMockServer

  @Nested
  inner class GetCorporateOrganisation {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetCorporateOrganisation(corporateId = 1234567)

      apiService.getCorporateOrganisation(nomisCorporateId = 1234567)

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass NOMIS id to service`() = runTest {
      mockServer.stubGetCorporateOrganisation(corporateId = 1234567)

      apiService.getCorporateOrganisation(nomisCorporateId = 1234567)

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/corporates/1234567")),
      )
    }

    @Test
    fun `will return corporate`() = runTest {
      mockServer.stubGetCorporateOrganisation(
        corporateId = 1234567,
        corporate = corporateOrganisation().copy(name = "Police"),
      )

      val corporate = apiService.getCorporateOrganisation(nomisCorporateId = 1234567)

      assertThat(corporate.name).isEqualTo("Police")
    }
  }

  @Nested
  inner class GetCorporateOrganisationIds {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetCorporateOrganisationIds()

      apiService.getCorporateOrganisationIds()

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will pass mandatory parameters to service`() = runTest {
      mockServer.stubGetCorporateOrganisationIds()

      apiService.getCorporateOrganisationIds(pageNumber = 12, pageSize = 20)

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withQueryParam("page", equalTo("12"))
          .withQueryParam("size", equalTo("20")),
      )
    }

    @Test
    fun `will return corporate ids`() = runTest {
      mockServer.stubGetCorporateOrganisationIds(
        content = listOf(
          CorporateOrganisationIdResponse(1234567),
          CorporateOrganisationIdResponse(1234568),
        ),
      )

      val pages = apiService.getCorporateOrganisationIds(pageNumber = 12, pageSize = 20)

      assertThat(pages.content).hasSize(2)
      assertThat(pages.content[0].corporateId).isEqualTo(1234567)
      assertThat(pages.content[1].corporateId).isEqualTo(1234568)
    }
  }
}
