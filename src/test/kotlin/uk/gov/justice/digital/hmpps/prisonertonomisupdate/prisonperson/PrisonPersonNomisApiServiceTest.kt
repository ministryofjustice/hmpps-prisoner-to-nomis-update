package uk.gov.justice.digital.hmpps.prisonertonomisupdate.prisonperson

import com.github.tomakehurst.wiremock.client.WireMock.anyUrl
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.web.reactive.function.client.WebClientResponseException
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest

@SpringAPIServiceTest
@Import(PrisonPersonNomisApiService::class, PrisonPersonConfiguration::class, PrisonPersonNomisApiMockServer::class)
class PrisonPersonNomisApiServiceTest {
  @Autowired
  private lateinit var apiService: PrisonPersonNomisApiService

  @Autowired
  private lateinit var prisonPersonNomisApiMockServer: PrisonPersonNomisApiMockServer

  @Nested
  inner class UpsertPhysicalAttributes {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      prisonPersonNomisApiMockServer.stubPutPhysicalAttributes(offenderNo = "A1234KT")

      apiService.upsertPhysicalAttributes(offenderNo = "A1234KT", height = 180, weight = 80)

      prisonPersonNomisApiMockServer.verify(
        putRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number and attributes to service`() = runTest {
      prisonPersonNomisApiMockServer.stubPutPhysicalAttributes(offenderNo = "A1234KT")

      apiService.upsertPhysicalAttributes(offenderNo = "A1234KT", height = 180, weight = 80)

      prisonPersonNomisApiMockServer.verify(
        putRequestedFor(urlPathEqualTo("/prisoners/A1234KT/physical-attributes"))
          .withRequestBody(matchingJsonPath("$.height", equalTo("180")))
          .withRequestBody(matchingJsonPath("$.weight", equalTo("80"))),
      )
    }

    @Test
    fun `will return created and booking ID`() = runTest {
      prisonPersonNomisApiMockServer.stubPutPhysicalAttributes(offenderNo = "A1234KT", created = true, bookingId = 1234567)

      val response = apiService.upsertPhysicalAttributes(offenderNo = "A1234KT", height = 180, weight = 80)

      assertThat(response.bookingId).isEqualTo(1234567)
      assertThat(response.created).isTrue()
    }
  }

  @Nested
  inner class GetReconciliation {
    @Test
    internal fun `will pass oath2 token to service`() = runTest {
      prisonPersonNomisApiMockServer.stubGetReconciliation(offenderNo = "A1234KT")

      apiService.getReconciliation(offenderNo = "A1234KT")

      prisonPersonNomisApiMockServer.verify(
        getRequestedFor(anyUrl()).withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    internal fun `will pass offender number to service`() = runTest {
      prisonPersonNomisApiMockServer.stubGetReconciliation(offenderNo = "A1234KT")

      apiService.getReconciliation(offenderNo = "A1234KT")

      prisonPersonNomisApiMockServer.verify(
        getRequestedFor(urlPathEqualTo("/prisoners/A1234KT/physical-attributes/reconciliation")),
      )
    }

    @Test
    fun `will return height and weight`() = runTest {
      prisonPersonNomisApiMockServer.stubGetReconciliation(offenderNo = "A1234KT", height = 180, weight = 80)

      val response = apiService.getReconciliation(offenderNo = "A1234KT")

      assertThat(response.height).isEqualTo(180)
      assertThat(response.weight).isEqualTo(80)
    }

    @Test
    fun `will throw error when API returns an error`() = runTest {
      prisonPersonNomisApiMockServer.stubGetReconciliation(INTERNAL_SERVER_ERROR)

      assertThrows<WebClientResponseException.InternalServerError> {
        apiService.getReconciliation(offenderNo = "A1234KT")
      }
    }
  }
}
