package uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.createAgencyAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.createAgencyAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.createAgencyEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.createAgencyEmailResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.createAgencyPhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.createAgencyPhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.updateAgencyEmailAddressesRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.updateAgencyPhoneNumbersRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.agency.AgencyNomisApiMockServer.Companion.updateAgencyPhoneNumbersResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.NomisApiExtension

@ExtendWith(NomisApiExtension::class)
@SpringAPIServiceTest
@Import(AgencyNomisApiService::class, AgencyRegistersConfiguration::class, AgencyNomisApiMockServer::class, RetryApiService::class)
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

  @Nested
  inner class CreateAgencyEmail {
    @Test
    fun `will pass oauth2 token to endpoint`() = runTest {
      mockServer.stubCreateAgencyEmail()

      apiService.createAgencyEmail("SHEFCC", createAgencyEmailRequest())

      mockServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the create agency email endpoint with request`() = runTest {
      val request = createAgencyEmailRequest()
      mockServer.stubCreateAgencyEmail(agencyId = "SHEFCC")

      apiService.createAgencyEmail("SHEFCC", request)

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/agency/SHEFCC/emails"))
          .withRequestBody(equalToJson("""{"emailAddress":"${request.emailAddress}"}""")),
      )
    }

    @Test
    fun `will return created agency email`() = runTest {
      mockServer.stubCreateAgencyEmail(
        agencyId = "SHEFCC",
        response = createAgencyEmailResponse().copy(id = 123456),
      )

      val response = apiService.createAgencyEmail("SHEFCC", createAgencyEmailRequest())

      assertThat(response.id).isEqualTo(123456)
    }
  }

  @Nested
  inner class CreateAgencyPhone {
    @Test
    fun `will pass oauth2 token to endpoint`() = runTest {
      mockServer.stubCreateAgencyPhone()

      apiService.createAgencyPhone("SHEFCC", createAgencyPhoneRequest())

      mockServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the create agency phone endpoint with request`() = runTest {
      val request = createAgencyPhoneRequest().copy(extension = "123")
      mockServer.stubCreateAgencyPhone(agencyId = "SHEFCC")

      apiService.createAgencyPhone("SHEFCC", request)

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/agency/SHEFCC/phones"))
          .withRequestBody(equalToJson("""{"number":"0114 555 9898","typeCode":"BUS","extension":"123"}""")),
      )
    }

    @Test
    fun `will return created agency phone`() = runTest {
      mockServer.stubCreateAgencyPhone(
        agencyId = "SHEFCC",
        response = createAgencyPhoneResponse().copy(id = 123456),
      )

      val response = apiService.createAgencyPhone("SHEFCC", createAgencyPhoneRequest())

      assertThat(response.id).isEqualTo(123456)
    }
  }

  @Nested
  inner class RefreshAgencyPhones {
    val request = updateAgencyPhoneNumbersRequest()

    @Test
    fun `will pass oauth2 token to endpoint`() = runTest {
      mockServer.stubUpdateAgencyPhoneNumbers()

      apiService.refreshAgencyPhones("SHEFCC", request)

      mockServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the refresh agency phones endpoint with request`() = runTest {
      mockServer.stubUpdateAgencyPhoneNumbers()
      val requestWithExtension = request.copy(phoneNumbers = request.phoneNumbers.map { it.copy(extension = "123") })

      apiService.refreshAgencyPhones("SHEFCC", requestWithExtension)

      mockServer.verify(
        putRequestedFor(urlPathEqualTo("/agency/SHEFCC/phones"))
          .withRequestBody(equalToJson("""{"phoneNumbers":[{"number":"0114 555 9898","typeCode":"BUS","extension":"123"}]}""")),
      )
    }

    @Test
    fun `will return refreshed agency phones`() = runTest {
      val expected = updateAgencyPhoneNumbersResponse()
      mockServer.stubUpdateAgencyPhoneNumbers(response = expected)

      assertThat(apiService.refreshAgencyPhones("SHEFCC", request)).isEqualTo(expected)
    }

    @Test
    fun `will refresh with an empty list of phones`() = runTest {
      mockServer.stubUpdateAgencyPhoneNumbers(response = updateAgencyPhoneNumbersResponse().copy(phoneNumbers = emptyList()))

      val response = apiService.refreshAgencyPhones("SHEFCC", request.copy(phoneNumbers = emptyList()))

      mockServer.verify(
        putRequestedFor(urlPathEqualTo("/agency/SHEFCC/phones"))
          .withRequestBody(equalToJson("""{"phoneNumbers":[]}""")),
      )
      assertThat(response.phoneNumbers).isEmpty()
    }
  }

  @Nested
  inner class RefreshAgencyEmails {
    val request = updateAgencyEmailAddressesRequest().copy(emailAddresses = listOf("test@justice.com"))

    @Test
    internal fun `will pass oauth2 token to endpoint`() = runTest {
      mockServer.stubUpdateAgencyEmailAddresses(agencyId = "SHEFCC")

      apiService.refreshAgencyEmails("SHEFCC", request)

      mockServer.verify(
        putRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the refresh agency emails endpoint with request`() = runTest {
      mockServer.stubUpdateAgencyEmailAddresses(agencyId = "SHEFCC")

      apiService.refreshAgencyEmails("SHEFCC", request)

      mockServer.verify(
        putRequestedFor(urlPathEqualTo("/agency/SHEFCC/emails"))
          .withRequestBody(equalToJson("""{"emailAddresses":["test@justice.com"]}""")),
      )
    }
  }

  @Nested
  inner class CreateAgencyAddress {
    @Test
    fun `will pass oauth2 token to endpoint`() = runTest {
      mockServer.stubCreateAgencyAddress(agencyId = "SHEFCC")

      apiService.createAgencyAddress("SHEFCC", createAgencyAddressRequest())

      mockServer.verify(
        postRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the create agency address endpoint with request`() = runTest {
      val request = createAgencyAddressRequest().copy(
        flat = null,
        premise = "Sheffield Combined Crt Centre",
        street = "The Law Courts",
        locality = "50 West Bar",
        postcode = "S3 8PH",
        city = "Sheffield",
        county = "South Yorkshire",
        country = "England",
      )
      mockServer.stubCreateAgencyAddress(agencyId = "SHEFCC")

      apiService.createAgencyAddress("SHEFCC", request)

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/agency/SHEFCC/addresses"))
          .withRequestBody(equalToJson("""{"flat":null,"premise":"Sheffield Combined Crt Centre","street":"The Law Courts","locality":"50 West Bar","postcode":"S3 8PH","city":"Sheffield","county":"South Yorkshire","country":"England"}""")),
      )
    }

    @Test
    fun `will return created agency address`() = runTest {
      mockServer.stubCreateAgencyAddress(
        agencyId = "SHEFCC",
        response = createAgencyAddressResponse().copy(id = 123456),
      )

      val response = apiService.createAgencyAddress("SHEFCC", createAgencyAddressRequest())

      assertThat(response.id).isEqualTo(123456)
    }
  }
}
