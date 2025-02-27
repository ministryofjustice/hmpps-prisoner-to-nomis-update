package uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.profiledetails

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonConfiguration
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.personalrelationships.ContactPersonDpsApiExtension.Companion.dpsContactPersonServer

@SpringAPIServiceTest
@Import(ContactPersonProfileDetailsDpsApiService::class, ContactPersonProfileDetailsDpsApiMockServer::class, ContactPersonConfiguration::class)
class ContactPersonProfileDetailsDpsApiServiceTest(
  @Autowired private val apiService: ContactPersonProfileDetailsDpsApiService,
  @Autowired private val dpsApi: ContactPersonProfileDetailsDpsApiMockServer,
) {

  @Nested
  inner class GetDomesticStatus {
    @Test
    internal fun `will pass oath2 token`() = runTest {
      dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC")

      apiService.getDomesticStatus(prisonerNumber = "A1234BC")

      dpsContactPersonServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get sync endpoint`() = runTest {
      dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC")

      apiService.getDomesticStatus(prisonerNumber = "A1234BC")

      dpsContactPersonServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/A1234BC/domestic-status")),
      )
    }

    @Test
    fun `will parse the response`() = runTest {
      dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC")

      with(apiService.getDomesticStatus(prisonerNumber = "A1234BC")) {
        assertThat(id).isEqualTo(54321)
        assertThat(domesticStatusCode).isEqualTo("M")
      }
    }

    @Test
    fun `will throw in error`() = runTest {
      dpsApi.stubGetDomesticStatus(prisonerNumber = "A1234BC", errorStatus = NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getDomesticStatus(prisonerNumber = "A1234BC")
      }
    }
  }

  @Nested
  inner class GetNumberOfChildren {
    @Test
    internal fun `will pass oath2 token`() = runTest {
      dpsApi.stubGetNumberOfChildren(prisonerNumber = "A1234BC")

      apiService.getNumberOfChildren(prisonerNumber = "A1234BC")

      dpsContactPersonServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will call the get sync endpoint`() = runTest {
      dpsApi.stubGetNumberOfChildren(prisonerNumber = "A1234BC")

      apiService.getNumberOfChildren(prisonerNumber = "A1234BC")

      dpsContactPersonServer.verify(
        getRequestedFor(urlPathEqualTo("/sync/A1234BC/number-of-children")),
      )
    }

    @Test
    fun `will parse the response`() = runTest {
      dpsApi.stubGetNumberOfChildren(prisonerNumber = "A1234BC")

      with(apiService.getNumberOfChildren(prisonerNumber = "A1234BC")) {
        assertThat(id).isEqualTo(54321)
        assertThat(numberOfChildren).isEqualTo("3")
      }
    }

    @Test
    fun `will throw in error`() = runTest {
      dpsApi.stubGetNumberOfChildren(prisonerNumber = "A1234BC", errorStatus = NOT_FOUND)

      assertThrows<WebClientResponseException.NotFound> {
        apiService.getNumberOfChildren(prisonerNumber = "A1234BC")
      }
    }
  }
}
