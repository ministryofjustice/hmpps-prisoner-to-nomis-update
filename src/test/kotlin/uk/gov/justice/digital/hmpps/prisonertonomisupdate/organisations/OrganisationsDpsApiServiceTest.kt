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

  @Nested
  inner class GetSyncOrganisation {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOrganisationsServer.stubGetSyncOrganisation(organisationId = 1234567)

      apiService.getSyncOrganisation(organisationId = 1234567)

      dpsOrganisationsServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get sync endpoint`() = runTest {
      dpsOrganisationsServer.stubGetSyncOrganisation(organisationId = 1234567)

      apiService.getSyncOrganisation(organisationId = 1234567)

      dpsOrganisationsServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/organisation/1234567")),
      )
    }
  }

  @Nested
  inner class GetSyncOrganisationWeb {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOrganisationsServer.stubGetSyncOrganisationWeb(organisationWebId = 1234567)

      apiService.getSyncOrganisationWeb(organisationWebId = 1234567)

      dpsOrganisationsServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get sync endpoint`() = runTest {
      dpsOrganisationsServer.stubGetSyncOrganisationWeb(organisationWebId = 1234567)

      apiService.getSyncOrganisationWeb(organisationWebId = 1234567)

      dpsOrganisationsServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/organisation-web/1234567")),
      )
    }
  }

  @Nested
  inner class GetSyncOrganisationTypes {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOrganisationsServer.stubGetSyncOrganisationTypes(organisationId = 1234567)

      apiService.getSyncOrganisationTypes(organisationId = 1234567)

      dpsOrganisationsServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get sync endpoint`() = runTest {
      dpsOrganisationsServer.stubGetSyncOrganisationTypes(organisationId = 1234567)

      apiService.getSyncOrganisationTypes(organisationId = 1234567)

      dpsOrganisationsServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/organisation-types/1234567")),
      )
    }
  }

  @Nested
  inner class GetSyncOrganisationAddress {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOrganisationsServer.stubGetSyncOrganisationAddress(organisationAddressId = 1234567)

      apiService.getSyncOrganisationAddress(organisationAddressId = 1234567)

      dpsOrganisationsServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get sync endpoint`() = runTest {
      dpsOrganisationsServer.stubGetSyncOrganisationAddress(organisationAddressId = 1234567)

      apiService.getSyncOrganisationAddress(organisationAddressId = 1234567)

      dpsOrganisationsServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/organisation-address/1234567")),
      )
    }
  }

  @Nested
  inner class GetSyncOrganisationEmail {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOrganisationsServer.stubGetSyncOrganisationEmail(organisationEmailId = 1234567)

      apiService.getSyncOrganisationEmail(organisationEmailId = 1234567)

      dpsOrganisationsServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get sync endpoint`() = runTest {
      dpsOrganisationsServer.stubGetSyncOrganisationEmail(organisationEmailId = 1234567)

      apiService.getSyncOrganisationEmail(organisationEmailId = 1234567)

      dpsOrganisationsServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/organisation-email/1234567")),
      )
    }
  }

  @Nested
  inner class GetSyncOrganisationPhone {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOrganisationsServer.stubGetSyncOrganisationPhone(organisationPhoneId = 1234567)

      apiService.getSyncOrganisationPhone(organisationPhoneId = 1234567)

      dpsOrganisationsServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get sync endpoint`() = runTest {
      dpsOrganisationsServer.stubGetSyncOrganisationPhone(organisationPhoneId = 1234567)

      apiService.getSyncOrganisationPhone(organisationPhoneId = 1234567)

      dpsOrganisationsServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/organisation-phone/1234567")),
      )
    }
  }

  @Nested
  inner class GetSyncOrganisationAddressPhone {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsOrganisationsServer.stubGetSyncOrganisationAddressPhone(organisationAddressPhoneId = 1234567)

      apiService.getSyncOrganisationAddressPhone(organisationAddressPhoneId = 1234567)

      dpsOrganisationsServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get sync endpoint`() = runTest {
      dpsOrganisationsServer.stubGetSyncOrganisationAddressPhone(organisationAddressPhoneId = 1234567)

      apiService.getSyncOrganisationAddressPhone(organisationAddressPhoneId = 1234567)

      dpsOrganisationsServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/organisation-address-phone/1234567")),
      )
    }
  }
}
