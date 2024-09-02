package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest

@SpringAPIServiceTest
@Import(NomisApiService::class, Configuration::class, NomisApiMockServer::class)
class NomisApiServiceTest {
  @Autowired
  private lateinit var nomisApiService: NomisApiService

  @Autowired
  private lateinit var nomisApiMockServer: NomisApiMockServer

  @Nested
  inner class GetReconciliation {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      nomisApiMockServer.stubGetReconciliation(offenderNo = "A1234KT")

      nomisApiService.getReconciliation(offenderNo = "A1234KT")

      nomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number to service`() = runTest {
      nomisApiMockServer.stubGetReconciliation(offenderNo = "A1234KT")

      nomisApiService.getReconciliation(offenderNo = "A1234KT")

      nomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1234KT/prison-person/reconciliation")),
      )
    }

    @Test
    fun `will return height and weight`() = runTest {
      nomisApiMockServer.stubGetReconciliation(offenderNo = "A1234KT", height = 180, weight = 80)

      val response = nomisApiService.getReconciliation(offenderNo = "A1234KT")

      assertThat(response?.height).isEqualTo(180)
      assertThat(response?.weight).isEqualTo(80)
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      nomisApiMockServer.stubGetReconciliation(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        nomisApiService.getReconciliation(offenderNo = "A1234KT")
      }
    }

    @Test
    fun `will return null if not found`() = runTest {
      nomisApiMockServer.stubGetReconciliation(NOT_FOUND)

      assertThat(nomisApiService.getReconciliation(offenderNo = "A1234AA")).isNull()
    }
  }
}
