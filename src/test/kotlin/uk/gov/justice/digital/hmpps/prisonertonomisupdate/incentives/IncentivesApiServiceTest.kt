package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.github.tomakehurst.wiremock.client.WireMock
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.IncentivesApiExtension
import java.time.LocalDate
import java.time.LocalDateTime

@SpringAPIServiceTest
@Import(IncentivesApiService::class, IncentivesConfiguration::class)
internal class IncentivesApiServiceTest {

  @Autowired
  private lateinit var incentivesApiService: IncentivesApiService

  @Nested
  inner class GetIncentive {
    @BeforeEach
    internal fun setUp() {
      IncentivesApiExtension.incentivesApi.stubIncentiveGet(
        1234,
        """
          {
            "id": 1234,
            "iepCode": "STD",
            "iepLevel": "Standard",
            "bookingId": 456,
            "sequence": 2,
            "iepDate": "2022-12-02",
            "iepTime": "2022-12-02T10:00:00",
            "agencyId": "MDI"
          }
        """.trimIndent(),
      )
    }

    @Test
    fun `should call api with OAuth2 token`() {
      incentivesApiService.getIncentive(1234)

      IncentivesApiExtension.incentivesApi.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/iep/reviews/id/1234"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `get parse core data`() {
      val incentive = incentivesApiService.getIncentive(1234)

      assertThat(incentive.id).isEqualTo(1234)
      assertThat(incentive.iepDate).isEqualTo(LocalDate.parse("2022-12-02"))
      assertThat(incentive.iepTime).isEqualTo(LocalDateTime.parse("2022-12-02T10:00:00"))
      assertThat(incentive.agencyId).isEqualTo("MDI")
      assertThat(incentive.iepCode).isEqualTo("STD")
      assertThat(incentive.bookingId).isEqualTo(456)
      assertThat(incentive.sequence).isEqualTo(2)
    }

    @Test
    internal fun `when incentive is not found an exception is thrown`() {
      IncentivesApiExtension.incentivesApi.stubIncentiveGetWithError(1234, status = 404)

      Assertions.assertThatThrownBy {
        incentivesApiService.getIncentive(1234)
      }.isInstanceOf(NotFound::class.java)
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() {
      IncentivesApiExtension.incentivesApi.stubIncentiveGetWithError(1234, status = 503)

      Assertions.assertThatThrownBy {
        incentivesApiService.getIncentive(1234)
      }.isInstanceOf(ServiceUnavailable::class.java)
    }
  }
}
