@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjudications

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjustments.AdjudicationsApiService
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.adjustments.AdjudicationsConfiguration
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.AdjudicationsApiExtension.Companion.adjudicationsApiServer

@SpringAPIServiceTest
@Import(AdjudicationsApiService::class, AdjudicationsConfiguration::class)
internal class AdjudicationsApiServiceTest {

  @Autowired
  private lateinit var adjustmentsApiService: AdjudicationsApiService

  @Nested
  @DisplayName("GET /reported-adjudications/{chargeNumber}/v2")
  inner class GetAdjudication {
    @BeforeEach
    internal fun setUp() {
      adjudicationsApiServer.stubChargeGet(chargeNumber = "1234")
    }

    @Test
    fun `should call api with OAuth2 token`(): Unit = runTest {
      adjustmentsApiService.getCharge("1234", "MDI")

      adjudicationsApiServer.verify(
        getRequestedFor(urlEqualTo("/reported-adjudications/1234/v2"))
          .withHeader("Authorization", WireMock.equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call api with prisonId in header token`(): Unit = runTest {
      adjustmentsApiService.getCharge("1234", "MDI")

      adjudicationsApiServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Active-Caseload", WireMock.equalTo("MDI")),
      )
    }

    @Test
    internal fun `will parse data for an adjudication`(): Unit = runTest {
      adjudicationsApiServer.stubChargeGet(chargeNumber = "1234", offenderNo = "A1234KT")

      val adjudication = adjustmentsApiService.getCharge("1234", "MDI")

      assertThat(adjudication.prisonerNumber).isEqualTo("A1234KT")
    }

    @Test
    internal fun `when adjudication is not found an exception is thrown`() = runTest {
      adjudicationsApiServer.stubChargeGetWithError("1234", status = 404)

      assertThrows<NotFound> {
        adjustmentsApiService.getCharge("1234", "MDI")
      }
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      adjudicationsApiServer.stubChargeGetWithError("1234", status = 503)

      assertThrows<ServiceUnavailable> {
        adjustmentsApiService.getCharge("1234", "MDI")
      }
    }
  }
}
