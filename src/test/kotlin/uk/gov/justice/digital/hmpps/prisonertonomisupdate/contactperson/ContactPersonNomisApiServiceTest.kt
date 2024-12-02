package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonNomisApiMockServer.Companion.createPersonAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonNomisApiMockServer.Companion.createPersonAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonNomisApiMockServer.Companion.createPersonContactRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonNomisApiMockServer.Companion.createPersonContactResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonNomisApiMockServer.Companion.createPersonEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonNomisApiMockServer.Companion.createPersonEmailResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonNomisApiMockServer.Companion.createPersonPhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonNomisApiMockServer.Companion.createPersonPhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonNomisApiMockServer.Companion.createPersonRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonNomisApiMockServer.Companion.createPersonResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest

@SpringAPIServiceTest
@Import(ContactPersonNomisApiService::class, ContactPersonNomisApiMockServer::class)
class ContactPersonNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: ContactPersonNomisApiService

  @Autowired
  private lateinit var mockServer: ContactPersonNomisApiMockServer

  @Nested
  inner class CreatePerson {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubCreatePerson()

      apiService.createPerson(request = createPersonRequest())

      mockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call create endpoint`() = runTest {
      mockServer.stubCreatePerson()

      apiService.createPerson(request = createPersonRequest())

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/persons")),
      )
    }

    @Test
    fun `will return person id `() = runTest {
      mockServer.stubCreatePerson(response = createPersonResponse().copy(personId = 123456))

      val response = apiService.createPerson(request = createPersonRequest())

      assertThat(response.personId).isEqualTo(123456)
    }
  }

  @Nested
  inner class CreatePersonContact {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubCreatePersonContact(personId = 1234567)

      apiService.createPersonContact(personId = 1234567, request = createPersonContactRequest())

      mockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call create endpoint`() = runTest {
      mockServer.stubCreatePersonContact(personId = 1234567)

      apiService.createPersonContact(personId = 1234567, request = createPersonContactRequest())

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/persons/1234567/contact")),
      )
    }

    @Test
    fun `will return contact id `() = runTest {
      mockServer.stubCreatePersonContact(personId = 1234567, response = createPersonContactResponse().copy(personContactId = 123456))

      val response = apiService.createPersonContact(personId = 1234567, request = createPersonContactRequest())

      assertThat(response.personContactId).isEqualTo(123456)
    }
  }

  @Nested
  inner class CreatePersonAddress {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubCreatePersonAddress(personId = 1234567)

      apiService.createPersonAddress(personId = 1234567, request = createPersonAddressRequest())

      mockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call create endpoint`() = runTest {
      mockServer.stubCreatePersonAddress(personId = 1234567)

      apiService.createPersonAddress(personId = 1234567, request = createPersonAddressRequest())

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/persons/1234567/address")),
      )
    }

    @Test
    fun `will return address id `() = runTest {
      mockServer.stubCreatePersonAddress(personId = 1234567, response = createPersonAddressResponse().copy(personAddressId = 123456))

      val response = apiService.createPersonAddress(personId = 1234567, request = createPersonAddressRequest())

      assertThat(response.personAddressId).isEqualTo(123456)
    }
  }

  @Nested
  inner class CreatePersonEmail {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubCreatePersonEmail(personId = 1234567)

      apiService.createPersonEmail(personId = 1234567, request = createPersonEmailRequest())

      mockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call create endpoint`() = runTest {
      mockServer.stubCreatePersonEmail(personId = 1234567)

      apiService.createPersonEmail(personId = 1234567, request = createPersonEmailRequest())

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/persons/1234567/email")),
      )
    }

    @Test
    fun `will return email id `() = runTest {
      mockServer.stubCreatePersonEmail(personId = 1234567, response = createPersonEmailResponse().copy(emailAddressId = 123456))

      val response = apiService.createPersonEmail(personId = 1234567, request = createPersonEmailRequest())

      assertThat(response.emailAddressId).isEqualTo(123456)
    }
  }

  @Nested
  inner class CreatePersonPhone {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubCreatePersonPhone(personId = 1234567)

      apiService.createPersonPhone(personId = 1234567, request = createPersonPhoneRequest())

      mockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call create endpoint`() = runTest {
      mockServer.stubCreatePersonPhone(personId = 1234567)

      apiService.createPersonPhone(personId = 1234567, request = createPersonPhoneRequest())

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/persons/1234567/phone")),
      )
    }

    @Test
    fun `will return phone id `() = runTest {
      mockServer.stubCreatePersonPhone(personId = 1234567, response = createPersonPhoneResponse().copy(phoneId = 123456))

      val response = apiService.createPersonPhone(personId = 1234567, request = createPersonPhoneRequest())

      assertThat(response.phoneId).isEqualTo(123456)
    }
  }

  @Nested
  inner class CreatePersonAddressPhone {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubCreatePersonAddressPhone(personId = 1234567, addressId = 67890)

      apiService.createPersonAddressPhone(personId = 1234567, addressId = 67890, request = createPersonPhoneRequest())

      mockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call create endpoint`() = runTest {
      mockServer.stubCreatePersonAddressPhone(personId = 1234567, addressId = 67890)

      apiService.createPersonAddressPhone(personId = 1234567, addressId = 67890, request = createPersonPhoneRequest())

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/persons/1234567/address/67890/phone")),
      )
    }

    @Test
    fun `will return phone id `() = runTest {
      mockServer.stubCreatePersonAddressPhone(personId = 1234567, addressId = 67890, response = createPersonPhoneResponse().copy(phoneId = 123456))

      val response = apiService.createPersonAddressPhone(personId = 1234567, addressId = 67890, request = createPersonPhoneRequest())

      assertThat(response.phoneId).isEqualTo(123456)
    }
  }
}
