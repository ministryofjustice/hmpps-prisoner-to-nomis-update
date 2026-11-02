package uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.movements.personlocation.PersonLocationDpsApiExtension.Companion.dpsPersonLocationServer
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.services.RetryApiService

@ExtendWith(PersonLocationDpsApiExtension::class)
@SpringAPIServiceTest
@Import(
  PersonLocationDpsApiService::class,
  PersonLocationConfiguration::class,
  PersonLocationDpsApiMockServer::class,
  RetryApiService::class,
)
class PersonLocationDpsApiServiceTest {
  @Autowired
  private lateinit var apiService: PersonLocationDpsApiService

  @Nested
  inner class Reconciliation {
    @Test
    internal fun `should pass oauth2 token`() = runTest {
      dpsPersonLocationServer.stubReconciliation()

      apiService.getReconciliation("A1234BC")

      dpsPersonLocationServer.verify(
        getRequestedFor(anyUrl())
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `should call the reconciliation endpoint`() = runTest {
      dpsPersonLocationServer.stubReconciliation("A1234BC")

      apiService.getReconciliation("A1234BC")

      dpsPersonLocationServer.verify(
        getRequestedFor(urlPathEqualTo("/reconciliation/external-movements/A1234BC")),
      )
    }

    @Test
    fun `should throw if error`() = runTest {
      dpsPersonLocationServer.stubReconciliation(status = INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getReconciliation("A1234BC")
      }
    }
  }
}
