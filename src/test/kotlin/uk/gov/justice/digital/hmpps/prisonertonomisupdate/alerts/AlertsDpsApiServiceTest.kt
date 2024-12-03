package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.not
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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

  @Nested
  inner class GetActiveAlertsForPrisoner {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsDpsApi.stubGetActiveAlertsForPrisoner("A1234TK")

      apiService.getActiveAlertsForPrisoner("A1234TK")

      alertsDpsApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offenderNo to service`() = runTest {
      alertsDpsApi.stubGetActiveAlertsForPrisoner("A1234TK")

      apiService.getActiveAlertsForPrisoner("A1234TK")

      alertsDpsApi.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1234TK/alerts")),
      )
    }

    @Test
    internal fun `will always supply large page size big enough for all possible alerts`() = runTest {
      alertsDpsApi.stubGetActiveAlertsForPrisoner("A1234TK")

      apiService.getActiveAlertsForPrisoner("A1234TK")

      alertsDpsApi.verify(
        getRequestedFor(anyUrl())
          .withQueryParam("size", equalTo("1000"))
          .withQueryParam("page", equalTo("0")),
      )
    }

    @Test
    internal fun `will always request active alerts`() = runTest {
      alertsDpsApi.stubGetActiveAlertsForPrisoner("A1234TK")

      apiService.getActiveAlertsForPrisoner("A1234TK")

      alertsDpsApi.verify(
        getRequestedFor(anyUrl())
          .withQueryParam("isActive", equalTo("true")),
      )
    }

    @Test
    fun `will return alert`() = runTest {
      alertsDpsApi.stubGetActiveAlertsForPrisoner("A1234TK", count = 300)

      val alerts = apiService.getActiveAlertsForPrisoner("A1234TK")

      assertThat(alerts).hasSize(300)
    }

    @Test
    fun `will throw exception if there are more alerts than hard coded page size`() = runTest {
      alertsDpsApi.stubGetActiveAlertsForPrisoner("A1234TK", count = 1001)

      assertThrows<IllegalStateException> { apiService.getActiveAlertsForPrisoner("A1234TK") }
    }
  }

  @Nested
  inner class GetAllAlertsForPrisoner {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsDpsApi.stubGetAllAlertsForPrisoner("A1234TK")

      apiService.getAllAlertsForPrisoner("A1234TK")

      alertsDpsApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offenderNo to service`() = runTest {
      alertsDpsApi.stubGetAllAlertsForPrisoner("A1234TK")

      apiService.getAllAlertsForPrisoner("A1234TK")

      alertsDpsApi.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1234TK/alerts")),
      )
    }

    @Test
    internal fun `will always supply large page size big enough for all possible alerts`() = runTest {
      alertsDpsApi.stubGetAllAlertsForPrisoner("A1234TK")

      apiService.getAllAlertsForPrisoner("A1234TK")

      alertsDpsApi.verify(
        getRequestedFor(anyUrl())
          .withQueryParam("size", equalTo("1000"))
          .withQueryParam("page", equalTo("0")),
      )
    }

    @Test
    internal fun `will request all alerts not just the active ones`() = runTest {
      alertsDpsApi.stubGetAllAlertsForPrisoner("A1234TK")

      apiService.getAllAlertsForPrisoner("A1234TK")

      alertsDpsApi.verify(
        getRequestedFor(anyUrl())
          .withQueryParam("isActive", not(equalTo("true"))),
      )
    }

    @Test
    fun `will return alert`() = runTest {
      alertsDpsApi.stubGetAllAlertsForPrisoner("A1234TK", count = 300)

      val alerts = apiService.getAllAlertsForPrisoner("A1234TK")

      assertThat(alerts).hasSize(300)
    }

    @Test
    fun `will throw exception if there are more alerts than hard coded page size`() = runTest {
      alertsDpsApi.stubGetAllAlertsForPrisoner("A1234TK", count = 1001)

      assertThrows<IllegalStateException> { apiService.getAllAlertsForPrisoner("A1234TK") }
    }
  }

  @Nested
  inner class GetAlertCode {
    private val code = "ABC"

    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsDpsApi.stubGetAlertCode(code)

      apiService.getAlertCode(code)

      alertsDpsApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass alert code to service`() = runTest {
      alertsDpsApi.stubGetAlertCode(code)

      apiService.getAlertCode(code)

      alertsDpsApi.verify(
        getRequestedFor(urlEqualTo("/alert-codes/$code")),
      )
    }

    @Test
    fun `will return alert code`() = runTest {
      alertsDpsApi.stubGetAlertCode(code, dpsAlertCodeReferenceData(code).copy(description = "An alert code"))

      val alertCode = apiService.getAlertCode(code)

      assertThat(alertCode.code).isEqualTo(code)
      assertThat(alertCode.description).isEqualTo("An alert code")
    }
  }

  @Nested
  inner class GetAlertType {
    private val code = "ABC"

    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsDpsApi.stubGetAlertType(code)

      apiService.getAlertType(code)

      alertsDpsApi.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass alert type to service`() = runTest {
      alertsDpsApi.stubGetAlertType(code)

      apiService.getAlertType(code)

      alertsDpsApi.verify(
        getRequestedFor(urlEqualTo("/alert-types/$code")),
      )
    }

    @Test
    fun `will return alert type`() = runTest {
      alertsDpsApi.stubGetAlertType(code, dpsAlertTypeReferenceData(code).copy(description = "An alert type"))

      val alertCode = apiService.getAlertType(code)

      assertThat(alertCode.code).isEqualTo(code)
      assertThat(alertCode.description).isEqualTo("An alert type")
    }
  }
}
