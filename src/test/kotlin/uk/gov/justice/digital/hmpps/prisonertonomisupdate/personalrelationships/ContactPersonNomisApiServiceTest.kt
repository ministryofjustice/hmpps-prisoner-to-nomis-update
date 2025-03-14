package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createContactPersonRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createContactPersonRestrictionResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonAddressResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonContactRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonContactResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonEmailResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonEmploymentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonEmploymentResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonIdentifierRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonIdentifierResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonPhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonPhoneResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.createPersonResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.updateContactPersonRestrictionRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.updatePersonAddressRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.updatePersonContactRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.updatePersonEmailRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.updatePersonEmploymentRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.updatePersonIdentifierRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.updatePersonPhoneRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonNomisApiMockServer.Companion.updatePersonRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(ContactPersonNomisApiService::class, ContactPersonNomisApiMockServer::class, RetryApiService::class)
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
  inner class DeletePerson {
    private val personId = 17171L

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeletePerson(personId)

      apiService.deletePerson(personId)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call delete endpoint`() = runTest {
      mockServer.stubDeletePerson(personId)

      apiService.deletePerson(personId)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/persons/$personId")),
      )
    }
  }

  @Nested
  inner class UpdatePerson {
    private val personId = 17171L

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubUpdatePerson(personId)

      apiService.updatePerson(personId, updatePersonRequest())

      mockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call update endpoint`() = runTest {
      mockServer.stubUpdatePerson(personId)

      apiService.updatePerson(personId, updatePersonRequest())

      mockServer.verify(
        putRequestedFor(urlPathEqualTo("/persons/$personId")),
      )
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
  inner class UpdateContactPerson {
    private val personId = 17171L
    private val contactId = 9233L

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubUpdatePersonContact(personId, contactId)

      apiService.updatePersonContact(personId, contactId, updatePersonContactRequest())

      mockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call update endpoint`() = runTest {
      mockServer.stubUpdatePersonContact(personId, contactId)

      apiService.updatePersonContact(personId, contactId, updatePersonContactRequest())

      mockServer.verify(
        putRequestedFor(urlPathEqualTo("/persons/$personId/contact/$contactId")),
      )
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
  inner class UpdatePersonAddress {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubUpdatePersonAddress(personId = 1234567, addressId = 654321)

      apiService.updatePersonAddress(personId = 1234567, addressId = 654321, request = updatePersonAddressRequest())

      mockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call update endpoint`() = runTest {
      mockServer.stubUpdatePersonAddress(personId = 1234567, addressId = 654321)

      apiService.updatePersonAddress(personId = 1234567, addressId = 654321, request = updatePersonAddressRequest())

      mockServer.verify(
        putRequestedFor(urlPathEqualTo("/persons/1234567/address/654321")),
      )
    }
  }

  @Nested
  inner class DeletePersonAddress {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeletePersonAddress(personId = 1234567, addressId = 654321)

      apiService.deletePersonAddress(personId = 1234567, addressId = 654321)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call delete endpoint`() = runTest {
      mockServer.stubDeletePersonAddress(personId = 1234567, addressId = 654321)

      apiService.deletePersonAddress(personId = 1234567, addressId = 654321)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/persons/1234567/address/654321")),
      )
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
  inner class UpdatePersonEmail {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubUpdatePersonEmail(personId = 1234567, emailId = 7655)

      apiService.updatePersonEmail(personId = 1234567, emailId = 7655, request = updatePersonEmailRequest())

      mockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call update endpoint`() = runTest {
      mockServer.stubUpdatePersonEmail(personId = 1234567, emailId = 7655)

      apiService.updatePersonEmail(personId = 1234567, emailId = 7655, request = updatePersonEmailRequest())

      mockServer.verify(
        putRequestedFor(urlPathEqualTo("/persons/1234567/email/7655")),
      )
    }
  }

  @Nested
  inner class DeletePersonEmail {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeletePersonEmail(personId = 1234567, emailId = 7655)

      apiService.deletePersonEmail(personId = 1234567, emailId = 7655)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call delete endpoint`() = runTest {
      mockServer.stubDeletePersonEmail(personId = 1234567, emailId = 7655)

      apiService.deletePersonEmail(personId = 1234567, emailId = 7655)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/persons/1234567/email/7655")),
      )
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
  inner class UpdatePersonPhone {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubUpdatePersonPhone(personId = 1234567, phoneId = 65543)

      apiService.updatePersonPhone(personId = 1234567, phoneId = 65543, updatePersonPhoneRequest())

      mockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call update endpoint`() = runTest {
      mockServer.stubUpdatePersonPhone(personId = 1234567, phoneId = 65543)

      apiService.updatePersonPhone(personId = 1234567, phoneId = 65543, updatePersonPhoneRequest())

      mockServer.verify(
        putRequestedFor(urlPathEqualTo("/persons/1234567/phone/65543")),
      )
    }
  }

  @Nested
  inner class DeletePersonPhone {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeletePersonPhone(personId = 1234567, phoneId = 65543)

      apiService.deletePersonPhone(personId = 1234567, phoneId = 65543)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call delete endpoint`() = runTest {
      mockServer.stubDeletePersonPhone(personId = 1234567, phoneId = 65543)

      apiService.deletePersonPhone(personId = 1234567, phoneId = 65543)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/persons/1234567/phone/65543")),
      )
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

  @Nested
  inner class UpdatePersonAddressPhone {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubUpdatePersonAddressPhone(personId = 1234567, addressId = 67890, phoneId = 8755)

      apiService.updatePersonAddressPhone(personId = 1234567, addressId = 67890, phoneId = 8755, request = updatePersonPhoneRequest())

      mockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call update endpoint`() = runTest {
      mockServer.stubUpdatePersonAddressPhone(personId = 1234567, addressId = 67890, phoneId = 8755)

      apiService.updatePersonAddressPhone(personId = 1234567, addressId = 67890, phoneId = 8755, request = updatePersonPhoneRequest())

      mockServer.verify(
        putRequestedFor(urlPathEqualTo("/persons/1234567/address/67890/phone/8755")),
      )
    }
  }

  @Nested
  inner class DeletePersonAddressPhone {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeletePersonAddressPhone(personId = 1234567, addressId = 67890, phoneId = 8755)

      apiService.deletePersonAddressPhone(personId = 1234567, addressId = 67890, phoneId = 8755)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call delete endpoint`() = runTest {
      mockServer.stubDeletePersonAddressPhone(personId = 1234567, addressId = 67890, phoneId = 8755)

      apiService.deletePersonAddressPhone(personId = 1234567, addressId = 67890, phoneId = 8755)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/persons/1234567/address/67890/phone/8755")),
      )
    }
  }

  @Nested
  inner class CreatePersonIdentifier {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubCreatePersonIdentifier(personId = 1234567)

      apiService.createPersonIdentifier(personId = 1234567, request = createPersonIdentifierRequest())

      mockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call create endpoint`() = runTest {
      mockServer.stubCreatePersonIdentifier(personId = 1234567)

      apiService.createPersonIdentifier(personId = 1234567, request = createPersonIdentifierRequest())

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/persons/1234567/identifier")),
      )
    }

    @Test
    fun `will return identifier id `() = runTest {
      mockServer.stubCreatePersonIdentifier(personId = 1234567, response = createPersonIdentifierResponse().copy(sequence = 4))

      val response = apiService.createPersonIdentifier(personId = 1234567, request = createPersonIdentifierRequest())

      assertThat(response.sequence).isEqualTo(4)
    }
  }

  @Nested
  inner class UpdatePersonIdentifier {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubUpdatePersonIdentifier(personId = 1234567, sequence = 4)

      apiService.updatePersonIdentifier(personId = 1234567, sequence = 4, updatePersonIdentifierRequest())

      mockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call update endpoint`() = runTest {
      mockServer.stubUpdatePersonIdentifier(personId = 1234567, sequence = 4)

      apiService.updatePersonIdentifier(personId = 1234567, sequence = 4, updatePersonIdentifierRequest())

      mockServer.verify(
        putRequestedFor(urlPathEqualTo("/persons/1234567/identifier/4")),
      )
    }
  }

  @Nested
  inner class DeletePersonIdentifier {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeletePersonIdentifier(personId = 1234567, sequence = 4)

      apiService.deletePersonIdentifier(personId = 1234567, sequence = 4)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call delete endpoint`() = runTest {
      mockServer.stubDeletePersonIdentifier(personId = 1234567, sequence = 4)

      apiService.deletePersonIdentifier(personId = 1234567, sequence = 4)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/persons/1234567/identifier/4")),
      )
    }
  }

  @Nested
  inner class CreatePersonEmployment {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubCreatePersonEmployment(personId = 1234567)

      apiService.createPersonEmployment(personId = 1234567, request = createPersonEmploymentRequest())

      mockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call create endpoint`() = runTest {
      mockServer.stubCreatePersonEmployment(personId = 1234567)

      apiService.createPersonEmployment(personId = 1234567, request = createPersonEmploymentRequest())

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/persons/1234567/employment")),
      )
    }

    @Test
    fun `will return employment id `() = runTest {
      mockServer.stubCreatePersonEmployment(personId = 1234567, response = createPersonEmploymentResponse().copy(sequence = 4))

      val response = apiService.createPersonEmployment(personId = 1234567, request = createPersonEmploymentRequest())

      assertThat(response.sequence).isEqualTo(4)
    }
  }

  @Nested
  inner class UpdatePersonEmployment {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubUpdatePersonEmployment(personId = 1234567, sequence = 4)

      apiService.updatePersonEmployment(personId = 1234567, sequence = 4, updatePersonEmploymentRequest())

      mockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call update endpoint`() = runTest {
      mockServer.stubUpdatePersonEmployment(personId = 1234567, sequence = 4)

      apiService.updatePersonEmployment(personId = 1234567, sequence = 4, updatePersonEmploymentRequest())

      mockServer.verify(
        putRequestedFor(urlPathEqualTo("/persons/1234567/employment/4")),
      )
    }
  }

  @Nested
  inner class DeletePersonEmployment {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubDeletePersonEmployment(personId = 1234567, sequence = 4)

      apiService.deletePersonEmployment(personId = 1234567, sequence = 4)

      mockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call delete endpoint`() = runTest {
      mockServer.stubDeletePersonEmployment(personId = 1234567, sequence = 4)

      apiService.deletePersonEmployment(personId = 1234567, sequence = 4)

      mockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/persons/1234567/employment/4")),
      )
    }
  }

  @Nested
  inner class CreatePersonRestriction {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubCreatePersonRestriction(personId = 1234567)

      apiService.createPersonRestriction(personId = 1234567, request = createContactPersonRestrictionRequest())

      mockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call create endpoint`() = runTest {
      mockServer.stubCreatePersonRestriction(personId = 1234567)

      apiService.createPersonRestriction(personId = 1234567, request = createContactPersonRestrictionRequest())

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/persons/1234567/restriction")),
      )
    }

    @Test
    fun `will return restriction id `() = runTest {
      mockServer.stubCreatePersonRestriction(personId = 1234567, response = createContactPersonRestrictionResponse().copy(id = 123456))

      val response = apiService.createPersonRestriction(personId = 1234567, request = createContactPersonRestrictionRequest())

      assertThat(response.id).isEqualTo(123456)
    }
  }

  @Nested
  inner class UpdatePersonRestriction {
    private val personRestrictionId = 838383L

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubUpdatePersonRestriction(personId = 1234567, personRestrictionId = personRestrictionId)

      apiService.updatePersonRestriction(personId = 1234567, personRestrictionId = personRestrictionId, request = updateContactPersonRestrictionRequest())

      mockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call update endpoint`() = runTest {
      mockServer.stubUpdatePersonRestriction(personId = 1234567, personRestrictionId = personRestrictionId)

      apiService.updatePersonRestriction(personId = 1234567, personRestrictionId = personRestrictionId, request = updateContactPersonRestrictionRequest())

      mockServer.verify(
        putRequestedFor(urlPathEqualTo("/persons/1234567/restriction/838383")),
      )
    }
  }

  @Nested
  inner class CreateContactRestriction {
    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubCreateContactRestriction(personId = 1234567, contactId = 67890)

      apiService.createContactRestriction(personId = 1234567, contactId = 67890, request = createContactPersonRestrictionRequest())

      mockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call create endpoint`() = runTest {
      mockServer.stubCreateContactRestriction(personId = 1234567, contactId = 67890)

      apiService.createContactRestriction(personId = 1234567, contactId = 67890, request = createContactPersonRestrictionRequest())

      mockServer.verify(
        postRequestedFor(urlPathEqualTo("/persons/1234567/contact/67890/restriction")),
      )
    }

    @Test
    fun `will return restriction id `() = runTest {
      mockServer.stubCreateContactRestriction(personId = 1234567, contactId = 67890, response = createContactPersonRestrictionResponse().copy(id = 123456))

      val response = apiService.createContactRestriction(personId = 1234567, contactId = 67890, request = createContactPersonRestrictionRequest())

      assertThat(response.id).isEqualTo(123456)
    }
  }

  @Nested
  inner class UpdateContactRestriction {
    private val contactRestrictionId = 838383L

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubUpdateContactRestriction(personId = 1234567, contactId = 67890, contactRestrictionId = contactRestrictionId)

      apiService.updateContactRestriction(personId = 1234567, contactId = 67890, contactRestrictionId = contactRestrictionId, request = updateContactPersonRestrictionRequest())

      mockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call update endpoint`() = runTest {
      mockServer.stubUpdateContactRestriction(personId = 1234567, contactId = 67890, contactRestrictionId = contactRestrictionId)

      apiService.updateContactRestriction(personId = 1234567, contactId = 67890, contactRestrictionId = contactRestrictionId, request = updateContactPersonRestrictionRequest())

      mockServer.verify(
        putRequestedFor(urlPathEqualTo("/persons/1234567/contact/67890/restriction/838383")),
      )
    }
  }

  @Nested
  inner class GetPrisonerContacts {

    @Test
    fun `will pass oath2 token to service`() = runTest {
      mockServer.stubGetPrisonerContacts(prisonerNumber = "A1234KT")

      apiService.getContactsForPrisoner("A1234KT")

      mockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get endpoint`() = runTest {
      mockServer.stubGetPrisonerContacts(prisonerNumber = "A1234KT")

      apiService.getContactsForPrisoner("A1234KT")

      mockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1234KT/contacts")),
      )
    }

    @Test
    fun `will request just active contacts`() = runTest {
      mockServer.stubGetPrisonerContacts(prisonerNumber = "A1234KT")

      apiService.getContactsForPrisoner("A1234KT")

      mockServer.verify(
        getRequestedFor(anyUrl())
          .withQueryParam("active-only", equalTo("true")),
      )
    }
  }
}
