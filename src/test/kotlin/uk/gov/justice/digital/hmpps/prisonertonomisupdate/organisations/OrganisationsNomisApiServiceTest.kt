package uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomisprisoner.model.CorporateOrganisationIdResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsNomisApiMockServer.Companion.createCorporateAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsNomisApiMockServer.Companion.createCorporateAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsNomisApiMockServer.Companion.createCorporateEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsNomisApiMockServer.Companion.createCorporateEmailResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsNomisApiMockServer.Companion.createCorporatePhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsNomisApiMockServer.Companion.createCorporatePhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsNomisApiMockServer.Companion.createCorporateRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsNomisApiMockServer.Companion.createCorporateWebAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsNomisApiMockServer.Companion.createCorporateWebAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsNomisApiMockServer.Companion.updateCorporateAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsNomisApiMockServer.Companion.updateCorporateEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsNomisApiMockServer.Companion.updateCorporatePhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsNomisApiMockServer.Companion.updateCorporateRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsNomisApiMockServer.Companion.updateCorporateTypesRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.organisations.OrganisationsNomisApiMockServer.Companion.updateCorporateWebAddressRequest
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

  @Nested
  inner class Corporate {

    @Nested
    inner class CreateCorporate {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubCreateCorporate()

        apiService.createCorporate(request = createCorporateRequest())

        mockServer.verify(
          postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call create endpoint`() = runTest {
        mockServer.stubCreateCorporate()

        apiService.createCorporate(request = createCorporateRequest())

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/corporates")),
        )
      }
    }

    @Nested
    inner class DeleteCorporate {
      private val corporateId = 17171L

      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubDeleteCorporate(corporateId)

        apiService.deleteCorporate(corporateId)

        mockServer.verify(
          deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call delete endpoint`() = runTest {
        mockServer.stubDeleteCorporate(corporateId)

        apiService.deleteCorporate(corporateId)

        mockServer.verify(
          deleteRequestedFor(urlPathEqualTo("/corporates/$corporateId")),
        )
      }
    }

    @Nested
    inner class UpdateCorporate {
      private val corporateId = 17171L

      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubUpdateCorporate(corporateId)

        apiService.updateCorporate(corporateId, updateCorporateRequest())

        mockServer.verify(
          putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call update endpoint`() = runTest {
        mockServer.stubUpdateCorporate(corporateId)

        apiService.updateCorporate(corporateId, updateCorporateRequest())

        mockServer.verify(
          putRequestedFor(urlPathEqualTo("/corporates/$corporateId")),
        )
      }
    }
  }

  @Nested
  inner class CorporateTypes {

    @Nested
    inner class UpdateCorporateTypes {
      private val corporateId = 17171L

      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubUpdateCorporateTypes(corporateId)

        apiService.updateCorporateTypes(corporateId, updateCorporateTypesRequest())

        mockServer.verify(
          putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call update endpoint`() = runTest {
        mockServer.stubUpdateCorporateTypes(corporateId)

        apiService.updateCorporateTypes(corporateId, updateCorporateTypesRequest())

        mockServer.verify(
          putRequestedFor(urlPathEqualTo("/corporates/$corporateId/type")),
        )
      }
    }
  }

  @Nested
  inner class WebAddresses {
    @Nested
    inner class CreateCorporateWebAddress {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubCreateCorporateWebAddress(corporateId = 1234567)

        apiService.createCorporateWebAddress(corporateId = 1234567, request = createCorporateWebAddressRequest())

        mockServer.verify(
          postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call create endpoint`() = runTest {
        mockServer.stubCreateCorporateWebAddress(corporateId = 1234567)

        apiService.createCorporateWebAddress(corporateId = 1234567, request = createCorporateWebAddressRequest())

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/corporates/1234567/web-address")),
        )
      }

      @Test
      fun `will return webAddress id `() = runTest {
        mockServer.stubCreateCorporateWebAddress(corporateId = 1234567, response = createCorporateWebAddressResponse().copy(id = 123456))

        val response = apiService.createCorporateWebAddress(corporateId = 1234567, request = createCorporateWebAddressRequest())

        assertThat(response.id).isEqualTo(123456)
      }
    }

    @Nested
    inner class UpdateCorporateWebAddress {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubUpdateCorporateWebAddress(corporateId = 1234567, webAddressId = 7655)

        apiService.updateCorporateWebAddress(corporateId = 1234567, webAddressId = 7655, request = updateCorporateWebAddressRequest())

        mockServer.verify(
          putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call update endpoint`() = runTest {
        mockServer.stubUpdateCorporateWebAddress(corporateId = 1234567, webAddressId = 7655)

        apiService.updateCorporateWebAddress(corporateId = 1234567, webAddressId = 7655, request = updateCorporateWebAddressRequest())

        mockServer.verify(
          putRequestedFor(urlPathEqualTo("/corporates/1234567/web-address/7655")),
        )
      }
    }

    @Nested
    inner class DeleteCorporateWebAddress {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubDeleteCorporateWebAddress(corporateId = 1234567, webAddressId = 7655)

        apiService.deleteCorporateWebAddress(corporateId = 1234567, webAddressId = 7655)

        mockServer.verify(
          deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call delete endpoint`() = runTest {
        mockServer.stubDeleteCorporateWebAddress(corporateId = 1234567, webAddressId = 7655)

        apiService.deleteCorporateWebAddress(corporateId = 1234567, webAddressId = 7655)

        mockServer.verify(
          deleteRequestedFor(urlPathEqualTo("/corporates/1234567/web-address/7655")),
        )
      }
    }
  }

  @Nested
  inner class Phones {

    @Nested
    inner class CreateCorporatePhone {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubCreateCorporatePhone(corporateId = 1234567)

        apiService.createCorporatePhone(corporateId = 1234567, request = createCorporatePhoneRequest())

        mockServer.verify(
          postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call create endpoint`() = runTest {
        mockServer.stubCreateCorporatePhone(corporateId = 1234567)

        apiService.createCorporatePhone(corporateId = 1234567, request = createCorporatePhoneRequest())

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/corporates/1234567/phone")),
        )
      }

      @Test
      fun `will return phone id `() = runTest {
        mockServer.stubCreateCorporatePhone(corporateId = 1234567, response = createCorporatePhoneResponse().copy(id = 123456))

        val response = apiService.createCorporatePhone(corporateId = 1234567, request = createCorporatePhoneRequest())

        assertThat(response.id).isEqualTo(123456)
      }
    }

    @Nested
    inner class UpdateCorporatePhone {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubUpdateCorporatePhone(corporateId = 1234567, phoneId = 65543)

        apiService.updateCorporatePhone(corporateId = 1234567, phoneId = 65543, updateCorporatePhoneRequest())

        mockServer.verify(
          putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call update endpoint`() = runTest {
        mockServer.stubUpdateCorporatePhone(corporateId = 1234567, phoneId = 65543)

        apiService.updateCorporatePhone(corporateId = 1234567, phoneId = 65543, updateCorporatePhoneRequest())

        mockServer.verify(
          putRequestedFor(urlPathEqualTo("/corporates/1234567/phone/65543")),
        )
      }
    }

    @Nested
    inner class DeleteCorporatePhone {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubDeleteCorporatePhone(corporateId = 1234567, phoneId = 65543)

        apiService.deleteCorporatePhone(corporateId = 1234567, phoneId = 65543)

        mockServer.verify(
          deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call delete endpoint`() = runTest {
        mockServer.stubDeleteCorporatePhone(corporateId = 1234567, phoneId = 65543)

        apiService.deleteCorporatePhone(corporateId = 1234567, phoneId = 65543)

        mockServer.verify(
          deleteRequestedFor(urlPathEqualTo("/corporates/1234567/phone/65543")),
        )
      }
    }
  }

  @Nested
  inner class Emails {
    @Nested
    inner class CreateCorporateEmail {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubCreateCorporateEmail(corporateId = 1234567)

        apiService.createCorporateEmail(corporateId = 1234567, request = createCorporateEmailRequest())

        mockServer.verify(
          postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call create endpoint`() = runTest {
        mockServer.stubCreateCorporateEmail(corporateId = 1234567)

        apiService.createCorporateEmail(corporateId = 1234567, request = createCorporateEmailRequest())

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/corporates/1234567/email")),
        )
      }

      @Test
      fun `will return email id `() = runTest {
        mockServer.stubCreateCorporateEmail(corporateId = 1234567, response = createCorporateEmailResponse().copy(id = 123456))

        val response = apiService.createCorporateEmail(corporateId = 1234567, request = createCorporateEmailRequest())

        assertThat(response.id).isEqualTo(123456)
      }
    }

    @Nested
    inner class UpdateCorporateEmail {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubUpdateCorporateEmail(corporateId = 1234567, emailId = 7655)

        apiService.updateCorporateEmail(corporateId = 1234567, emailId = 7655, request = updateCorporateEmailRequest())

        mockServer.verify(
          putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call update endpoint`() = runTest {
        mockServer.stubUpdateCorporateEmail(corporateId = 1234567, emailId = 7655)

        apiService.updateCorporateEmail(corporateId = 1234567, emailId = 7655, request = updateCorporateEmailRequest())

        mockServer.verify(
          putRequestedFor(urlPathEqualTo("/corporates/1234567/email/7655")),
        )
      }
    }

    @Nested
    inner class DeleteCorporateEmail {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubDeleteCorporateEmail(corporateId = 1234567, emailId = 7655)

        apiService.deleteCorporateEmail(corporateId = 1234567, emailId = 7655)

        mockServer.verify(
          deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call delete endpoint`() = runTest {
        mockServer.stubDeleteCorporateEmail(corporateId = 1234567, emailId = 7655)

        apiService.deleteCorporateEmail(corporateId = 1234567, emailId = 7655)

        mockServer.verify(
          deleteRequestedFor(urlPathEqualTo("/corporates/1234567/email/7655")),
        )
      }
    }
  }

  @Nested
  inner class Addresses {
    @Nested
    inner class CreateCorporateAddress {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubCreateCorporateAddress(corporateId = 1234567)

        apiService.createCorporateAddress(corporateId = 1234567, request = createCorporateAddressRequest())

        mockServer.verify(
          postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call create endpoint`() = runTest {
        mockServer.stubCreateCorporateAddress(corporateId = 1234567)

        apiService.createCorporateAddress(corporateId = 1234567, request = createCorporateAddressRequest())

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/corporates/1234567/address")),
        )
      }

      @Test
      fun `will return address id `() = runTest {
        mockServer.stubCreateCorporateAddress(corporateId = 1234567, response = createCorporateAddressResponse().copy(id = 123456))

        val response = apiService.createCorporateAddress(corporateId = 1234567, request = createCorporateAddressRequest())

        assertThat(response.id).isEqualTo(123456)
      }
    }

    @Nested
    inner class UpdateCorporateAddress {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubUpdateCorporateAddress(corporateId = 1234567, addressId = 654321)

        apiService.updateCorporateAddress(corporateId = 1234567, addressId = 654321, request = updateCorporateAddressRequest())

        mockServer.verify(
          putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call update endpoint`() = runTest {
        mockServer.stubUpdateCorporateAddress(corporateId = 1234567, addressId = 654321)

        apiService.updateCorporateAddress(corporateId = 1234567, addressId = 654321, request = updateCorporateAddressRequest())

        mockServer.verify(
          putRequestedFor(urlPathEqualTo("/corporates/1234567/address/654321")),
        )
      }
    }

    @Nested
    inner class DeleteCorporateAddress {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubDeleteCorporateAddress(corporateId = 1234567, addressId = 654321)

        apiService.deleteCorporateAddress(corporateId = 1234567, addressId = 654321)

        mockServer.verify(
          deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call delete endpoint`() = runTest {
        mockServer.stubDeleteCorporateAddress(corporateId = 1234567, addressId = 654321)

        apiService.deleteCorporateAddress(corporateId = 1234567, addressId = 654321)

        mockServer.verify(
          deleteRequestedFor(urlPathEqualTo("/corporates/1234567/address/654321")),
        )
      }
    }
  }

  @Nested
  inner class AddressPhones {

    @Nested
    inner class CreateCorporateAddressPhone {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubCreateCorporateAddressPhone(corporateId = 1234567, addressId = 67890)

        apiService.createCorporateAddressPhone(corporateId = 1234567, addressId = 67890, request = createCorporatePhoneRequest())

        mockServer.verify(
          postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call create endpoint`() = runTest {
        mockServer.stubCreateCorporateAddressPhone(corporateId = 1234567, addressId = 67890)

        apiService.createCorporateAddressPhone(corporateId = 1234567, addressId = 67890, request = createCorporatePhoneRequest())

        mockServer.verify(
          postRequestedFor(urlPathEqualTo("/corporates/1234567/address/67890/phone")),
        )
      }

      @Test
      fun `will return phone id `() = runTest {
        mockServer.stubCreateCorporateAddressPhone(corporateId = 1234567, addressId = 67890, response = createCorporatePhoneResponse().copy(id = 123456))

        val response = apiService.createCorporateAddressPhone(corporateId = 1234567, addressId = 67890, request = createCorporatePhoneRequest())

        assertThat(response.id).isEqualTo(123456)
      }
    }

    @Nested
    inner class UpdateCorporateAddressPhone {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubUpdateCorporateAddressPhone(corporateId = 1234567, addressId = 67890, phoneId = 8755)

        apiService.updateCorporateAddressPhone(corporateId = 1234567, addressId = 67890, phoneId = 8755, request = updateCorporatePhoneRequest())

        mockServer.verify(
          putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call update endpoint`() = runTest {
        mockServer.stubUpdateCorporateAddressPhone(corporateId = 1234567, addressId = 67890, phoneId = 8755)

        apiService.updateCorporateAddressPhone(corporateId = 1234567, addressId = 67890, phoneId = 8755, request = updateCorporatePhoneRequest())

        mockServer.verify(
          putRequestedFor(urlPathEqualTo("/corporates/1234567/address/67890/phone/8755")),
        )
      }
    }

    @Nested
    inner class DeleteCorporateAddressPhone {
      @Test
      fun `will pass oath2 token to service`() = runTest {
        mockServer.stubDeleteCorporateAddressPhone(corporateId = 1234567, phoneId = 8755)

        apiService.deleteCorporateAddressPhone(corporateId = 1234567, phoneId = 8755)

        mockServer.verify(
          deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
        )
      }

      @Test
      fun `will call delete endpoint`() = runTest {
        mockServer.stubDeleteCorporateAddressPhone(corporateId = 1234567, phoneId = 8755)

        apiService.deleteCorporateAddressPhone(corporateId = 1234567, phoneId = 8755)

        mockServer.verify(
          deleteRequestedFor(urlPathEqualTo("/corporates/1234567/address/phone/8755")),
        )
      }
    }
  }
}
