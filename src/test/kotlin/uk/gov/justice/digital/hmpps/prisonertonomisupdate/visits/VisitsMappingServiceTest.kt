@file:OptIn(ExperimentalCoroutinesApi::class)

package uk.gov.justice.digital.hmpps.prisonertonomisupdate.visits

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.web.reactive.function.client.WebClientResponseException.BadRequest
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound
import org.springframework.web.reactive.function.client.WebClientResponseException.ServiceUnavailable
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.helpers.SpringAPIServiceTest
import uk.gov.justice.digital.hmpps.prisonertonomisupdate.wiremock.MappingExtension

@SpringAPIServiceTest
@Import(VisitsMappingService::class)
internal class VisitsMappingServiceTest {

  @Autowired
  private lateinit var mappingService: VisitsMappingService

  @Nested
  inner class CreateMapping {
    @BeforeEach
    internal fun setUp() {
      MappingExtension.mappingServer.stubCreate()
    }

    @Test
    fun `should call mapping api with OAuth2 token`() = runTest {
      mappingService.createMapping(newMapping())

      MappingExtension.mappingServer.verify(
        postRequestedFor(urlEqualTo("/mapping/visits"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will post data to mapping api`() = runTest {
      mappingService.createMapping(newMapping())

      MappingExtension.mappingServer.verify(
        postRequestedFor(urlEqualTo("/mapping/visits"))
          .withRequestBody(matchingJsonPath("$.nomisId", equalTo("456"))),
      )
    }

    @Test
    internal fun `when a bad response is received an exception is thrown`() = runTest {
      MappingExtension.mappingServer.stubCreateWithError(400)

      assertThrows<BadRequest> {
        mappingService.createMapping(newMapping())
      }
    }
  }

  @Nested
  inner class GetMappingGivenNomisId {

    @Test
    fun `should call nomis api OAuth2 token`() = runTest {
      MappingExtension.mappingServer.stubGetNomis(
        nomisId = "456",
        response = """{
          "nomisId": "456",
          "vsipId": "123",
          "mappingType": "ONLINE"
        }
        """.trimMargin(),
      )

      mappingService.getMappingGivenNomisIdOrNull(456)

      MappingExtension.mappingServer.verify(
        getRequestedFor(urlEqualTo("/mapping/visits/nomisId/456"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return data`() = runTest {
      MappingExtension.mappingServer.stubGetNomis(
        nomisId = "456",
        response = """{
          "nomisId": "456",
          "vsipId": "123",
          "mappingType": "ONLINE"
        }
        """.trimMargin(),
      )

      val data = mappingService.getMappingGivenNomisIdOrNull(456)

      assertThat(data).isEqualTo(newMapping())
    }

    @Test
    internal fun `when mapping is not found null is returned`() = runTest {
      MappingExtension.mappingServer.stubGetNomisWithError("456", 404)

      assertThat(mappingService.getMappingGivenNomisIdOrNull(456)).isNull()
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      MappingExtension.mappingServer.stubGetNomisWithError("456", 503)

      assertThrows<ServiceUnavailable> {
        mappingService.getMappingGivenNomisIdOrNull(456)
      }
    }
  }

  @Nested
  inner class GetMappingGivenVsipIdOrNull {

    @Test
    fun `should call api with OAuth2 token`() = runTest {
      MappingExtension.mappingServer.stubGetVsip(
        vsipId = "123",
        response = """{
          "nomisId": "456",
          "vsipId": "123",
          "mappingType": "ONLINE"
        }
        """.trimMargin(),
      )

      mappingService.getMappingGivenVsipIdOrNull("123")

      MappingExtension.mappingServer.verify(
        getRequestedFor(urlEqualTo("/mapping/visits/vsipId/123"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return data`() = runTest {
      MappingExtension.mappingServer.stubGetVsip(
        vsipId = "123",
        response = """{
          "nomisId": "456",
          "vsipId": "123",
          "mappingType": "ONLINE"
        }
        """.trimMargin(),
      )

      val data = mappingService.getMappingGivenVsipIdOrNull("123")

      assertThat(data).isEqualTo(newMapping())
    }

    @Test
    internal fun `when mapping is not found null is returned`() = runTest {
      MappingExtension.mappingServer.stubGetVsipWithError("123", 404)

      assertThat(mappingService.getMappingGivenVsipIdOrNull("123")).isNull()
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      MappingExtension.mappingServer.stubGetVsipWithError("123", 503)

      assertThrows<ServiceUnavailable>() {
        mappingService.getMappingGivenVsipIdOrNull("123")
      }
    }
  }

  @Nested
  inner class GetMappingGivenVsipId {

    @Test
    fun `should call api with OAuth2 token`() = runTest {
      MappingExtension.mappingServer.stubGetVsip(
        vsipId = "123",
        response = """{
          "nomisId": "456",
          "vsipId": "123",
          "mappingType": "ONLINE"
        }
        """.trimMargin(),
      )

      mappingService.getMappingGivenVsipId("123")

      MappingExtension.mappingServer.verify(
        getRequestedFor(urlEqualTo("/mapping/visits/vsipId/123"))
          .withHeader("Authorization", equalTo("Bearer ABCDE")),
      )
    }

    @Test
    fun `will return data`() = runTest {
      MappingExtension.mappingServer.stubGetVsip(
        vsipId = "123",
        response = """{
          "nomisId": "456",
          "vsipId": "123",
          "mappingType": "ONLINE"
        }
        """.trimMargin(),
      )

      val data = mappingService.getMappingGivenVsipId("123")

      assertThat(data).isEqualTo(newMapping())
    }

    @Test
    internal fun `when mapping is not found exception is thrown`() = runTest {
      MappingExtension.mappingServer.stubGetVsipWithError("123", 404)

      assertThrows<NotFound> {
        mappingService.getMappingGivenVsipId("123")
      }
    }

    @Test
    internal fun `when any bad response is received an exception is thrown`() = runTest {
      MappingExtension.mappingServer.stubGetVsipWithError("123", 503)

      assertThrows<ServiceUnavailable> {
        mappingService.getMappingGivenVsipId("123")
      }
    }
  }

  private fun newMapping() = VisitMappingDto(nomisId = "456", vsipId = "123", mappingType = "ONLINE")
}
