package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

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
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAlertRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAlertResponse
import java.time.LocalDate

@SpringAPIServiceTest
@Import(AlertsNomisApiService::class, AlertsConfiguration::class, AlertsNomisApiMockServer::class)
class AlertsNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: AlertsNomisApiService

  @Autowired
  private lateinit var alertsNomisApiMockServer: AlertsNomisApiMockServer

  @Nested
  inner class CreateAlert {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsNomisApiMockServer.stubPostAlert(offenderNo = "A1234KT")

      apiService.createAlert(offenderNo = "A1234KT", nomisAlert = createAlertRequest())

      alertsNomisApiMockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number to service`() = runTest {
      alertsNomisApiMockServer.stubPostAlert(offenderNo = "A1234KT")

      apiService.createAlert(offenderNo = "A1234KT", nomisAlert = createAlertRequest())

      alertsNomisApiMockServer.verify(
        postRequestedFor(urlPathEqualTo("/prisoners/A1234KT/alerts")),
      )
    }

    @Test
    fun `will return NOMIS alert id`() = runTest {
      alertsNomisApiMockServer.stubPostAlert(
        offenderNo = "A1234KT",
        alert = CreateAlertResponse(
          bookingId = 1234567,
          alertSequence = 3,
          alertCode = CodeDescription("HPI", ""),
          type = CodeDescription("X", ""),
        ),
      )

      val nomisAlert = apiService.createAlert(offenderNo = "A1234KT", nomisAlert = createAlertRequest())

      assertThat(nomisAlert.bookingId).isEqualTo(1234567)
      assertThat(nomisAlert.alertSequence).isEqualTo(3)
    }

    private fun createAlertRequest(): CreateAlertRequest = CreateAlertRequest(
      alertCode = "HPI",
      date = LocalDate.now(),
      isActive = true,
      createUsername = "BOBBY.BEANS",
    )
  }
}
