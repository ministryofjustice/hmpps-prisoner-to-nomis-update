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
}
