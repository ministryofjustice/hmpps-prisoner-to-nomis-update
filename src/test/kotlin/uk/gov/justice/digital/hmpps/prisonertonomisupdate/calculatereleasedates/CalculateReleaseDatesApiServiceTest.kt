package uk.gov.justice.digital.hmpps.prisonertonomisupdate.calculatereleasedates

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matching
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.calculatereleasedates.CalculateReleaseDatesApiExtension.Companion.calculateReleaseDatesApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@ExtendWith(CalculateReleaseDatesApiExtension::class)
@SpringAPIServiceTest
@Import(CalculateReleaseDatesApiService::class, CalculateReleaseDatesConfiguration::class, RetryApiService::class)
class CalculateReleaseDatesApiServiceTest {
  @Autowired
  private lateinit var apiService: CalculateReleaseDatesApiService

  @Nested
  inner class GetSentenceCalculations {
    @Test
    fun `will pass oauth2 token to endpoint`() = runTest {
      calculateReleaseDatesApi.stubGetSentenceCalculations("A1234BC")

      apiService.getSentenceCalculations("A1234BC")

      calculateReleaseDatesApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", matching(".+")),
      )
    }

    @Test
    fun `will call the endpoint`() = runTest {
      calculateReleaseDatesApi.stubGetSentenceCalculations("A1234BC")

      apiService.getSentenceCalculations("A1234BC")

      calculateReleaseDatesApi.verify(
        getRequestedFor(urlPathEqualTo("/legacy-sentence-calculation/A1234BC")),
      )
    }

    @Test
    fun `will return the calculations from the endpoint`() = runTest {
      calculateReleaseDatesApi.stubGetSentenceCalculations("A1234BC")

      val calculations = apiService.getSentenceCalculations("A1234BC")

      assertThat(calculations).hasSize(1)
      assertThat(calculations[0].prisonerId).isEqualTo("A1234BC")
      assertThat(calculations[0].nomisCalculationId).isEqualTo(12345)
    }
  }
}
