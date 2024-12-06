package uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.contactperson.ContactPersonDpsApiExtension.Companion.dpsContactPersonServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest

@SpringAPIServiceTest
@Import(ContactPersonDpsApiService::class, ContactPersonConfiguration::class)
class ContactPersonDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: ContactPersonDpsApiService

  @Nested
  inner class GetContact {
    @Test
    internal fun `will pass oath2 token to contact endpoint`() = runTest {
      dpsContactPersonServer.stubGetContact(contactId = 1234567)

      apiService.getContact(contactId = 1234567)

      dpsContactPersonServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get sync endpoint`() = runTest {
      dpsContactPersonServer.stubGetContact(contactId = 1234567)

      apiService.getContact(contactId = 1234567)

      dpsContactPersonServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/contact/1234567")),
      )
    }
  }

  @Nested
  inner class GetPrisonerContact {
    @Test
    internal fun `will pass oath2 token to prisoner contact endpoint`() = runTest {
      dpsContactPersonServer.stubGetPrisonerContact(prisonerContactId = 1234567)

      apiService.getPrisonerContact(prisonerContactId = 1234567)

      dpsContactPersonServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get sync endpoint`() = runTest {
      dpsContactPersonServer.stubGetPrisonerContact(prisonerContactId = 1234567)

      apiService.getPrisonerContact(prisonerContactId = 1234567)

      dpsContactPersonServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/prisoner-contact/1234567")),
      )
    }
  }

  @Nested
  inner class GetContactAddress {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubGetContactAddress(contactAddressId = 1234567)

      apiService.getContactAddress(contactAddressId = 1234567)

      dpsContactPersonServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get sync endpoint`() = runTest {
      dpsContactPersonServer.stubGetContactAddress(contactAddressId = 1234567)

      apiService.getContactAddress(contactAddressId = 1234567)

      dpsContactPersonServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/contact-address/1234567")),
      )
    }
  }

  @Nested
  inner class GetContactEmail {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubGetContactEmail(contactEmailId = 1234567)

      apiService.getContactEmail(contactEmailId = 1234567)

      dpsContactPersonServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get sync endpoint`() = runTest {
      dpsContactPersonServer.stubGetContactEmail(contactEmailId = 1234567)

      apiService.getContactEmail(contactEmailId = 1234567)

      dpsContactPersonServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/contact-email/1234567")),
      )
    }
  }

  @Nested
  inner class GetContactPhone {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubGetContactPhone(contactPhoneId = 1234567)

      apiService.getContactPhone(contactPhoneId = 1234567)

      dpsContactPersonServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get sync endpoint`() = runTest {
      dpsContactPersonServer.stubGetContactPhone(contactPhoneId = 1234567)

      apiService.getContactPhone(contactPhoneId = 1234567)

      dpsContactPersonServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/contact-phone/1234567")),
      )
    }
  }

  @Nested
  inner class GetContactAddressPhone {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubGetContactAddressPhone(contactAddressPhoneId = 1234567)

      apiService.getContactAddressPhone(contactAddressPhoneId = 1234567)

      dpsContactPersonServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get sync endpoint`() = runTest {
      dpsContactPersonServer.stubGetContactAddressPhone(contactAddressPhoneId = 1234567)

      apiService.getContactAddressPhone(contactAddressPhoneId = 1234567)

      dpsContactPersonServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/contact-address-phone/1234567")),
      )
    }
  }

  @Nested
  inner class GetContactIdentity {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubGetContactIdentity(contactIdentityId = 1234567)

      apiService.getContactIdentity(contactIdentityId = 1234567)

      dpsContactPersonServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get sync endpoint`() = runTest {
      dpsContactPersonServer.stubGetContactIdentity(contactIdentityId = 1234567)

      apiService.getContactIdentity(contactIdentityId = 1234567)

      dpsContactPersonServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/contact-identity/1234567")),
      )
    }
  }

  @Nested
  inner class GetContactRestriction {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubGetContactRestriction(contactRestrictionId = 1234567)

      apiService.getContactRestriction(contactRestrictionId = 1234567)

      dpsContactPersonServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get sync endpoint`() = runTest {
      dpsContactPersonServer.stubGetContactRestriction(contactRestrictionId = 1234567)

      apiService.getContactRestriction(contactRestrictionId = 1234567)

      dpsContactPersonServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/contact-restriction/1234567")),
      )
    }
  }

  @Nested
  inner class GetPrisonerContactRestriction {
    @Test
    internal fun `will pass oath2 token to endpoint`() = runTest {
      dpsContactPersonServer.stubGetPrisonerContactRestriction(contactPrisonerRestrictionId = 1234567)

      apiService.getPrisonerContactRestriction(prisonerContactRestrictionId = 1234567)

      dpsContactPersonServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get sync endpoint`() = runTest {
      dpsContactPersonServer.stubGetPrisonerContactRestriction(contactPrisonerRestrictionId = 1234567)

      apiService.getPrisonerContactRestriction(prisonerContactRestrictionId = 1234567)

      dpsContactPersonServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/prisoner-contact-restriction/1234567")),
      )
    }
  }
}
