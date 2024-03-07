package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts.AlertsDpsApiExtension.Companion.alertsDpsApi
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import java.util.UUID

@SpringAPIServiceTest
@Import(AlertsDpsApiService::class, AlertsConfiguration::class)
class AlertsDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: AlertsDpsApiService

  @Nested
  inner class GetAlert {
    private val dpsAlertId = "a04f7a8d-61aa-400c-9395-f4dc62f36ab0"

    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsDpsApi.stubGetAlert()

      apiService.getAlert(alertId = dpsAlertId)

      alertsDpsApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass alert Id to service`() = runTest {
      alertsDpsApi.stubGetAlert()

      apiService.getAlert(alertId = dpsAlertId)

      alertsDpsApi.verify(
        getRequestedFor(urlEqualTo("/alerts/$dpsAlertId")),
      )
    }

    @Test
    fun `will return alert`() = runTest {
      alertsDpsApi.stubGetAlert(alert = dpsAlert().copy(alertUuid = UUID.fromString(dpsAlertId), authorisedBy = "Bobby Beans"))

      val alert = apiService.getAlert(dpsAlertId)

      assertThat(alert.alertUuid.toString()).isEqualTo(dpsAlertId)
      assertThat(alert.authorisedBy).isEqualTo("Bobby Beans")
    }
  }
}
