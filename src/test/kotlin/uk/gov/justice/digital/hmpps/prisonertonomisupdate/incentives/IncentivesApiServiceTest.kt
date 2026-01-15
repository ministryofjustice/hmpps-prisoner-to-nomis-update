@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.incentives

import com.github.tomakehurst.wiremock.client.WireMock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
            "prisonerNumber": "A1234AA",
            "iepDate": "2022-12-02",
            "iepTime": "2022-12-02T10:00:00",
            "agencyId": "MDI",
            "userId": "BILLYBOB",
            "reviewType": "INITIAL",
            "auditModuleName": "audit",
            "isRealReview": true
          }
        """.trimIndent(),
      )
    }

    @Test
    fun `should call api with OAuth2 token`() = runTest {
      incentivesApiService.getIncentive(1234)

      IncentivesApiExtension.incentivesApi.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/incentive-reviews/id/1234"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `get parse core data`() = runTest {
      val incentive = incentivesApiService.getIncentive(1234)

      assertThat(incentive.id).isEqualTo(1234)
      assertThat(incentive.iepDate).isEqualTo(LocalDate.parse("2022-12-02"))
      assertThat(incentive.iepTime).isEqualTo(LocalDateTime.parse("2022-12-02T10:00:00"))
      assertThat(incentive.agencyId).isEqualTo("MDI")
      assertThat(incentive.iepCode).isEqualTo("STD")
      assertThat(incentive.bookingId).isEqualTo(456)
    }

    @Test
    internal fun `when incentive is not found an exception is thrown`() = runTest {
      IncentivesApiExtension.incentivesApi.stubIncentiveGetWithError(1234, status = 404)

      assertThrows<NotFound> {
        incentivesApiService.getIncentive(1234)
      }
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      IncentivesApiExtension.incentivesApi.stubIncentiveGetWithError(1234, status = 503)

      assertThrows<ServiceUnavailable> {
        incentivesApiService.getIncentive(1234)
      }
    }
  }

  @Nested
  inner class GetCurrentIncentive {
    @BeforeEach
    internal fun setUp() {
      IncentivesApiExtension.incentivesApi.stubCurrentIncentiveGet(99, "STD")
    }

    @Test
    fun `should call api with OAuth2 token`() = runTest {
      incentivesApiService.getCurrentIncentive(99)

      IncentivesApiExtension.incentivesApi.verify(
        WireMock.getRequestedFor(WireMock.urlEqualTo("/incentive-reviews/booking/99?with-details=false"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `get parse core data`() = runTest {
      val incentive = incentivesApiService.getCurrentIncentive(99)

      assertThat(incentive?.iepCode).isEqualTo("STD")
    }

    @Test
    internal fun `when incentive is not found level will be null`() = runTest {
      val incentive = incentivesApiService.getCurrentIncentive(88)

      assertThat(incentive).isNull()
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      IncentivesApiExtension.incentivesApi.stubCurrentIncentiveGetWithError(99, responseCode = 503)

      assertThrows<ServiceUnavailable> {
        incentivesApiService.getCurrentIncentive(99)
      }
    }
  }
}
