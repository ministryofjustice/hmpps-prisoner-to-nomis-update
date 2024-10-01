package uk.gov.justice.digital.hmpps.prisonertonomisupdate.alerts

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CodeDescription
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAlertCode
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAlertRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAlertResponse
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.CreateAlertType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateAlertCode
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateAlertRequest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.nomissync.model.UpdateAlertType
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.withRequestBodyJsonPath
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

  @Nested
  inner class UpdateAlert {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsNomisApiMockServer.stubPutAlert(bookingId = 1234567, alertSequence = 4)

      apiService.updateAlert(bookingId = 1234567, alertSequence = 4, nomisAlert = updateAlertRequest())

      alertsNomisApiMockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass alert Id to service`() = runTest {
      alertsNomisApiMockServer.stubPutAlert(bookingId = 1234567, alertSequence = 4)

      apiService.updateAlert(bookingId = 1234567, alertSequence = 4, nomisAlert = updateAlertRequest())

      alertsNomisApiMockServer.verify(
        putRequestedFor(urlPathEqualTo("/prisoners/booking-id/1234567/alerts/4")),
      )
    }

    @Test
    internal fun `will pass alert update request to service`() = runTest {
      alertsNomisApiMockServer.stubPutAlert(bookingId = 1234567, alertSequence = 4)

      apiService.updateAlert(
        bookingId = 1234567,
        alertSequence = 4,
        nomisAlert = UpdateAlertRequest(
          date = LocalDate.parse("2020-07-19"),
          expiryDate = LocalDate.parse("2020-08-19"),
          isActive = true,
          updateUsername = "BOBBY.BEANS",
        ),
      )

      alertsNomisApiMockServer.verify(
        putRequestedFor(anyUrl())
          .withRequestBodyJsonPath("date", "2020-07-19")
          .withRequestBodyJsonPath("expiryDate", "2020-08-19")
          .withRequestBodyJsonPath("isActive", true)
          .withRequestBodyJsonPath("updateUsername", "BOBBY.BEANS"),
      )
    }

    private fun updateAlertRequest(): UpdateAlertRequest = UpdateAlertRequest(
      date = LocalDate.now(),
      isActive = true,
      updateUsername = "BOBBY.BEANS",
    )
  }

  @Nested
  inner class DeleteAlert {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsNomisApiMockServer.stubDeleteAlert(bookingId = 1234567, alertSequence = 4)

      apiService.deleteAlert(bookingId = 1234567, alertSequence = 4)

      alertsNomisApiMockServer.verify(
        deleteRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass alert Id to service`() = runTest {
      alertsNomisApiMockServer.stubDeleteAlert(bookingId = 1234567, alertSequence = 4)

      apiService.deleteAlert(bookingId = 1234567, alertSequence = 4)

      alertsNomisApiMockServer.verify(
        deleteRequestedFor(urlPathEqualTo("/prisoners/booking-id/1234567/alerts/4")),
      )
    }
  }

  @Nested
  inner class GetAlertsForReconciliation {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsNomisApiMockServer.stubGetAlertsForReconciliation(offenderNo = "A1234KT")

      apiService.getAlertsForReconciliation("A1234KT")

      alertsNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offenderNo to service`() = runTest {
      alertsNomisApiMockServer.stubGetAlertsForReconciliation(offenderNo = "A1234KT")

      apiService.getAlertsForReconciliation("A1234KT")

      alertsNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1234KT/alerts/reconciliation")),
      )
    }
  }

  @Nested
  inner class CreateAlertCode {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsNomisApiMockServer.stubCreateAlertCode()

      apiService.createAlertCode(createAlertCodeRequest())

      alertsNomisApiMockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will POST code details`() = runTest {
      alertsNomisApiMockServer.stubCreateAlertCode()

      apiService.createAlertCode(createAlertCodeRequest())

      alertsNomisApiMockServer.verify(
        postRequestedFor(urlPathEqualTo("/alerts/codes")),
      )
    }

    private fun createAlertCodeRequest() = CreateAlertCode(
      code = "ABC",
      description = "Description for ABC",
      listSequence = 12,
      typeCode = "XYZ",
    )
  }

  @Nested
  inner class UpdateAlertCode {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsNomisApiMockServer.stubUpdateAlertCode("ABC")

      apiService.updateAlertCode("ABC", updateAlertCodeRequest())

      alertsNomisApiMockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will PUT code details`() = runTest {
      alertsNomisApiMockServer.stubUpdateAlertCode("ABC")

      apiService.updateAlertCode(
        "ABC",
        UpdateAlertCode(
          description = "Description for ABC",
        ),
      )

      alertsNomisApiMockServer.verify(
        putRequestedFor(urlPathEqualTo("/alerts/codes/ABC")).withRequestBodyJsonPath("description", "Description for ABC"),
      )
    }

    private fun updateAlertCodeRequest() = UpdateAlertCode(
      description = "Description for ABC",
    )
  }

  @Nested
  inner class DeactivateAlertCode {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsNomisApiMockServer.stubDeactivateAlertCode("ABC")

      apiService.deactivateAlertCode("ABC")

      alertsNomisApiMockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will PUT code to deactivate`() = runTest {
      alertsNomisApiMockServer.stubDeactivateAlertCode("ABC")

      apiService.deactivateAlertCode("ABC")

      alertsNomisApiMockServer.verify(
        putRequestedFor(urlPathEqualTo("/alerts/codes/ABC/deactivate")),
      )
    }
  }

  @Nested
  inner class ReactivateAlertCode {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsNomisApiMockServer.stubReactivateAlertCode("ABC")

      apiService.reactivateAlertCode("ABC")

      alertsNomisApiMockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will PUT code to reactivate`() = runTest {
      alertsNomisApiMockServer.stubReactivateAlertCode("ABC")

      apiService.reactivateAlertCode("ABC")

      alertsNomisApiMockServer.verify(
        putRequestedFor(urlPathEqualTo("/alerts/codes/ABC/reactivate")),
      )
    }
  }

  @Nested
  inner class CreateAlertType {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsNomisApiMockServer.stubCreateAlertType()

      apiService.createAlertType(createAlertTypeRequest())

      alertsNomisApiMockServer.verify(
        postRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will POST code details`() = runTest {
      alertsNomisApiMockServer.stubCreateAlertType()

      apiService.createAlertType(createAlertTypeRequest())

      alertsNomisApiMockServer.verify(
        postRequestedFor(urlPathEqualTo("/alerts/types")),
      )
    }

    private fun createAlertTypeRequest() = CreateAlertType(
      code = "ABC",
      description = "Description for ABC",
      listSequence = 12,
    )
  }

  @Nested
  inner class UpdateAlertTypeTest {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsNomisApiMockServer.stubUpdateAlertType("ABC")

      apiService.updateAlertType("ABC", updateAlertTypeRequest())

      alertsNomisApiMockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will PUT code details`() = runTest {
      alertsNomisApiMockServer.stubUpdateAlertType("ABC")

      apiService.updateAlertType(
        "ABC",
        UpdateAlertType(
          description = "Description for ABC",
        ),
      )

      alertsNomisApiMockServer.verify(
        putRequestedFor(urlPathEqualTo("/alerts/types/ABC")).withRequestBodyJsonPath("description", "Description for ABC"),
      )
    }

    private fun updateAlertTypeRequest() = UpdateAlertType(
      description = "Description for ABC",
    )
  }

  @Nested
  inner class DeactivateAlertType {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsNomisApiMockServer.stubDeactivateAlertType("ABC")

      apiService.deactivateAlertType("ABC")

      alertsNomisApiMockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will PUT code to deactivate`() = runTest {
      alertsNomisApiMockServer.stubDeactivateAlertType("ABC")

      apiService.deactivateAlertType("ABC")

      alertsNomisApiMockServer.verify(
        putRequestedFor(urlPathEqualTo("/alerts/types/ABC/deactivate")),
      )
    }
  }

  @Nested
  inner class ReactivateAlertType {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      alertsNomisApiMockServer.stubReactivateAlertType("ABC")

      apiService.reactivateAlertType("ABC")

      alertsNomisApiMockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will PUT code to reactivate`() = runTest {
      alertsNomisApiMockServer.stubReactivateAlertType("ABC")

      apiService.reactivateAlertType("ABC")

      alertsNomisApiMockServer.verify(
        putRequestedFor(urlPathEqualTo("/alerts/types/ABC/reactivate")),
      )
    }
  }
}
