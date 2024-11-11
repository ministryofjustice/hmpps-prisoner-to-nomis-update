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
        getRequestedFor(urlPathEqualTo("/sync/contact")),
      )
    }
  }
}
