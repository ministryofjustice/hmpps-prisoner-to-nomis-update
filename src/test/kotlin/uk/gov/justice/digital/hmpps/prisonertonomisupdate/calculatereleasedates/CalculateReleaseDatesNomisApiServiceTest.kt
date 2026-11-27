package uk.gov.justice.digital.hmpps.prisonertonomisupdate.calculatereleasedates

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@SpringAPIServiceTest
@Import(
  CalculateReleaseDatesNomisApiService::class,
  CalculateReleaseDatesNomisApiMockServer::class,
  RetryApiService::class,
)
class CalculateReleaseDatesNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: CalculateReleaseDatesNomisApiService

  @Autowired
  private lateinit var mockServer: CalculateReleaseDatesNomisApiMockServer

  @Nested
  inner class GetSentenceCalculations {
    @Test
    fun `will pass oauth2 token to service`() = runTest {
      mockServer.stubGetSentenceCalculations("A1234BC")

      apiService.getSentenceCalculations("A1234BC")

      mockServer.verify(getRequestedFor(anyUrl()).withHeader("Authorization", matching(".+")))
    }

    @Test
    fun `will call the endpoint with the offender number`() = runTest {
      mockServer.stubGetSentenceCalculations("A1234BC")

      apiService.getSentenceCalculations("A1234BC")

      mockServer.verify(getRequestedFor(urlPathEqualTo("/prisoners/A1234BC/sentence-calculations")))
    }

    @Test
    fun `will return the calculations`() = runTest {
      mockServer.stubGetSentenceCalculations("A1234BC")

      val calculations = apiService.getSentenceCalculations("A1234BC")

      assertThat(calculations).hasSize(1)
      assertThat(calculations[0].id).isEqualTo(12345)
      assertThat(calculations[0].offenderNo).isEqualTo("A1234BC")
    }
  }
}
